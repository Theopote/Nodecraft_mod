package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import org.joml.Vector3d;

public final class IcosahedronBlockGenerator {

    private IcosahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(IcosahedronGeometryData geometry) {
        Vector3d[] local = PlatonicSolidTables.icosahedronVertices(geometry.getCircumradius());
        Vector3d[] rotated = PolyhedronOrientationUtil.transformLocalVertices(geometry.getOrientationMatrix(), local);
        return ConvexTriangleMeshBlockGenerator.createBoundingRegion(new Vector3d(geometry.getCenter()), rotated);
    }

    public static void populateIcosahedron(BlockPosList blocks,
                                           RegionData region,
                                           IcosahedronGeometryData geometry) {
        if (geometry == null) {
            return;
        }
        Vector3d[] local = PlatonicSolidTables.icosahedronVertices(geometry.getCircumradius());
        Vector3d[] rotated = PolyhedronOrientationUtil.transformLocalVertices(geometry.getOrientationMatrix(), local);
        ConvexTriangleMeshBlockGenerator.populateConvexHull(
            blocks,
            region,
            new Vector3d(geometry.getCenter()),
            rotated,
            PlatonicSolidTables.icosahedronTriangleIndices()
        );
    }
}
