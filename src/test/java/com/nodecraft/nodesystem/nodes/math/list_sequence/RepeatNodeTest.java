package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatNodeTest {

    @Test
    void clampsHugeRepeatCountForScalarData() {
        RepeatNode node = new RepeatNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_data", "x",
            "input_count", Integer.MAX_VALUE
        ));

        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, outputs.get("output_length"));
    }

    @Test
    void clampsHugeRepeatCountForListData() {
        RepeatNode node = new RepeatNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_data", List.of(1, 2, 3, 4),
            "input_count", 1_000_000
        ));

        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, outputs.get("output_length"));
    }
}
