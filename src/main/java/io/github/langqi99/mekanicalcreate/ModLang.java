package io.github.langqi99.mekanicalcreate;

import mekanism.api.text.ILangEntry;

public enum ModLang implements ILangEntry {
    DESCRIPTION_SIMULATION_CHAMBER("description.mekanicalcreate.simulation_chamber"),
    DESCRIPTION_FLUID_MEKANICAL_FACTORY("description.mekanicalcreate.fluid_mekanical_factory");

    private final String translationKey;

    ModLang(String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
