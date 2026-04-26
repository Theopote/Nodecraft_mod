package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import org.joml.Vector3d;

public final class DodecahedronBlockGenerator {

    private DodecahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(DodecahedronGeometryData geometry) {
        Vector3d[] local = PlatonicSolidTables.dodecahedronVertices(geometry.getCircumradius());
        Vector3d[] rotated = PolyhedronOrientationUtil.transformLocalVertices(geometry.getOrientationMatrix(), local);
        return ConvexTriangleMeshBlockGenerator.createBoundingRegion(new Vector3d(geometry.getCenter()), rotated);
    }

    public static void populateDodecahedron(BlockPosList blocks,
                                            RegionData region,
                                            DodecahedronGeometryData geometry) {
        if (geometry == null) {
            return;
        }
        Vector3d[] local = PlatonicSolidTables.dodecahedronVertices(geometry.getCircumradius());
        Vector3d[] rotated = PolyhedronOrientationUtil.transformLocalVertices(geometry.getOrientationMatrix(), local);
        ConvexTriangleMeshBlockGenerator.populateConvexHull(
            blocks,
            region,
            new Vector3d(geometry.getCenter()),
            rotated,
            PlatonicSolidTables.dodecahedronTriangleIndices()
        );
    }
}
