package io.github.langqi99.mekanicalcreate.content;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/** The four Mek-style transfer channels exposed by a factory port. */
public enum MekanicalFactoryPortMode implements StringRepresentable {
    INPUT(ChatFormatting.RED),
    OUTPUT(ChatFormatting.AQUA),
    CATALYST(ChatFormatting.YELLOW),
    ENERGY(ChatFormatting.GREEN);

    private final ChatFormatting color;

    MekanicalFactoryPortMode(ChatFormatting color) {
        this.color = color;
    }

    public MekanicalFactoryPortMode next() {
        MekanicalFactoryPortMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public Component getTextComponent() {
        return Component.translatable("port_mode.mekanicalcreate." + getSerializedName())
                .withStyle(color);
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
