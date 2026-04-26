package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

/**
 * Canonical vertex positions and triangulations for the regular icosahedron and dodecahedron,
 * aligned with three.js {@code IcosahedronGeometry} / {@code DodecahedronGeometry} (detail 0, then projected to a sphere).
 */
public final class PlatonicSolidTables {

    public static final double PHI = (1.0d + Math.sqrt(5.0d)) * 0.5d;

    /** Raw icosahedron coordinates before spherical normalization (three.js order). */
    private static final double[] ICOSA_RAW = {
        -1, PHI, 0, 1, PHI, 0, -1, -PHI, 0, 1, -PHI, 0,
        0, -1, PHI, 0, 1, PHI, 0, -1, -PHI, 0, 1, -PHI,
        PHI, 0, -1, PHI, 0, 1, -PHI, 0, -1, -PHI, 0, 1
    };

    /** Triangle list (vertex indices), three.js icosahedron. */
    private static final int[] ICOSA_TRIANGLES = {
        0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
        1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
        3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
        4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1
    };

    private static final double INV_PHI = 1.0d / PHI;

    /** Raw dodecahedron coordinates (three.js order), 20 vertices. */
    private static final double[] DODECA_RAW = {
        -1, -1, -1, -1, -1, 1, -1, 1, -1, -1, 1, 1,
        1, -1, -1, 1, -1, 1, 1, 1, -1, 1, 1, 1,
        0, -INV_PHI, -PHI, 0, -INV_PHI, PHI,
        0, INV_PHI, -PHI, 0, INV_PHI, PHI,
        -INV_PHI, -PHI, 0, -INV_PHI, PHI, 0,
        INV_PHI, -PHI, 0, INV_PHI, PHI, 0,
        -PHI, 0, -INV_PHI, PHI, 0, -INV_PHI,
        -PHI, 0, INV_PHI, PHI, 0, INV_PHI
    };

    /** Triangulated faces (three.js dodecahedron), 27 triangles. */
    private static final int[] DODECA_TRIANGLES = {
        3, 11, 7, 3, 7, 15, 3, 15, 13,
        7, 19, 17, 7, 17, 6, 7, 6, 15,
        17, 4, 8, 17, 8, 10, 17, 10, 6,
        8, 0, 16, 8, 16, 2, 8, 2, 10,
        0, 12, 1, 0, 1, 18, 0, 18, 16,
        6, 10, 2, 6, 2, 13, 6, 13, 15,
        2, 16, 18, 2, 18, 3, 2, 3, 13,
        18, 1, 9, 18, 9, 11, 18, 11, 3,
        4, 14, 12, 4, 12, 0, 4, 0, 8,
        11, 9, 5, 11, 5, 19, 11, 19, 7,
        19, 5, 14, 19, 14, 4, 19, 4, 17,
        1, 12, 14, 1, 14, 5, 1, 5, 9
    };

    private static final double ICOSA_UNIT_EDGE = computeIcosaUnitEdgeInternal();
    private static final double DODECA_UNIT_EDGE = computeDodecaUnitEdgeInternal();

    private PlatonicSolidTables() {
    }

    public static double icosahedronUnitSphereEdgeLength() {
        return ICOSA_UNIT_EDGE;
    }

    public static double dodecahedronUnitSphereEdgeLength() {
        return DODECA_UNIT_EDGE;
    }

    /**
     * Vertices on a circumscribed sphere of radius {@code circumradius}, centered at the origin.
     */
    public static Vector3d[] icosahedronVertices(double circumradius) {
        return scaleVertices(unitIcosahedronVertices(), circumradius);
    }

    public static Vector3d[] dodecahedronVertices(double circumradius) {
        return scaleVertices(unitDodecahedronVertices(), circumradius);
    }

    public static int[] icosahedronTriangleIndices() {
        return ICOSA_TRIANGLES;
    }

    public static int[] dodecahedronTriangleIndices() {
        return DODECA_TRIANGLES;
    }

    /** Circumscribed-sphere radius for a regular icosahedron with the given edge length. */
    public static double icosahedronCircumradiusFromEdge(double edgeLength) {
        return edgeLength / ICOSA_UNIT_EDGE;
    }

    public static double dodecahedronCircumradiusFromEdge(double edgeLength) {
        return edgeLength / DODECA_UNIT_EDGE;
    }

    private static Vector3d[] unitIcosahedronVertices() {
        Vector3d[] out = new Vector3d[12];
        for (int i = 0; i < 12; i++) {
            double x = ICOSA_RAW[i * 3];
            double y = ICOSA_RAW[i * 3 + 1];
            double z = ICOSA_RAW[i * 3 + 2];
            out[i] = new Vector3d(x, y, z).normalize();
        }
        return out;
    }

    private static Vector3d[] unitDodecahedronVertices() {
        Vector3d[] out = new Vector3d[20];
        for (int i = 0; i < 20; i++) {
            double x = DODECA_RAW[i * 3];
            double y = DODECA_RAW[i * 3 + 1];
            double z = DODECA_RAW[i * 3 + 2];
            out[i] = new Vector3d(x, y, z).normalize();
        }
        return out;
    }

    private static Vector3d[] scaleVertices(Vector3d[] unit, double circumradius) {
        Vector3d[] out = new Vector3d[unit.length];
        for (int i = 0; i < unit.length; i++) {
            out[i] = new Vector3d(unit[i]).mul(circumradius);
        }
        return out;
    }

    private static double computeIcosaUnitEdgeInternal() {
        Vector3d[] v = unitIcosahedronVertices();
        return v[ICOSA_TRIANGLES[0]].distance(v[ICOSA_TRIANGLES[1]]);
    }

    private static double computeDodecaUnitEdgeInternal() {
        Vector3d[] v = unitDodecahedronVertices();
        return v[DODECA_TRIANGLES[0]].distance(v[DODECA_TRIANGLES[1]]);
    }
}
