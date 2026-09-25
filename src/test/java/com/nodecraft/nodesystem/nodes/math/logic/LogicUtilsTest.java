package com.nodecraft.nodesystem.nodes.math.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicUtilsTest {

    @Test
    void booleanValueAcceptsOnlyBoolean() {
        assertTrue(LogicUtils.booleanValue(Boolean.TRUE));
        assertFalse(LogicUtils.booleanValue(Boolean.FALSE));
        assertFalse(LogicUtils.booleanValue(null));
        assertFalse(LogicUtils.booleanValue(1));
        assertFalse(LogicUtils.booleanValue(0));
        assertFalse(LogicUtils.booleanValue("true"));
        assertFalse(LogicUtils.booleanValue("false"));
        assertFalse(LogicUtils.booleanValue(new Object()));
    }

    @Test
    void switchIndexAcceptsOnlyInteger() {
        assertEquals(0, LogicUtils.switchIndex(0));
        assertEquals(2, LogicUtils.switchIndex(2));
        assertEquals(-1, LogicUtils.switchIndex(null));
        assertEquals(-1, LogicUtils.switchIndex(1.9d));
        assertEquals(-1, LogicUtils.switchIndex("1"));
        assertEquals(-1, LogicUtils.switchIndex(Boolean.TRUE));
        assertEquals(-1, LogicUtils.switchIndex(1L));
    }
}
