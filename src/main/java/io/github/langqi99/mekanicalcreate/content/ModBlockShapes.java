package io.github.langqi99.mekanicalcreate.content;

import mekanism.common.util.EnumUtils;
import mekanism.common.util.VoxelShapeUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Collision geometry kept in lockstep with the detailed chamber model. */
public final class ModBlockShapes {
    public static final VoxelShape[] SIMULATION_CHAMBER =
            new VoxelShape[EnumUtils.HORIZONTAL_DIRECTIONS.length];

    static {
        VoxelShapeUtils.setShape(VoxelShapeUtils.combine(
                box(0, 0, 0, 16, 16, 4),
                box(4, 4, 14, 12, 12, 16),
                box(1, 7, 4, 15, 14, 14),
                box(2, 4, 4, 14, 7, 10),
                box(5, 5, 10, 11, 7, 14),
                box(0, 0, 4, 16, 4, 16),
                box(0, 14, 4, 16, 16, 16),
                box(0, 6, 4, 2, 14, 16),
                box(14, 6, 4, 16, 14, 16)
        ), SIMULATION_CHAMBER);

    }

    private ModBlockShapes() {
    }

    private static VoxelShape box(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
