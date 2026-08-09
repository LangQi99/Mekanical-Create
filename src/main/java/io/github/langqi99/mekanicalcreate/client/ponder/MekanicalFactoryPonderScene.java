package io.github.langqi99.mekanicalcreate.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import io.github.langqi99.mekanicalcreate.content.BlockMekanicalFactoryPort;
import io.github.langqi99.mekanicalcreate.content.MekanicalFactoryPortMode;
import io.github.langqi99.mekanicalcreate.registry.ModBlocks;
import java.util.List;
import mekanism.common.registries.MekanismBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** A deliberately compact visual guide to the factory's defining rules. */
public final class MekanicalFactoryPonderScene {
    private static final int SHELL_MIN = 2;
    private static final int SHELL_MAX = 6;
    private static final int SHELL_BOTTOM = 1;
    private static final int SHELL_TOP = 5;
    private static final int FRONT_Z = SHELL_MIN;

    private static final BlockPos CONTROLLER = new BlockPos(4, 4, FRONT_Z);
    private static final BlockPos INPUT_PORT = new BlockPos(3, 3, FRONT_Z);
    private static final BlockPos CATALYST_PORT = new BlockPos(4, 3, FRONT_Z);
    private static final BlockPos OUTPUT_PORT = new BlockPos(5, 3, FRONT_Z);
    private static final BlockPos ENERGY_PORT = new BlockPos(4, 2, FRONT_Z);

    private static final List<BlockPos> SPEED_CORES = List.of(
            new BlockPos(3, 2, 3),
            new BlockPos(3, 3, 3),
            new BlockPos(3, 4, 3),
            new BlockPos(3, 2, 4));
    private static final List<BlockPos> ENERGY_CORES = List.of(
            new BlockPos(4, 2, 3),
            new BlockPos(4, 3, 3),
            new BlockPos(4, 4, 3),
            new BlockPos(4, 3, 4));
    private static final List<BlockPos> FLUID_CORES = List.of(
            new BlockPos(5, 2, 3),
            new BlockPos(5, 3, 3));
    private static final List<BlockPos> CATALYST_CORES = List.of(
            new BlockPos(5, 4, 3),
            new BlockPos(5, 2, 4),
            new BlockPos(5, 3, 4));

    private MekanicalFactoryPonderScene() {
    }

    public static void build(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mekanical_factory_structure", "Mekanical Factory Structure");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.58F);

        resetTemplate(scene, util);
        showSupportedSizes(scene, util);
        showShellAndExternalComponents(scene, util);
        showInternalUpgrades(scene, util);
    }

    private static void resetTemplate(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, 8, 0, 8),
                Blocks.SMOOTH_STONE.defaultBlockState(), false);
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        scene.showBasePlate();
        scene.idle(12);
    }

    private static void showSupportedSizes(CreateSceneBuilder scene, SceneBuildingUtil util) {
        showSizeWireframe(scene, 3, PonderPalette.GREEN, 52);
        scene.overlay().showText(52)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("The smallest valid factory is 3x3x3")
                .placeNearTarget()
                .pointAt(util.vector().of(4.5, 4.0, 4.5));
        scene.idle(58);

        for (int size = 4; size <= 6; size++) {
            showSizeWireframe(scene, size, PonderPalette.WHITE, 16);
            scene.idle(16);
        }

        showSizeWireframe(scene, 7, PonderPalette.BLUE, 64);
        scene.overlay().showText(64)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("Every whole size through 7x7x7 is supported")
                .placeNearTarget()
                .pointAt(util.vector().of(4.5, 8.0, 4.5));
        scene.idle(72);
    }

    private static void showSizeWireframe(CreateSceneBuilder scene, int size,
            PonderPalette palette, int duration) {
        double min = 4.5D - size / 2.0D;
        double max = 4.5D + size / 2.0D;
        double bottom = SHELL_BOTTOM;
        double top = SHELL_BOTTOM + size;

        Vec3 bottomNorthWest = new Vec3(min, bottom, min);
        Vec3 bottomNorthEast = new Vec3(max, bottom, min);
        Vec3 bottomSouthWest = new Vec3(min, bottom, max);
        Vec3 bottomSouthEast = new Vec3(max, bottom, max);
        Vec3 topNorthWest = new Vec3(min, top, min);
        Vec3 topNorthEast = new Vec3(max, top, min);
        Vec3 topSouthWest = new Vec3(min, top, max);
        Vec3 topSouthEast = new Vec3(max, top, max);

        scene.overlay().showBigLine(palette, bottomNorthWest, bottomNorthEast, duration);
        scene.overlay().showBigLine(palette, bottomNorthEast, bottomSouthEast, duration);
        scene.overlay().showBigLine(palette, bottomSouthEast, bottomSouthWest, duration);
        scene.overlay().showBigLine(palette, bottomSouthWest, bottomNorthWest, duration);
        scene.overlay().showBigLine(palette, topNorthWest, topNorthEast, duration);
        scene.overlay().showBigLine(palette, topNorthEast, topSouthEast, duration);
        scene.overlay().showBigLine(palette, topSouthEast, topSouthWest, duration);
        scene.overlay().showBigLine(palette, topSouthWest, topNorthWest, duration);
        scene.overlay().showBigLine(palette, bottomNorthWest, topNorthWest, duration);
        scene.overlay().showBigLine(palette, bottomNorthEast, topNorthEast, duration);
        scene.overlay().showBigLine(palette, bottomSouthWest, topSouthWest, duration);
        scene.overlay().showBigLine(palette, bottomSouthEast, topSouthEast, duration);
    }

    private static void showShellAndExternalComponents(CreateSceneBuilder scene,
            SceneBuildingUtil util) {
        placeFiveByFiveShell(scene, util);
        Selection shell = shellSelection(util);
        scene.world().showSection(shell, Direction.DOWN);
        scene.overlay().showText(50)
                .attachKeyFrame()
                .text("This 5x5x5 shell is a clear mid-sized example")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, SHELL_TOP, 4), Direction.UP));
        scene.idle(58);

        scene.world().setBlock(CONTROLLER, controllerState(), false);
        scene.overlay().showOutline(PonderPalette.WHITE, "factory_controller",
                util.select().position(CONTROLLER), 54);
        scene.overlay().showText(54)
                .attachKeyFrame()
                .text("Place exactly one Controller and at least one Port on a flat face")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(CONTROLLER, Direction.NORTH));
        scene.idle(62);

        scene.world().setBlock(INPUT_PORT, portState(MekanicalFactoryPortMode.INPUT), false);
        scene.idle(5);
        scene.world().setBlock(CATALYST_PORT, portState(MekanicalFactoryPortMode.CATALYST), false);
        scene.idle(5);
        scene.world().setBlock(OUTPUT_PORT, portState(MekanicalFactoryPortMode.OUTPUT), false);
        scene.idle(5);
        scene.world().setBlock(ENERGY_PORT, portState(MekanicalFactoryPortMode.ENERGY), false);
        scene.overlay().showOutline(PonderPalette.RED, "input_port",
                util.select().position(INPUT_PORT), 66);
        scene.overlay().showOutline(PonderPalette.INPUT, "catalyst_port",
                util.select().position(CATALYST_PORT), 66);
        scene.overlay().showOutline(PonderPalette.BLUE, "output_port",
                util.select().position(OUTPUT_PORT), 66);
        scene.overlay().showOutline(PonderPalette.GREEN, "energy_port",
                util.select().position(ENERGY_PORT), 66);
        scene.overlay().showText(66)
                .attachKeyFrame()
                .text("Ports switch between red input, yellow catalyst, blue output, and green energy")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(CATALYST_PORT, Direction.NORTH));
        scene.idle(74);
    }

    private static void showInternalUpgrades(CreateSceneBuilder scene, SceneBuildingUtil util) {
        Selection frontFace = util.select().fromTo(
                SHELL_MIN, SHELL_BOTTOM, FRONT_Z,
                SHELL_MAX, SHELL_TOP, FRONT_Z);
        scene.world().hideSection(frontFace, Direction.NORTH);
        scene.rotateCameraY(18);
        scene.idle(28);

        Selection speed = positions(util, SPEED_CORES);
        placeBlocks(scene, SPEED_CORES,
                ModBlocks.MEKANICAL_FACTORY_SPEED_CORE.getBlock().defaultBlockState());
        scene.world().showSection(speed, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.FAST, "speed_cores", speed, 50);
        scene.overlay().showText(50)
                .attachKeyFrame()
                .colored(PonderPalette.FAST)
                .text("Only the first 4 Speed Cores take effect")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(3, 3, 3));
        scene.idle(58);

        Selection energy = positions(util, ENERGY_CORES);
        placeBlocks(scene, ENERGY_CORES,
                ModBlocks.MEKANICAL_FACTORY_ENERGY_CORE.getBlock().defaultBlockState());
        scene.world().showSection(energy, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "energy_cores", energy, 50);
        scene.overlay().showText(50)
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .text("Only the first 4 Energy Cores take effect")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(4, 3, 3));
        scene.idle(58);

        Selection fluid = positions(util, FLUID_CORES);
        placeBlocks(scene, FLUID_CORES,
                ModBlocks.MEKANICAL_FACTORY_FLUID_CORE.getBlock().defaultBlockState());
        scene.world().showSection(fluid, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "fluid_cores", fluid, 50);
        scene.overlay().showText(50)
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .text("Only the first 2 Fluid Cores take effect")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(5, 3, 3));
        scene.idle(62);

        Selection catalyst = positions(util, CATALYST_CORES);
        placeBlocks(scene, CATALYST_CORES,
                ModBlocks.MEKANICAL_FACTORY_CATALYST_CORE.getBlock().defaultBlockState());
        scene.world().showSection(catalyst, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.INPUT, "catalyst_cores", catalyst, 50);
        scene.overlay().showText(50)
                .attachKeyFrame()
                .colored(PonderPalette.INPUT)
                .text("Each of the first 3 Catalyst Cores unlocks 2 more slots")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(5, 3, 4));
        scene.idle(58);

        scene.rotateCameraY(-18);
        scene.world().showSection(frontFace, Direction.SOUTH);
        scene.idle(36);
    }

    private static void placeFiveByFiveShell(CreateSceneBuilder scene, SceneBuildingUtil util) {
        for (int x = SHELL_MIN; x <= SHELL_MAX; x++) {
            for (int y = SHELL_BOTTOM; y <= SHELL_TOP; y++) {
                for (int z = SHELL_MIN; z <= SHELL_MAX; z++) {
                    int boundaryCount = (x == SHELL_MIN || x == SHELL_MAX ? 1 : 0)
                            + (y == SHELL_BOTTOM || y == SHELL_TOP ? 1 : 0)
                            + (z == SHELL_MIN || z == SHELL_MAX ? 1 : 0);
                    if (boundaryCount == 0) {
                        continue;
                    }
                    BlockState state = boundaryCount >= 2
                            ? ModBlocks.MEKANICAL_FACTORY_CASING.getBlock().defaultBlockState()
                            : MekanismBlocks.STRUCTURAL_GLASS.getBlock().defaultBlockState();
                    scene.world().setBlock(util.grid().at(x, y, z), state, false);
                }
            }
        }
    }

    private static Selection shellSelection(SceneBuildingUtil util) {
        return util.select().fromTo(SHELL_MIN, SHELL_BOTTOM, SHELL_MIN,
                        SHELL_MAX, SHELL_BOTTOM, SHELL_MAX)
                .add(util.select().fromTo(SHELL_MIN, SHELL_TOP, SHELL_MIN,
                        SHELL_MAX, SHELL_TOP, SHELL_MAX))
                .add(util.select().fromTo(SHELL_MIN, SHELL_BOTTOM + 1, SHELL_MIN,
                        SHELL_MIN, SHELL_TOP - 1, SHELL_MAX))
                .add(util.select().fromTo(SHELL_MAX, SHELL_BOTTOM + 1, SHELL_MIN,
                        SHELL_MAX, SHELL_TOP - 1, SHELL_MAX))
                .add(util.select().fromTo(SHELL_MIN + 1, SHELL_BOTTOM + 1, SHELL_MIN,
                        SHELL_MAX - 1, SHELL_TOP - 1, SHELL_MIN))
                .add(util.select().fromTo(SHELL_MIN + 1, SHELL_BOTTOM + 1, SHELL_MAX,
                        SHELL_MAX - 1, SHELL_TOP - 1, SHELL_MAX));
    }

    private static BlockState controllerState() {
        return ModBlocks.FLUID_MEKANICAL_FACTORY.getBlock().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static BlockState portState(MekanicalFactoryPortMode mode) {
        return ModBlocks.MEKANICAL_FACTORY_PORT.getBlock().defaultBlockState()
                .setValue(BlockMekanicalFactoryPort.MODE, mode);
    }

    private static Selection positions(SceneBuildingUtil util, List<BlockPos> positions) {
        Selection selection = util.select().position(positions.get(0));
        for (int index = 1; index < positions.size(); index++) {
            selection = selection.add(util.select().position(positions.get(index)));
        }
        return selection;
    }

    private static void placeBlocks(CreateSceneBuilder scene, List<BlockPos> positions,
            BlockState state) {
        for (BlockPos pos : positions) {
            scene.world().setBlock(pos, state, false);
        }
    }
}
