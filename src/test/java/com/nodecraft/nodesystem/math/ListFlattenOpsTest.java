package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFlattenOpsTest {

    @Test
    void flattensNestedLists() {
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                List.of(List.of("A", "B"), List.of("C")),
                GenerationLimits.MAX_FORMAT_DEPTH,
                ListFlattenOps.FlattenDepthMode.FULL_FLATTEN);
        assertTrue(result.valid());
        assertEquals(List.of("A", "B", "C"), result.items());
    }

    @Test
    void depthZeroPreservesNestedLists() {
        List<Object> nested = List.of("A", "B");
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                List.of(nested, "C"),
                0,
                ListFlattenOps.FlattenDepthMode.PARTIAL_DEPTH);
        assertTrue(result.valid());
        assertEquals(List.of(nested, "C"), result.items());
    }

    @Test
    void depthOneFlattensOneLevel() {
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                List.of(List.of("A", "B"), List.of("C")),
                1,
                ListFlattenOps.FlattenDepthMode.PARTIAL_DEPTH);
        assertTrue(result.valid());
        assertEquals(List.of("A", "B", "C"), result.items());
    }

    @Test
    void cycleReferenceFailsClosed() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add("A");
        cyclic.add(cyclic);
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                cyclic,
                GenerationLimits.MAX_FORMAT_DEPTH,
                ListFlattenOps.FlattenDepthMode.FULL_FLATTEN);
        assertFalse(result.valid());
        assertEquals(ListFlattenOps.ERROR_CYCLE_REFERENCE, result.error());
    }

    @Test
    void deepNestBeyondCapFails() {
        List<Object> nested = List.of("leaf");
        for (int i = 0; i < GenerationLimits.MAX_FORMAT_DEPTH + 2; i++) {
            nested = new ArrayList<>(List.of(nested));
        }
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                nested,
                GenerationLimits.MAX_FORMAT_DEPTH,
                ListFlattenOps.FlattenDepthMode.FULL_FLATTEN);
        assertFalse(result.valid());
        assertEquals(ListFlattenOps.ERROR_DEPTH_EXCEEDED, result.error());
    }

    @Test
    void sharedListReferenceWithoutCycleSucceeds() {
        List<Object> shared = new ArrayList<>(List.of("A"));
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                List.of(shared, shared),
                GenerationLimits.MAX_FORMAT_DEPTH,
                ListFlattenOps.FlattenDepthMode.FULL_FLATTEN);
        assertTrue(result.valid());
        assertEquals(List.of("A", "A"), result.items());
    }

    @Test
    void elementBudgetFails() {
        ListFlattenOps.FlattenResult result = ListFlattenOps.flatten(
                List.of("A", "B", "C"),
                GenerationLimits.MAX_FORMAT_DEPTH,
                ListFlattenOps.FlattenDepthMode.PARTIAL_DEPTH,
                2);
        assertFalse(result.valid());
        assertEquals(ListFlattenOps.ERROR_ELEMENT_LIMIT, result.error());
    }
}
