package com.nodecraft.nodesystem.nodes.math.scalar_math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphMapperCurveSamplerTest {

    @Test
    void linearCurveSamplesEndpoints() {
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.LINEAR);
        assertEquals(0.0d, node.sampleNormalizedCurve(0.0d), 1.0e-9);
        assertEquals(1.0d, node.sampleNormalizedCurve(1.0d), 1.0e-9);
        assertEquals(0.5d, node.sampleNormalizedCurve(0.5d), 1.0e-9);
    }

    @Test
    void smoothstepIsMonotonicAtMidpoint() {
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.SMOOTHSTEP);
        double mid = node.sampleNormalizedCurve(0.5d);
        assertTrue(mid > 0.4d && mid < 0.6d);
    }

    @Test
    void bezierSamplesWithinReasonableRange() {
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.BEZIER);
        node.setBezierX1(0.25d);
        node.setBezierY1(0.0d);
        node.setBezierX2(0.75d);
        node.setBezierY2(1.0d);
        double start = node.sampleNormalizedCurve(0.0d);
        double end = node.sampleNormalizedCurve(1.0d);
        assertEquals(0.0d, start, 0.05d);
        assertEquals(1.0d, end, 0.05d);
    }

    @Test
    void resetRestoresSmoothstepDefaults() {
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.BEZIER);
        node.setDefaultExponent(5.0d);
        node.resetCurveDefaults();
        assertEquals(GraphMapperNode.CurveType.SMOOTHSTEP, node.getCurveType());
        assertEquals(2.0d, node.getDefaultExponent(), 1.0e-9);
    }
}
