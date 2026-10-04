package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatNodeTest {

    @Test
    void drivenHugeRepeatCountFailsClosedForScalarData() {
        RepeatNode node = new RepeatNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_data", "x",
            "input_count", Integer.MAX_VALUE
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_length"));
        assertTrue(((List<?>) outputs.get("output_result")).isEmpty());
    }

    @Test
    void drivenHugeRepeatCountFailsClosedForListData() {
        RepeatNode node = new RepeatNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_data", List.of(1, 2, 3, 4),
            "input_count", Integer.MAX_VALUE
        ));

        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_length"));
    }

    @Test
    void undrivenPropertyCountStillClamps() {
        RepeatNode node = new RepeatNode();
        node.setDefaultCount(Integer.MAX_VALUE);
        node.setInput("input_data", "x");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, node.getOutput("output_length"));
    }
}
