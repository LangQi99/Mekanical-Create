package io.github.langqi99.mekanicalcreate.gametest;

import com.simibubi.create.AllBlocks;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.List;
import mekanism.api.Upgrade;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismItems;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MekanicalCreate.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactoryUpgradeGameTests {
    private FactoryUpgradeGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void tierInstallersPreserveEveryMachineInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SIMULATION_CHAMBER.getBlock());
        helper.runAfterDelay(2, () -> {
            SimulationChamberBlockEntity original =
                    (SimulationChamberBlockEntity) helper.getBlockEntity(pos);
            List<mekanism.api.inventory.IInventorySlot> slots = original.getInventorySlots(null);
            slots.get(0).setStack(AllBlocks.ENCASED_FAN.asStack());
            slots.get(1).setStack(new ItemStack(Items.WATER_BUCKET));
            for (int input = 0; input < SimulationChamberBlockEntity.INPUT_COUNT; input++) {
                slots.get(input + 2).setStack(new ItemStack(Items.BARRIER, input + 1));
            }
            for (int output = 0; output < SimulationChamberBlockEntity.OUTPUT_COUNT; output++) {
                slots.get(output + 18).setStack(new ItemStack(Items.STRUCTURE_VOID, output + 11));
            }
            original.getEnergyContainer().setEnergy(FloatingLong.create(12_345));
            original.getComponent().addUpgrades(Upgrade.SPEED, 3);
            original.getComponent().addUpgrades(Upgrade.ENERGY, 2);

            var player = helper.makeMockPlayer();
            player.setShiftKeyDown(true);
            player.setPos(helper.absolutePos(pos).getCenter());
            List<ItemStack> installers = List.of(
                    MekanismItems.BASIC_TIER_INSTALLER.getItemStack(),
                    MekanismItems.ADVANCED_TIER_INSTALLER.getItemStack(),
                    MekanismItems.ELITE_TIER_INSTALLER.getItemStack(),
                    MekanismItems.ULTIMATE_TIER_INSTALLER.getItemStack());
            List<Block> expectedBlocks = List.of(
                    ModBlocks.BASIC_MEKANICAL_FACTORY.getBlock(),
                    ModBlocks.ADVANCED_MEKANICAL_FACTORY.getBlock(),
                    ModBlocks.ELITE_MEKANICAL_FACTORY.getBlock(),
                    ModBlocks.ULTIMATE_MEKANICAL_FACTORY.getBlock());
            for (int tier = 0; tier < installers.size(); tier++) {
                ItemStack installer = installers.get(tier);
                player.setItemInHand(InteractionHand.MAIN_HAND, installer);
                BlockPos absolute = helper.absolutePos(pos);
                var hit = new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false);
                InteractionResult result = installer.useOn(new UseOnContext(player,
                        InteractionHand.MAIN_HAND, hit));
                helper.assertTrue(result.consumesAction(), "factory installer did not apply");
                helper.assertTrue(helper.getBlockState(pos).is(expectedBlocks.get(tier)),
                        "factory installer created the wrong factory tier");

                SimulationChamberBlockEntity upgraded =
                        (SimulationChamberBlockEntity) helper.getBlockEntity(pos);
                List<mekanism.api.inventory.IInventorySlot> upgradedSlots = upgraded.getInventorySlots(null);
                helper.assertTrue(upgradedSlots.get(0).getStack().is(AllBlocks.ENCASED_FAN.asItem()),
                        "factory installer lost the processing module");
                helper.assertTrue(upgradedSlots.get(1).getStack().is(Items.WATER_BUCKET),
                        "factory installer lost the condition item");
                for (int input = 0; input < SimulationChamberBlockEntity.INPUT_COUNT; input++) {
                    ItemStack stack = upgradedSlots.get(input + 2).getStack();
                    helper.assertTrue(stack.is(Items.BARRIER) && stack.getCount() == input + 1,
                            "factory installer lost input stack " + input);
                }
                for (int output = 0; output < SimulationChamberBlockEntity.OUTPUT_COUNT; output++) {
                    ItemStack stack = upgradedSlots.get(output + 18).getStack();
                    helper.assertTrue(stack.is(Items.STRUCTURE_VOID) && stack.getCount() == output + 11,
                            "factory installer lost output stack " + output);
                }
                helper.assertTrue(upgraded.getEnergyContainer().getEnergy().longValue() == 12_345L,
                        "factory installer lost stored energy");
                helper.assertTrue(upgraded.getComponent().getUpgrades(Upgrade.SPEED) == 3,
                        "factory installer lost installed speed upgrades");
                helper.assertTrue(upgraded.getComponent().getUpgrades(Upgrade.ENERGY) == 2,
                        "factory installer lost installed energy upgrades");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void upgradeSnapshotDoesNotDependOnRemovedMachineSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SIMULATION_CHAMBER.getBlock());
        helper.runAfterDelay(2, () -> {
            SimulationChamberBlockEntity original =
                    (SimulationChamberBlockEntity) helper.getBlockEntity(pos);
            List<mekanism.api.inventory.IInventorySlot> oldSlots = original.getInventorySlots(null);
            oldSlots.get(0).setStack(AllBlocks.MILLSTONE.asStack());
            oldSlots.get(2).setStack(new ItemStack(Items.DIAMOND, 23));
            oldSlots.get(18).setStack(new ItemStack(Items.EMERALD, 17));
            original.getEnergyContainer().setEnergy(FloatingLong.create(54_321));

            IUpgradeData snapshot = original.getUpgradeData();
            for (mekanism.api.inventory.IInventorySlot slot : oldSlots) {
                slot.setStack(ItemStack.EMPTY);
            }
            original.getEnergyContainer().setEnergy(FloatingLong.ZERO);

            helper.setBlock(pos, ModBlocks.BASIC_MEKANICAL_FACTORY.getBlock());
            SimulationChamberBlockEntity upgraded =
                    (SimulationChamberBlockEntity) helper.getBlockEntity(pos);
            upgraded.parseUpgradeData(snapshot);
            List<mekanism.api.inventory.IInventorySlot> restored = upgraded.getInventorySlots(null);

            helper.assertTrue(restored.get(0).getStack().is(AllBlocks.MILLSTONE.asItem()),
                    "upgrade snapshot retained a live reference to the old module slot");
            helper.assertTrue(restored.get(2).getStack().is(Items.DIAMOND)
                            && restored.get(2).getStack().getCount() == 23,
                    "upgrade snapshot retained a live reference to the old input slot");
            helper.assertTrue(restored.get(18).getStack().is(Items.EMERALD)
                            && restored.get(18).getStack().getCount() == 17,
                    "upgrade snapshot retained a live reference to the old output slot");
            helper.assertTrue(upgraded.getEnergyContainer().getEnergy().longValue() == 54_321L,
                    "upgrade snapshot retained a live reference to the old energy container");
            helper.succeed();
        });
    }
}
