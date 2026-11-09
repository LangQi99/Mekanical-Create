package io.github.langqi99.mekanicalcreate.content;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface KineticMachine extends IHaveGoggleInformation, IHaveHoveringInformation {
    MachineShaft shaft();

    @Override
    default boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return shaft().addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    @Override
    default boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return shaft().addToTooltip(tooltip, isPlayerSneaking);
    }
}
