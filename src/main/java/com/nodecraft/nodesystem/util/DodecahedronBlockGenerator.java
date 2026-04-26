package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import org.joml.Vector3d;
import com.nodecraft.nodesystem.datatypes.RegionData;

public final class DodecahedronBlockGenerator {

    private DodecahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(DodecahedronGeometryData geometry) {
        Vector3d[] local = PlatonicSolidTables.dodecahedronVertices(geometry.getCircumradius());
        return ConvexTriangleMeshBlockGenerator.createBoundingRegion(new Vector3d(geometry.getCenter()), local);
    }

    public static void populateDodecahedron(BlockPosList blocks,
                                            RegionData region,
                                            DodecahedronGeometryData geometry) {
        if (geometry == null) {
            return;
        }
        Vector3d[] local = PlatonicSolidTables.dodecahedronVertices(geometry.getCircumradius());
        ConvexTriangleMeshBlockGenerator.populateConvexHull(
            blocks,
            region,
            new Vector3d(geometry.getCenter()),
            local,
            PlatonicSolidTables.dodecahedronTriangleIndices()
        );
    }
}
