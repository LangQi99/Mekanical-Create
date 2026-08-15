package io.github.langqi99.mekanicalcreate.content;

final class CreateSifterIds {
    static final String NAMESPACE = "createsifter";

    private CreateSifterIds() {
    }

    static boolean isSifterModule(String namespace, String path) {
        return NAMESPACE.equals(namespace)
                && ("sifter".equals(path) || "brass_sifter".equals(path));
    }

    static boolean isSiftingRecipeType(String namespace, String path) {
        return NAMESPACE.equals(namespace)
                && ("sifting_type".equals(path) || "sifting".equals(path));
    }
}
