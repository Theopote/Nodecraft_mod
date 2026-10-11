package com.nodecraft.nodesystem.nodes.math.list_sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateListNodeChangeInputCountTest {

    @Test
    void headlessChangeInputCountMutatesPortsWithoutEditor() {
        CreateListNode list = new CreateListNode();
        assertEquals(3, list.getInputCount());

        assertTrue(list.changeInputCountWithHistory(5));
        assertEquals(5, list.getInputCount());
        assertEquals(5, list.getInputPorts().size());

        assertTrue(list.changeInputCountWithHistory(2));
        assertEquals(2, list.getInputCount());
        assertEquals(2, list.getInputPorts().size());

        assertFalse(list.changeInputCountWithHistory(2));
    }

    @Test
    void clampsToAllowedRange() {
        CreateListNode list = new CreateListNode();
        assertTrue(list.changeInputCountWithHistory(100));
        assertEquals(20, list.getInputCount());
        assertTrue(list.changeInputCountWithHistory(0));
        assertEquals(1, list.getInputCount());
    }
}
