package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneEquationEditTest {

    private static final double EPS = 1.0e-6;

    @Test
    void displayOffsetMatchesPlaneW() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 64, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);
        assertEquals(plane.getPlane().w, PlaneEquationEdit.displayOffset(plane), EPS);
        assertEquals(-64.0, PlaneEquationEdit.displayOffset(plane), EPS);
    }

    @Test
    void unitNormalAndOffsetRoundTripPreservesPlane() {
        PlaneData original = PlaneData.canonical(new Vector3d(0, 64, 0), new Vector3d(0, 1, 0));
        assertNotNull(original);
        float d = (float) PlaneEquationEdit.displayOffset(original);
        PlaneData committed = PlaneEquationEdit.commit(0f, 1f, 0f, d);
        assertNotNull(committed);
        assertEquals(0.0, committed.getNormal().x, EPS);
        assertEquals(1.0, committed.getNormal().y, EPS);
        assertEquals(0.0, committed.getNormal().z, EPS);
        assertEquals(d, PlaneEquationEdit.displayOffset(committed), 1.0e-4);
        assertEquals(0.0, committed.signedDistanceTo(new Vector3d(0, 64, 0)), 1.0e-4);
    }

    @Test
    void nonUnitNormalScalesOffsetSoEquationIsPreserved() {
        // Equation 0*x + 2*y + 0*z - 128 = 0  <=>  y = 64 with unit normal
        PlaneData committed = PlaneEquationEdit.commit(0f, 2f, 0f, -128f);
        assertNotNull(committed);
        assertEquals(0.0, committed.getNormal().x, EPS);
        assertEquals(1.0, committed.getNormal().y, EPS);
        assertEquals(0.0, committed.getNormal().z, EPS);
        assertEquals(-64.0, PlaneEquationEdit.displayOffset(committed), 1.0e-4);
        assertEquals(0.0, committed.signedDistanceTo(new Vector3d(10, 64, -3)), 1.0e-4);
    }

    @Test
    void negativeOffsetIsPreservedForUnitNormal() {
        PlaneData committed = PlaneEquationEdit.commit(1f, 0f, 0f, 5f);
        assertNotNull(committed);
        assertEquals(5.0, PlaneEquationEdit.displayOffset(committed), EPS);
        assertEquals(0.0, committed.signedDistanceTo(new Vector3d(-5, 0, 0)), 1.0e-4);
    }

    @Test
    void zeroNormalReturnsNull() {
        assertNull(PlaneEquationEdit.commit(0f, 0f, 0f, 1f));
    }

    @Test
    void formatReadOnlyIncludesNormalAndOffset() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 1, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);
        String text = PlaneEquationEdit.formatReadOnly(plane);
        assertTrue(text.contains("N ("));
        assertTrue(text.contains("D "));
    }
}
