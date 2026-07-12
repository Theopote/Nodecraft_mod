package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomNumberNodeTest {

    @Test
    void clampsHugeCountToGenerationLimit() {
        RandomNumberNode node = new RandomNumberNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_count", Integer.MAX_VALUE,
            "input_min", 0.0d,
            "input_max", 1.0d
        ));

        Object value = outputs.get("output_random");
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, ((List<?>) value).size());
    }
}
