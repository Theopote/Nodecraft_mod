package com.nodecraft.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class PortDataRendererRegistry {
    private static final List<TypedEntry> TYPED_ENTRIES = new ArrayList<>();
    private static final List<PredicateEntry> PREDICATE_ENTRIES = new ArrayList<>();

    private PortDataRendererRegistry() {
    }

    static void registerType(Class<?> type, BiConsumer<PortDataRenderer, Object> renderer) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("type and renderer must not be null");
        }
        TYPED_ENTRIES.add(new TypedEntry(type, renderer));
    }

    static void registerPredicate(Predicate<Object> predicate, BiConsumer<PortDataRenderer, Object> renderer) {
        if (predicate == null || renderer == null) {
            throw new IllegalArgumentException("predicate and renderer must not be null");
        }
        PREDICATE_ENTRIES.add(new PredicateEntry(predicate, renderer));
    }

    static boolean render(PortDataRenderer owner, Object value) {
        for (TypedEntry entry : TYPED_ENTRIES) {
            if (entry.type.isAssignableFrom(value.getClass())) {
                entry.renderer.accept(owner, value);
                return true;
            }
        }
        for (PredicateEntry entry : PREDICATE_ENTRIES) {
            if (entry.predicate.test(value)) {
                entry.renderer.accept(owner, value);
                return true;
            }
        }
        return false;
    }

    private record TypedEntry(Class<?> type, BiConsumer<PortDataRenderer, Object> renderer) {
    }

    private record PredicateEntry(Predicate<Object> predicate, BiConsumer<PortDataRenderer, Object> renderer) {
    }
}

