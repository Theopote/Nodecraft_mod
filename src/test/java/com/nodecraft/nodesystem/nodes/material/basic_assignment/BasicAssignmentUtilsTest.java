package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
