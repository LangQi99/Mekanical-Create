package io.github.langqi99.mekanicalcreate.content;

/** Shared coordinates for the compact, variable-width multiblock GUI. */
public final class MekanicalFactoryGuiLayout {
    public static final int IMAGE_HEIGHT = 190;
    public static final int INVENTORY_Y_OFFSET = 106;
    public static final int INVENTORY_LABEL_Y = 95;

    public static final int POWER_BAR_X = 11;
    public static final int POWER_BAR_Y = 24;
    public static final int ENERGY_SLOT_X = 6;
    public static final int ENERGY_SLOT_Y = 84;

    public static final int SLOT_SIZE = 18;
    public static final int GROUP_GAP = 5;
    public static final int PROCESS_START_X = 35;
    public static final int PROCESS_START_Y = 24;
    public static final int INPUT_GRID_WIDTH = 4 * SLOT_SIZE;

    public static final int FLUID_GAUGE_Y = PROCESS_START_Y;
    public static final int FLUID_GAUGE_WIDTH = 18;
    // Two pixels between adjacent 18px gauges keeps every 1-3 gauge group an
    // even width, so the container slot below can be centered exactly.
    public static final int FLUID_GAUGE_SPACING = 20;
    public static final int FLUID_CONTAINER_Y = 84;

    public static final int PROGRESS_WIDTH = 28;
    public static final int PROGRESS_Y = 43;

    public static final int OUTPUT_SLOT_Y = PROCESS_START_Y;
    private static final int RIGHT_MARGIN = 6;

    private MekanicalFactoryGuiLayout() {
    }

    /**
     * Catalyst slots fill downward first: 2, 4, then 4+2, finally 4+4.
     */
    public static int catalystX(int index) {
        return PROCESS_START_X + index / 4 * SLOT_SIZE;
    }

    public static int catalystY(int index) {
        return PROCESS_START_Y + index % 4 * SLOT_SIZE;
    }

    public static int catalystColumnCount(int catalystSlotCount) {
        return Math.max(1, (clampedCatalystSlotCount(catalystSlotCount) + 3) / 4);
    }

    public static int catalystGroupWidth(int catalystSlotCount) {
        return catalystColumnCount(catalystSlotCount) * SLOT_SIZE;
    }

    public static int inputStartX(int catalystSlotCount) {
        return PROCESS_START_X + catalystGroupWidth(catalystSlotCount) + GROUP_GAP;
    }

    public static int inputX(int catalystSlotCount, int index) {
        return inputStartX(catalystSlotCount) + index % 4 * SLOT_SIZE;
    }

    public static int inputY(int index) {
        return PROCESS_START_Y + index / 4 * SLOT_SIZE;
    }

    public static int fluidGaugeGroupWidth(int tankCount) {
        return (clampedTankCount(tankCount) - 1) * FLUID_GAUGE_SPACING
                + FLUID_GAUGE_WIDTH;
    }

    public static int inputFluidStartX(int catalystSlotCount) {
        return inputStartX(catalystSlotCount) + INPUT_GRID_WIDTH + GROUP_GAP;
    }

    public static int inputFluidGaugeX(int tankCount, int catalystSlotCount, int index) {
        return inputFluidStartX(catalystSlotCount) + index * FLUID_GAUGE_SPACING;
    }

    public static int progressX(int tankCount, int catalystSlotCount) {
        return inputFluidStartX(catalystSlotCount)
                + fluidGaugeGroupWidth(tankCount) + GROUP_GAP;
    }

    public static int outputFluidStartX(int tankCount, int catalystSlotCount) {
        return progressX(tankCount, catalystSlotCount) + PROGRESS_WIDTH + GROUP_GAP;
    }

    public static int outputFluidGaugeX(int tankCount, int catalystSlotCount, int index) {
        return outputFluidStartX(tankCount, catalystSlotCount)
                + index * FLUID_GAUGE_SPACING;
    }

    public static int inputFluidContainerX(int tankCount, int catalystSlotCount) {
        return inputFluidStartX(catalystSlotCount)
                + (fluidGaugeGroupWidth(tankCount) - SLOT_SIZE) / 2 + 1;
    }

    public static int outputFluidContainerX(int tankCount, int catalystSlotCount) {
        return outputFluidStartX(tankCount, catalystSlotCount)
                + (fluidGaugeGroupWidth(tankCount) - SLOT_SIZE) / 2 + 1;
    }

    public static int outputSlotX(int tankCount, int catalystSlotCount, int index) {
        return outputFluidStartX(tankCount, catalystSlotCount)
                + fluidGaugeGroupWidth(tankCount) + GROUP_GAP;
    }

    public static int outputSlotY(int index) {
        return OUTPUT_SLOT_Y + index * SLOT_SIZE;
    }

    public static int imageWidth(int tankCount, int catalystSlotCount) {
        return Math.max(176, outputSlotX(tankCount, catalystSlotCount, 0)
                + SLOT_SIZE + RIGHT_MARGIN);
    }

    public static int inventoryXOffset(int tankCount, int catalystSlotCount) {
        return (imageWidth(tankCount, catalystSlotCount) - 162) / 2;
    }

    public static int inventoryLabelX(int tankCount, int catalystSlotCount) {
        return inventoryXOffset(tankCount, catalystSlotCount);
    }

    private static int clampedTankCount(int tankCount) {
        return Math.max(1, Math.min(MekanicalFactoryMultiblockData.MAX_FLUID_TANK_COUNT,
                tankCount));
    }

    private static int clampedCatalystSlotCount(int catalystSlotCount) {
        return Math.max(MekanicalFactoryMultiblockData.BASE_CATALYST_SLOT_COUNT,
                Math.min(MekanicalFactoryMultiblockData.MAX_CATALYST_SLOT_COUNT,
                        catalystSlotCount));
    }
}
