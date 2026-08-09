package io.github.langqi99.mekanicalcreate.content;

import java.util.List;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.multiblock.MultiblockCache;

/**
 * Pads pre-catalyst-core caches before Mekanism performs its index-based sync.
 * This keeps worlds created with the original 25-slot layout loadable after
 * the six expandable catalyst slots were appended.
 */
public final class MekanicalFactoryMultiblockCache
        extends MultiblockCache<MekanicalFactoryMultiblockData> {
    @Override
    public void sync(MekanicalFactoryMultiblockData data) {
        padInventory(getInventorySlots(null), data.getInventorySlots(null).size());
        super.sync(data);
    }

    @Override
    public void merge(MultiblockCache<MekanicalFactoryMultiblockData> other,
                      RejectContents rejectContents) {
        List<IInventorySlot> ownSlots = getInventorySlots(null);
        List<IInventorySlot> otherSlots = other.getInventorySlots(null);
        int slotCount = Math.max(ownSlots.size(), otherSlots.size());
        padInventory(ownSlots, slotCount);
        padInventory(otherSlots, slotCount);
        super.merge(other, rejectContents);
    }

    private static void padInventory(List<IInventorySlot> slots, int slotCount) {
        while (slots.size() < slotCount) {
            slots.add(BasicInventorySlot.at(null, 0, 0));
        }
    }
}
