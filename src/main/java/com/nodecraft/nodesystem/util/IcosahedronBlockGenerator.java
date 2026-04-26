package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import org.joml.Vector3d;
import com.nodecraft.nodesystem.datatypes.RegionData;

public final class IcosahedronBlockGenerator {

    private IcosahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(IcosahedronGeometryData geometry) {
        Vector3d[] local = PlatonicSolidTables.icosahedronVertices(geometry.getCircumradius());
        return ConvexTriangleMeshBlockGenerator.createBoundingRegion(new Vector3d(geometry.getCenter()), local);
    }

    public static void populateIcosahedron(BlockPosList blocks,
                                           RegionData region,
                                           IcosahedronGeometryData geometry) {
        if (geometry == null) {
            return;
        }
        Vector3d[] local = PlatonicSolidTables.icosahedronVertices(geometry.getCircumradius());
        ConvexTriangleMeshBlockGenerator.populateConvexHull(
            blocks,
            region,
            new Vector3d(geometry.getCenter()),
            local,
            PlatonicSolidTables.icosahedronTriangleIndices()
        );
    }
}
