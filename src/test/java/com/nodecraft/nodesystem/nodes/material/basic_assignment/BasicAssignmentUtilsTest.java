package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAssignmentUtilsTest {

    @Test
    void pickWeightedIndexNeverSelectsZeroWeightEntries() {
        List<Double> leadingZero = List.of(0.0d, 1.0d);
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(0.0d, leadingZero));
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(0.5d, leadingZero));
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(1.0d, leadingZero));

        List<Double> trailingZeros = List.of(1.0d, 0.0d, 0.0d);
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(0.0d, trailingZeros));
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(0.5d, trailingZeros));
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(1.0d, trailingZeros));
    }

    @Test
    void pickWeightedIndexDistributesPositiveMass() {
        List<Double> balanced = List.of(1.0d, 1.0d);
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(0.0d, balanced));
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(0.5d, balanced));
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(1.0d, balanced));
    }

    @Test
    void validateWeightsRejectsNonFiniteSum() {
        assertFalse(BasicAssignmentUtils.validateWeights(
            List.of(Double.MAX_VALUE, Double.MAX_VALUE), 2).valid());
        assertTrue(BasicAssignmentUtils.validateWeights(List.of(1.0d, 2.0d), 2).valid());
        assertFalse(Double.isFinite(BasicAssignmentUtils.totalWeight(
            List.of(Double.MAX_VALUE, Double.MAX_VALUE))));
    }

    @Test
    void parseDoubleListRequiresExactDouble() {
        assertFalse(BasicAssignmentUtils.parseDoubleList(List.of(1, 2), "W").valid());
        assertFalse(BasicAssignmentUtils.parseDoubleList(List.of(1.0f), "W").valid());
        assertTrue(BasicAssignmentUtils.parseDoubleList(List.of(1.0d, 0.0d), "W").valid());
    }
}
