package com.nodecraft.nodesystem.nodes.flow.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
