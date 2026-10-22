package com.nodecraft.gui.editor.impl;

import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.utilities.assist.SignalForkNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural side-effects for Create List / Signal Fork that affect custom UI layout.
 * Full undo/dangling-edge coverage lives in {@code GraphStructureEditSupportTest}.
 */
class CustomUIStructuralOpsTest {

    @BeforeAll
    static void enableHeadlessMetrics() {
        System.setProperty("nodecraft.customui.headlessMetrics", "true");
    }

    @Test
    void createListPortCountChangeUpdatesVisiblePortsImmediately() {
        CreateListNode list = new CreateListNode();
        assertEquals(3, list.getInputPorts().size());

        assertTrue(list.changeInputCountWithHistory(5));
        assertEquals(5, list.getInputPorts().size());
        assertEquals(5, list.getInputCount());

        assertTrue(list.changeInputCountWithHistory(2));
        assertEquals(2, list.getInputPorts().size());
        // Custom UI region itself stays present after structural change.
        assertTrue(list.hasCustomUI());
        assertTrue(Float.isFinite(list.getCustomUIHeight()) && list.getCustomUIHeight() >= 0f);
    }

    @Test
    void signalForkBranchChangeUpdatesOutputPortsImmediately() {
        SignalForkNode fork = new SignalForkNode();
        assertEquals(2, fork.getOutputBranchCount());
        assertEquals(2, fork.getOutputPorts().size());

        assertTrue(fork.addOutputBranch());
        assertEquals(3, fork.getOutputBranchCount());
        assertEquals(3, fork.getOutputPorts().size());
        assertTrue(fork.getOutputPorts().stream().anyMatch(p -> "output_3".equals(p.getId())));

        String removed = fork.removeLastOutputBranch();
        assertEquals("output_3", removed);
        assertEquals(2, fork.getOutputBranchCount());
        assertEquals(2, fork.getOutputPorts().size());
        assertTrue(fork.hasCustomUI());
        assertTrue(Float.isFinite(fork.getMinRequiredUIWidth()) && fork.getMinRequiredUIWidth() >= 0f);
    }
}
