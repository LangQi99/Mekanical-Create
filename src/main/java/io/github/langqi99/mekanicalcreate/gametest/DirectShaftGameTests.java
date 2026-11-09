package io.github.langqi99.mekanicalcreate.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import io.github.langqi99.mekanicalcreate.content.MachineShaftPlacement;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryControllerBlockEntity;
import io.github.langqi99.mekanicalcreate.content.SimulationChamberBlockEntity;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MekanicalCreate.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DirectShaftGameTests {
    private DirectShaftGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void machinePassesRotationAndDetachesWhenTurned(GameTestHelper helper) {
        BlockPos motorPos = new BlockPos(0, 1, 0);
        BlockPos machinePos = motorPos.east();
        BlockPos downstreamPos = machinePos.east();
        BlockState machineState = ModBlocks.SIMULATION_CHAMBER.get().defaultBlockState();
        helper.setBlock(motorPos, motorFacing(Direction.EAST));
        helper.setBlock(machinePos, machineState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.SHAFT.asStack());
        BlockPos absoluteMachine = helper.absolutePos(machinePos);
        helper.useBlock(machinePos, player, hitFace(absoluteMachine, Direction.EAST));
        BlockState placedShaft = helper.getBlockState(downstreamPos);
        helper.assertTrue(AllBlocks.SHAFT.has(placedShaft)
                        && placedShaft.getValue(BlockStateProperties.AXIS) == Direction.Axis.X,
                "Clicking the machine's side did not place an aligned shaft");
        prepareMilling(helper.getBlockEntity(machinePos));

        helper.startSequence()
                .thenWaitUntil(() -> {
                    KineticBlockEntity downstream = helper.getBlockEntity(downstreamPos);
                    SimulationChamberBlockEntity machine = helper.getBlockEntity(machinePos);
                    helper.assertTrue(downstream.getSpeed() != 0, "Rotation did not pass through the machine");
                    helper.assertTrue(machine.getScaledProgress() > 0, "The directly powered chamber did not mill");
                })
                .thenExecute(() -> helper.setBlock(machinePos,
                        machineState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)))
                .thenIdle(4)
                .thenExecute(() -> {
                    KineticBlockEntity downstream = helper.getBlockEntity(downstreamPos);
                    SimulationChamberBlockEntity machine = helper.getBlockEntity(machinePos);
                    helper.assertTrue(downstream.getTheoreticalSpeed() == 0, "Turning the chamber left the old shaft powered");
                    helper.assertTrue(machine.shaft().getSpeed() == 0, "The old shaft axis still powered the chamber");
                    helper.setBlock(machinePos.south(), motorFacing(Direction.NORTH));
                })
                .thenWaitUntil(() -> {
                    SimulationChamberBlockEntity machine = helper.getBlockEntity(machinePos);
                    helper.assertTrue(machine.shaft().getSpeed() != 0,
                            "The chamber did not pick up the new shaft axis");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void shaftPreviewMatchesPlacementAndRespectsTheSocket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        BlockState machine = ModBlocks.SIMULATION_CHAMBER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.setBlock(pos, machine);
        helper.setBlock(pos.south(), AllBlocks.SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z));

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack stack = AllBlocks.SHAFT.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var placement = PlacementHelpers.getHelpersView().stream()
                .filter(h -> h instanceof MachineShaftPlacement && h.matchesItem(stack) && h.matchesState(machine))
                .findFirst().orElseThrow(() -> new IllegalStateException("Machine helper missing from Create's preview list"));
        BlockHitResult socket = hitFace(absolute, Direction.SOUTH);
        var preview = placement.getOffset(player, helper.getLevel(), machine, absolute, socket, stack);
        helper.assertTrue(preview.isSuccessful() && preview.hasGhostState()
                        && preview.getBlockPos().equals(absolute.south(2)),
                "The preview must extend the existing shaft on the clicked side");
        BlockState ghost = preview.getTransform().apply(preview.getGhostState());
        helper.assertTrue(AllBlocks.SHAFT.has(ghost) && ghost.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z,
                "The preview shaft did not align with the machine");

        helper.useBlock(pos, player, socket);
        helper.assertTrue(helper.getBlockState(pos.south(2)) == ghost,
                "The actual placement did not match the preview");
        helper.setBlock(pos.south(3), Blocks.STONE);
        helper.assertTrue(!placement.getOffset(player, helper.getLevel(), machine, absolute, socket, stack).isSuccessful(),
                "A blocked socket must not suggest placement on the opposite side");
        helper.assertTrue(!placement.getOffset(player, helper.getLevel(), machine, absolute,
                hitFace(absolute, Direction.EAST), stack).isSuccessful(), "A face without a socket showed a preview");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void idleShaftsChargeAndStopGeneratingWhenOverloaded(GameTestHelper helper) {
        BlockPos motorPos = new BlockPos(0, 1, 0);
        BlockPos firstPos = motorPos.east();
        BlockPos secondPos = firstPos.east();
        helper.setBlock(motorPos, motorFacing(Direction.EAST));
        BlockState machineState = ModBlocks.SIMULATION_CHAMBER.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(firstPos, machineState);
        helper.setBlock(secondPos, machineState);
        SimulationChamberBlockEntity first = helper.getBlockEntity(firstPos);
        SimulationChamberBlockEntity second = helper.getBlockEntity(secondPos);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
                    helper.assertTrue(motor.getSpeed() == 16 && motor.hasNetwork(), "The motor did not start");
                    helper.assertTrue(first.getEnergyContainer().getEnergy() >= 22
                                    && second.getEnergyContainer().getEnergy() >= 22,
                            "Idle machines should charge without any recipe or FE cable");
                    helper.assertTrue(motor.getOrCreateNetwork().calculateStress() == 2048,
                            "Two alternator loads at 16 RPM should take 2048 SU");
                })
                .thenExecute(() -> {
                    CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
                    motor.getOrCreateNetwork().updateCapacityFor(motor, 64);
                    first.getEnergyContainer().setEnergy(0);
                    second.getEnergyContainer().setEnergy(0);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
                    helper.assertTrue(motor.isOverStressed() && first.getEnergyContainer().getEnergy() == 0
                                    && second.getEnergyContainer().getEnergy() == 0,
                            "An overloaded shaft must not charge either machine");
                    helper.setBlock(secondPos, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    CreativeMotorBlockEntity motor = helper.getBlockEntity(motorPos);
                    long energy = first.getEnergyContainer().getEnergy();
                    helper.assertTrue(motor.getSpeed() == 16 && motor.getOrCreateNetwork().calculateStress() == 1024,
                            "Removing one alternator should leave 1024 SU of load at 16 RPM");
                    helper.assertTrue(energy >= 22 && energy % 22 == 0,
                            "Generation should resume at 22 FE/t once the load fits");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void fluidFactoryReceivesPowerAtTheControllerFace(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(1, 2, 1);
        BlockPos motorPos = controllerPos.north();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 1), new BlockPos(2, 3, 3))) {
            if (!pos.equals(new BlockPos(1, 2, 2))) {
                helper.setBlock(pos, ModBlocks.MEKANICAL_FACTORY_CASING.get());
            }
        }
        helper.setBlock(new BlockPos(1, 2, 3), ModBlocks.MEKANICAL_FACTORY_PORT.get());
        helper.setBlock(controllerPos, ModBlocks.FLUID_MEKANICAL_FACTORY.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));

        MekanicalFactoryControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.getMultiblock().isFormed(), "Factory did not form"))
                .thenExecute(() -> {
                    controller.getCatalystSlots().getFirst().setStack(AllBlocks.MILLSTONE.asStack());
                    controller.getInputSlots().getFirst().setStack(new ItemStack(Items.WHEAT, 16));
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(controller.getScaledProgress() == 0
                                    && controller.getEnergyContainer().getEnergy() == 0,
                            "An unpowered fluid factory consumed work or energy");
                    helper.setBlock(motorPos, motorFacing(Direction.SOUTH));
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(controller.getScaledProgress() > 0, "Direct shaft did not power the fluid factory");
                    helper.assertTrue(controller.shaft().getOrCreateNetwork().calculateStress() == 1_024,
                            "The formed controller should have one alternator load");
                })
                .thenExecute(() -> {
                    helper.setBlock(motorPos, Blocks.AIR);
                    controller.getEnergyContainer().setEnergy(50_000);
                })
                .thenIdle(4)
                .thenExecute(() -> helper.assertTrue(controller.getMultiblock().isActive()
                                && controller.shaft().getSpeed() == 0
                                && controller.getEnergyContainer().getEnergy() < 50_000,
                        "The fluid factory should continue on stored FE after disconnecting the shaft"))
                .thenSucceed();
    }

    private static BlockHitResult hitFace(BlockPos pos, Direction face) {
        return new BlockHitResult(pos.getCenter().relative(face, 0.5), face, pos, false);
    }

    private static BlockState motorFacing(Direction direction) {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, direction);
    }

    private static void prepareMilling(SimulationChamberBlockEntity machine) {
        machine.getModuleSlot().setStack(AllBlocks.MILLSTONE.asStack());
        machine.getInputSlots().getFirst().setStack(new ItemStack(Items.WHEAT, 16));
    }
}
