package io.github.langqi99.mekanicalcreate;

import mekanism.api.text.ILangEntry;

public enum ModLang implements ILangEntry {
    DESCRIPTION_SIMULATION_CHAMBER("description.mekanicalcreate.simulation_chamber"),
    DESCRIPTION_FLUID_MEKANICAL_FACTORY("description.mekanicalcreate.fluid_mekanical_factory"),
    DESCRIPTION_MEKANICAL_FACTORY_CASING("description.mekanicalcreate.mekanical_factory_casing"),
    DESCRIPTION_MEKANICAL_FACTORY_PORT("description.mekanicalcreate.mekanical_factory_port"),
    DESCRIPTION_MEKANICAL_FACTORY_SPEED_CORE("description.mekanicalcreate.mekanical_factory_speed_core"),
    DESCRIPTION_MEKANICAL_FACTORY_ENERGY_CORE("description.mekanicalcreate.mekanical_factory_energy_core"),
    DESCRIPTION_MEKANICAL_FACTORY_FLUID_CORE("description.mekanicalcreate.mekanical_factory_fluid_core"),
    DESCRIPTION_MEKANICAL_FACTORY_CATALYST_CORE("description.mekanicalcreate.mekanical_factory_catalyst_core");

    private final String translationKey;

    ModLang(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
