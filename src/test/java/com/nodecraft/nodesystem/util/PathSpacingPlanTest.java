package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PathSpacingPlanTest {

    @Test
    void closedPathAtGeometryBudgetIsInclusiveOfZeroExclusiveOfSeam() {
        int max = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        List<Double> distances = PathSpacingPlan.distances(max, true, true, 1.0d, max);
        assertNotNull(distances);
        assertEquals(max, distances.size());
        assertEquals(0.0d, distances.getFirst());
        assertEquals(max - 1.0d, distances.getLast());
        assertEquals(max, PathSpacingPlan.estimateCount(max, true, true, 1.0d));
    }

    @Test
    void openPathSameSpanExceedsBudget() {
        int max = GenerationLimits.MAX_GEOMETRY_INSTANCES;
        assertNull(PathSpacingPlan.distances(max, false, true, 1.0d, max));
        assertEquals(max + 1L, PathSpacingPlan.estimateCount(max, false, true, 1.0d));
    }
}
