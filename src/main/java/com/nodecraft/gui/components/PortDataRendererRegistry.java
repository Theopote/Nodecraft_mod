package com.nodecraft.gui.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

final class PortDataRendererRegistry {
    @FunctionalInterface
    interface PortValueRenderer {
        void render(PortDataRenderer owner, Object value, String label);
    }

    private static final List<TypedEntry> TYPED_ENTRIES = new ArrayList<>();
    private static final List<PredicateEntry> PREDICATE_ENTRIES = new ArrayList<>();

    private PortDataRendererRegistry() {
    }

    static void registerType(Class<?> type, PortValueRenderer renderer) {
        registerType(type, renderer, 0);
    }

    static void registerType(Class<?> type, PortValueRenderer renderer, int priority) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("type and renderer must not be null");
        }
        TYPED_ENTRIES.add(new TypedEntry(type, renderer, priority));
        TYPED_ENTRIES.sort(Comparator.comparingInt(TypedEntry::priority).reversed());
    }

    static void registerPredicate(Predicate<Object> predicate, PortValueRenderer renderer) {
        registerPredicate(predicate, renderer, 0);
    }

    static void registerPredicate(Predicate<Object> predicate, PortValueRenderer renderer, int priority) {
        if (predicate == null || renderer == null) {
            throw new IllegalArgumentException("predicate and renderer must not be null");
        }
        PREDICATE_ENTRIES.add(new PredicateEntry(predicate, renderer, priority));
        PREDICATE_ENTRIES.sort(Comparator.comparingInt(PredicateEntry::priority).reversed());
    }

    static boolean render(PortDataRenderer owner, Object value, String label) {
        for (TypedEntry entry : TYPED_ENTRIES) {
            if (entry.type.isAssignableFrom(value.getClass())) {
                entry.renderer.render(owner, value, label);
                return true;
            }
        }
        for (PredicateEntry entry : PREDICATE_ENTRIES) {
            if (entry.predicate.test(value)) {
                entry.renderer.render(owner, value, label);
                return true;
            }
        }
        return false;
    }

    private record TypedEntry(Class<?> type, PortValueRenderer renderer, int priority) {
    }

    private record PredicateEntry(Predicate<Object> predicate, PortValueRenderer renderer, int priority) {
    }
}

