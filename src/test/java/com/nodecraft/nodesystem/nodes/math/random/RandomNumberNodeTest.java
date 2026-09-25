package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomNumberNodeTest {

    @Test
    void outputsSingleDouble() {
        RandomNumberNode node = new RandomNumberNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_domain", new NumericRangeData(0.0d, 1.0d),
            "input_seed", 0
        ));

        assertInstanceOf(Double.class, outputs.get("output_random"));
        assertTrue(Double.isFinite((Double) outputs.get("output_random")));
    }

    @Test
    void sameSeedProducesSameOutput() {
        RandomNumberNode node = new RandomNumberNode();
        NumericRangeData domain = new NumericRangeData(0.0d, 1.0d);
        Object a = node.compute(Map.of("input_domain", domain, "input_seed", 42)).get("output_random");
        Object b = node.compute(Map.of("input_domain", domain, "input_seed", 42)).get("output_random");
        assertEquals(a, b);
    }
}

class RandomNumbersNodeTest {

    @Test
    void clampsHugeCountToGenerationLimit() {
        RandomNumbersNode node = new RandomNumbersNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_count", Integer.MAX_VALUE,
            "input_domain", new NumericRangeData(0.0d, 1.0d),
            "input_seed", 0
        ));

        Object value = outputs.get("output_values");
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, ((List<?>) value).size());
    }

    @Test
    void sameSeedProducesSameList() {
        RandomNumbersNode node = new RandomNumbersNode();
        Map<String, Object> inputs = Map.of(
            "input_count", 5,
            "input_domain", new NumericRangeData(0.0d, 1.0d),
            "input_seed", 7
        );
        assertEquals(node.compute(inputs).get("output_values"), node.compute(inputs).get("output_values"));
    }
}
