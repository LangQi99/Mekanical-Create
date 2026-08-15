package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.MekanicalCreate;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import mekanism.api.providers.IBlockProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Common external node for the fixed Mekanical Factory multiblock. */
public class MekanicalFactoryBlockEntity extends TileEntityMultiblock<MekanicalFactoryMultiblockData> {
    private final RecipeRoundRobinState roundRobinState = new RecipeRoundRobinState();

    protected MekanicalFactoryBlockEntity(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    @NotNull
    @Override
    public MekanicalFactoryMultiblockData createMultiblock() {
        return new MekanicalFactoryMultiblockData(this);
    }

    @Override
    public MultiblockManager<MekanicalFactoryMultiblockData> getManager() {
        return MekanicalCreate.MEKANICAL_FACTORY_MANAGER;
    }

    @Override
    public boolean canBeMaster() {
        return false;
    }

    RecipeRoundRobinState getRecipeRoundRobinState() {
        return roundRobinState;
    }

    void roundRobinChanged() {
        markForSave();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        RecipeRoundRobinNbt.write(tag, roundRobinState);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        RecipeRoundRobinNbt.read(tag, roundRobinState);
    }
}
