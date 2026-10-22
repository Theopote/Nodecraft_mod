package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.gui.editor.impl.ICustomUINode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualTriggerNodeTest {

    @Test
    void requestPulseFiresOnceThenClears() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        trigger.processNode(null);
        assertEquals(Boolean.FALSE, trigger.getOutput("output_exec"));
        assertFalse(trigger.isPulsePending());

        trigger.requestPulse();
        assertTrue(trigger.isPulsePending());
        trigger.processNode(null);
        assertEquals(Boolean.TRUE, trigger.getOutput("output_exec"));
        assertFalse(trigger.isPulsePending());

        trigger.processNode(null);
        assertEquals(Boolean.FALSE, trigger.getOutput("output_exec"));
    }

    @Test
    void implementsCustomUIWithoutPersistingPulse() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        assertInstanceOf(ICustomUINode.class, trigger);
        trigger.requestPulse();
        assertTrue(trigger.isPulsePending());
        // Transient pulse must not appear in node state serialization surface
        Object state = trigger.getNodeState();
        assertTrue(state == null || !(state instanceof java.util.Map<?, ?> map && map.containsKey("pulseRequested")));
    }
}
