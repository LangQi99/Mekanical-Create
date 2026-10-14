package io.github.langqi99.mekanicalcreate.client;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.client.jei.MekanicalCreateJeiPlugin;
import io.github.langqi99.mekanicalcreate.content.RecipeCatalogReloadState;
import io.github.langqi99.mekanicalcreate.content.SimulationDisplayRecipeCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Coalesced, tag-aware client loading inspired by Useless Mod's alloy furnace. */
@EventBusSubscriber(modid = MekanicalCreate.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipeCatalogLifecycle {
    private static final long FRAME_BUDGET_NANOS = 4_000_000L;
    private static final RecipeCatalogReloadState RELOAD = new RecipeCatalogReloadState();
    private static RecipeManager observedManager;
    private static long lastStatus;
    private static boolean showedLoading;

    private ClientRecipeCatalogLifecycle() {
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        SimulationDisplayRecipeCatalog.invalidate(event.getRecipeManager());
        RELOAD.recipesUpdated();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) return;
        var level = Minecraft.getInstance().level;
        if (level != null) SimulationDisplayRecipeCatalog.invalidate(level.getRecipeManager());
        RELOAD.tagsUpdated();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        RecipeManager manager = level == null ? null : level.getRecipeManager();
        if (manager != observedManager) {
            if (observedManager != null) SimulationDisplayRecipeCatalog.invalidate(observedManager);
            observedManager = manager;
            if (level == null) {
                RELOAD.reset();
                showedLoading = false;
                lastStatus = 0;
            } else {
                // Preserve tags that arrived before the client level was installed.
                RELOAD.worldJoined();
            }
        }
        if (level != null && ModList.get().isLoaded("jei") && RELOAD.shouldBeginBuild()) {
            SimulationDisplayRecipeCatalog.beginRebuild(level);
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !ModList.get().isLoaded("jei")) return;
        long deadline = System.nanoTime() + FRAME_BUDGET_NANOS;
        if (!RELOAD.isWaiting()) {
            SimulationDisplayRecipeCatalog.advance(level, FRAME_BUDGET_NANOS);
        }
        long remaining = deadline - System.nanoTime();
        if (remaining > 0) MekanicalCreateJeiPlugin.advanceRegistration(remaining);

        long now = System.nanoTime();
        if (minecraft.player == null || now - lastStatus < 1_000_000_000L) return;
        lastStatus = now;
        if (SimulationDisplayRecipeCatalog.failed(level) || MekanicalCreateJeiPlugin.publicationFailed()) {
            minecraft.gui.setOverlayMessage(Component.translatable("jei.mekanicalcreate.loading_failed"), false);
        } else if (RELOAD.isWaiting() || SimulationDisplayRecipeCatalog.peek(level) == null) {
            showedLoading = true;
            minecraft.gui.setOverlayMessage(Component.translatable("jei.mekanicalcreate.preparing",
                    SimulationDisplayRecipeCatalog.producedCount(level)), false);
        } else if (!MekanicalCreateJeiPlugin.registrationComplete()) {
            showedLoading = true;
            minecraft.gui.setOverlayMessage(Component.translatable("jei.mekanicalcreate.registering",
                    MekanicalCreateJeiPlugin.registeredCount(),
                    MekanicalCreateJeiPlugin.totalCount()), false);
        } else if (showedLoading) {
            showedLoading = false;
            minecraft.gui.setOverlayMessage(Component.translatable("jei.mekanicalcreate.ready"), false);
        }
    }
}
