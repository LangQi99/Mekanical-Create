package io.github.langqi99.mekanicalcreate.content;

import io.github.langqi99.mekanicalcreate.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 传动接口的容器：没有槽位，只负责把「需要 / 实际」应力同步到客户端。
 *
 * <p>无法复用 {@code MekanismTileContainer}，因为它的泛型要求方块实体继承
 * {@code TileEntityMekanism}，而接口是 Create 的动能方块。界面层仍然用
 * {@code GuiMekanism}（它的泛型只要求 {@code AbstractContainerMenu}），所以外观是真 Mek 样式。</p>
 */
public final class MekanicalStressInterfaceMenu extends AbstractContainerMenu {
    private final Inventory playerInventory;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private long demand;
    private long capacity;
    private float rpm;

    /** 服务端构造。 */
    public MekanicalStressInterfaceMenu(int id, Inventory inventory, FactoryStressPortBlockEntity port) {
        this(id, inventory, port.getBlockPos(), ContainerLevelAccess.create(port.getLevel(), port.getBlockPos()));
    }

    /** 客户端构造：只拿坐标，数值靠 DataSlot 同步。 */
    public MekanicalStressInterfaceMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), ContainerLevelAccess.NULL);
    }

    private MekanicalStressInterfaceMenu(int id, Inventory inventory, BlockPos pos, ContainerLevelAccess access) {
        super(ModMenus.STRESS_INTERFACE.get(), id);
        this.playerInventory = inventory;
        this.pos = pos;
        this.access = access;
        // 客户端同样要注册 DataSlot，否则服务端发的值没有接收端，界面永远是 0
        addStressSlots();
    }

    private void addStressSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                FactoryStressPortBlockEntity port = tile();
                return port == null ? (int) rpm : (int) port.getSpeed();
            }

            @Override
            public void set(int value) {
                rpm = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (int) Math.min(Integer.MAX_VALUE, liveDemand());
            }

            @Override
            public void set(int value) {
                demand = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (int) Math.min(Integer.MAX_VALUE, liveCapacity());
            }

            @Override
            public void set(int value) {
                capacity = value;
            }
        });
    }

    private long liveDemand() {
        FactoryStressPortBlockEntity port = tile();
        return port == null ? demand : port.totalGroupDemand();
    }

    private long liveCapacity() {
        FactoryStressPortBlockEntity port = tile();
        return port == null ? capacity : port.networkCapacity();
    }

    public long demand() {
        return demand;
    }

    public long capacity() {
        return capacity;
    }

    /** 接口轴的转速（RPM），只影响效率，没有门槛。 */
    public float rpm() {
        FactoryStressPortBlockEntity port = tile();
        return port == null ? rpm : port.getSpeed();
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, player.level().getBlockState(pos).getBlock());
    }

    /** 供屏幕读取：客户端侧从方块实体兜底。 */
    public FactoryStressPortBlockEntity tile() {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof FactoryStressPortBlockEntity port) {
            return port;
        }
        return null;
    }

    public BlockEntity blockEntity() {
        return playerInventory.player.level().getBlockEntity(pos);
    }
}
