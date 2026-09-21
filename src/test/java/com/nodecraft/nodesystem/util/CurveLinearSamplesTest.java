package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurveLinearSamplesTest {

    @Test
    void linearSamplesDoNotDuplicateInternalControlPoints() {
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        curve.addControlPoint(new Vec3d(0, 0, 0));
        curve.addControlPoint(new Vec3d(1, 0, 0));
        curve.addControlPoint(new Vec3d(2, 0, 0));

        List<Vec3d> samples = curve.getSamplePoints();
        assertEquals(3, samples.size());
        assertEquals(0.0d, samples.get(0).x, 1e-9);
        assertEquals(1.0d, samples.get(1).x, 1e-9);
        assertEquals(2.0d, samples.get(2).x, 1e-9);
    }

    @Test
    void linearSamplesPreserveEndpointsForTwoPointPath() {
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        curve.addControlPoint(new Vec3d(0, 0, 0));
        curve.addControlPoint(new Vec3d(4, 0, 0));

        List<Vec3d> samples = curve.getSamplePoints();
        assertEquals(2, samples.size());
        assertEquals(0.0d, samples.get(0).x, 1e-9);
        assertEquals(4.0d, samples.get(1).x, 1e-9);
    }
}
