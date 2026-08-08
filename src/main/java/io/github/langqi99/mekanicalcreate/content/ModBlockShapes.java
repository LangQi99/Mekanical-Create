package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.util.EnumUtils;
import mekanism.common.util.VoxelShapeUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision geometry kept in lockstep with the detailed chamber model. */
public final class ModBlockShapes {
    public static final VoxelShape[] SIMULATION_CHAMBER =
            new VoxelShape[EnumUtils.HORIZONTAL_DIRECTIONS.length];

    static {
        VoxelShapeUtils.setShape(VoxelShapeUtils.rotate(VoxelShapeUtils.combine(
                box(0, 0, 0, 16, 4, 16),
                box(0, 4, 0, 7, 14, 7),
                box(0, 4, 9, 7, 14, 16),
                box(8, 4, 0, 16, 16, 16),
                box(2.5, 3.5, 0, 7.5, 8.5, 1),
                box(7, 4, 1, 8, 13, 15),
                box(3, 10, 3, 8, 16, 13)
        ), Rotation.CLOCKWISE_90), SIMULATION_CHAMBER);

    }

    private ModBlockShapes() {
    }

    private static VoxelShape box(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
