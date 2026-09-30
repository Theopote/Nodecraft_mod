package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListIndexResolverTest {

    @Test
    void requiredIndexRejectsDouble() {
        assertFalse(ListIndexResolver.resolveRequiredIndex(1.9d, true).valid());
    }

    @Test
    void requiredIndexAcceptsInteger() {
        assertTrue(ListIndexResolver.resolveRequiredIndex(2, true).valid());
        assertEquals(2, ListIndexResolver.resolveRequiredIndex(2, true).index());
    }

    @Test
    void requiredIndexUndrivenIsInvalid() {
        assertFalse(ListIndexResolver.resolveRequiredIndex(null, false).valid());
    }

    @Test
    void optionalIndexUsesDefaultWhenUndriven() {
        assertEquals(0, ListIndexResolver.resolveOptionalIndex(null, 0, false).index());
        assertEquals(5, ListIndexResolver.resolveOptionalIndex(null, 5, false).index());
    }

    @Test
    void normalizeNegativeFromEnd() {
        assertEquals(2, ListIndexResolver.normalizeNegativeFromEnd(-1, 3));
        assertEquals(1, ListIndexResolver.normalizeNegativeFromEnd(1, 3));
    }

    @Test
    void applyWrap() {
        assertEquals(0, ListIndexResolver.applyWrap(3, 3));
        assertEquals(2, ListIndexResolver.applyWrap(-1, 3));
    }
}
