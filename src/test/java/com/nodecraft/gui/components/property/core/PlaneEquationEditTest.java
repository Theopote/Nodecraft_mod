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
    void resubmittingSameValuesPreservesConstructionOrigin() {
        PlaneData original = PlaneData.canonical(new Vector3d(10, 64, 20), new Vector3d(0, 1, 0));
        assertNotNull(original);
        float d = (float) PlaneEquationEdit.displayOffset(original);

        PlaneData committed = PlaneEquationEdit.commit(original, 0f, 1f, 0f, d);
        assertNotNull(committed);
        assertEquals(10.0, committed.getPoint().x, EPS);
        assertEquals(64.0, committed.getPoint().y, EPS);
        assertEquals(20.0, committed.getPoint().z, EPS);
        assertEquals(0.0, committed.signedDistanceTo(new Vector3d(10, 64, 20)), 1.0e-4);
    }

    @Test
    void changingNormalKeepsConstructionOrigin() {
        PlaneData original = PlaneData.canonical(new Vector3d(10, 64, 20), new Vector3d(0, 1, 0));
        assertNotNull(original);
        float oldD = (float) PlaneEquationEdit.displayOffset(original);

        PlaneData committed = PlaneEquationEdit.commit(original, 1f, 0f, 0f, oldD);
        assertNotNull(committed);
        assertEquals(10.0, committed.getPoint().x, EPS);
        assertEquals(64.0, committed.getPoint().y, EPS);
        assertEquals(20.0, committed.getPoint().z, EPS);
        assertEquals(1.0, committed.getNormal().x, EPS);
        // D is recomputed from origin · normal
        assertEquals(-10.0, PlaneEquationEdit.displayOffset(committed), 1.0e-4);
    }

    @Test
    void changingOffsetMovesOriginAlongNormal() {
        PlaneData original = PlaneData.canonical(new Vector3d(10, 64, 20), new Vector3d(0, 1, 0));
        assertNotNull(original);

        PlaneData committed = PlaneEquationEdit.commit(original, 0f, 1f, 0f, -70f);
        assertNotNull(committed);
        assertEquals(10.0, committed.getPoint().x, EPS);
        assertEquals(70.0, committed.getPoint().y, EPS);
        assertEquals(20.0, committed.getPoint().z, EPS);
        assertEquals(-70.0, PlaneEquationEdit.displayOffset(committed), 1.0e-4);
    }

    @Test
    void nonUnitNormalLengthDoesNotScaleUnitOffset() {
        // D is unit-normal offset: length of UI normal is ignored for D.
        PlaneData original = PlaneData.canonical(new Vector3d(10, 64, 20), new Vector3d(0, 1, 0));
        assertNotNull(original);
        float d = (float) PlaneEquationEdit.displayOffset(original);

        PlaneData committed = PlaneEquationEdit.commit(original, 0f, 2f, 0f, d);
        assertNotNull(committed);
        assertEquals(10.0, committed.getPoint().x, EPS);
        assertEquals(64.0, committed.getPoint().y, EPS);
        assertEquals(20.0, committed.getPoint().z, EPS);
        assertEquals(d, PlaneEquationEdit.displayOffset(committed), 1.0e-4);
    }

    @Test
    void negativeOffsetWithoutPreviousPlacesOriginOnPlane() {
        PlaneData committed = PlaneEquationEdit.commit(null, 1f, 0f, 0f, 5f);
        assertNotNull(committed);
        assertEquals(5.0, PlaneEquationEdit.displayOffset(committed), EPS);
        assertEquals(0.0, committed.signedDistanceTo(new Vector3d(-5, 0, 0)), 1.0e-4);
    }

    @Test
    void zeroNormalReturnsNull() {
        PlaneData original = PlaneData.canonical(new Vector3d(0, 1, 0), new Vector3d(0, 1, 0));
        assertNull(PlaneEquationEdit.commit(original, 0f, 0f, 0f, 1f));
        assertNull(PlaneEquationEdit.commit(null, 0f, 0f, 0f, 1f));
    }

    @Test
    void formatReadOnlyIncludesOrigin() {
        PlaneData plane = PlaneData.canonical(new Vector3d(0, 1, 0), new Vector3d(0, 1, 0));
        assertNotNull(plane);
        String text = PlaneEquationEdit.formatReadOnly(plane);
        assertTrue(text.contains("N ("));
        assertTrue(text.contains("D "));
        assertTrue(text.contains("O ("));
    }
}
