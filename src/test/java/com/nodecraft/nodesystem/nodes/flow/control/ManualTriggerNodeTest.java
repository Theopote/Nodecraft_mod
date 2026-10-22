package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.gui.components.customui.CustomUIEditorContext;
import com.nodecraft.gui.editor.impl.ICustomUINode;
import com.nodecraft.nodesystem.graph.NodeGraph;
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
        Object state = trigger.getNodeState();
        assertTrue(state == null || !(state instanceof java.util.Map<?, ?> map && map.containsKey("pulseRequested")));
    }

    @Test
    void failedSubmitClearsPendingAndReportsFailed() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        CustomUIEditorContext.unbind();
        trigger.fireTriggerFromUi();
        assertFalse(trigger.isPulsePending(), "failed submit must not leave a stuck pending pulse");
        assertEquals(ManualTriggerNode.SubmitFeedback.FAILED, trigger.getLastFeedback());
        assertFalse(trigger.getLastFailureReason().isBlank());
        trigger.processNode(null);
        assertEquals(Boolean.FALSE, trigger.getOutput("output_exec"));
    }

    @Test
    void emptyGraphSubmitFailsWithoutConsumingAsExecTrue() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        CustomUIEditorContext.bind(new NodeGraph());
        try {
            trigger.fireTriggerFromUi();
            assertEquals(ManualTriggerNode.SubmitFeedback.FAILED, trigger.getLastFeedback());
            assertFalse(trigger.isPulsePending());
        } finally {
            CustomUIEditorContext.unbind();
        }
    }

    @Test
    void repeatedRequestPulseStillOneShotPerProcess() {
        ManualTriggerNode trigger = new ManualTriggerNode();
        trigger.requestPulse();
        trigger.requestPulse();
        trigger.requestPulse();
        assertTrue(trigger.isPulsePending());
        trigger.processNode(null);
        assertEquals(Boolean.TRUE, trigger.getOutput("output_exec"));
        assertFalse(trigger.isPulsePending());
        trigger.processNode(null);
        assertEquals(Boolean.FALSE, trigger.getOutput("output_exec"));
    }
}
