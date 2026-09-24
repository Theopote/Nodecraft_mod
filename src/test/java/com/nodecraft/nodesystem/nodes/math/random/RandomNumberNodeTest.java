package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RandomNumberNodeTest {

    @Test
    void outputsSingleDouble() {
        RandomNumberNode node = new RandomNumberNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_domain", new NumericRangeData(0.0d, 1.0d)
        ));

        assertInstanceOf(Double.class, outputs.get("output_random"));
    }
}

class RandomNumbersNodeTest {

    @Test
    void clampsHugeCountToGenerationLimit() {
        RandomNumbersNode node = new RandomNumbersNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_count", Integer.MAX_VALUE,
            "input_domain", new NumericRangeData(0.0d, 1.0d)
        ));

        Object value = outputs.get("output_values");
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, ((List<?>) value).size());
    }
}
