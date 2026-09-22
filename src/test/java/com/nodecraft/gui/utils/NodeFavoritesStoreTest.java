package com.nodecraft.gui.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeFavoritesStoreTest {

    @BeforeEach
    @AfterEach
    void resetFavorites() {
        NodeFavoritesStore.clear();
    }

    @Test
    void toggleAddsAndRemovesFavorite() {
        assertFalse(NodeFavoritesStore.isFavorite("math.trig.sine"));
        assertTrue(NodeFavoritesStore.toggle("math.trig.sine"));
        assertTrue(NodeFavoritesStore.isFavorite("Math.Trig.Sine"));
        assertEquals(List.of("math.trig.sine"), NodeFavoritesStore.getFavoriteIds());

        assertFalse(NodeFavoritesStore.toggle("math.trig.sine"));
        assertFalse(NodeFavoritesStore.isFavorite("math.trig.sine"));
        assertTrue(NodeFavoritesStore.getFavoriteIds().isEmpty());
    }

    @Test
    void preservesInsertionOrder() {
        NodeFavoritesStore.add("b.node");
        NodeFavoritesStore.add("a.node");
        NodeFavoritesStore.add("c.node");
        assertEquals(List.of("b.node", "a.node", "c.node"), NodeFavoritesStore.getFavoriteIds());
    }

    @Test
    void replaceAllForTestsNormalizesIds() {
        NodeFavoritesStore.replaceAllForTests(java.util.Arrays.asList(" Foo.Bar ", "baz.qux", "", null));
        assertEquals(List.of("foo.bar", "baz.qux"), NodeFavoritesStore.getFavoriteIds());
    }
}
