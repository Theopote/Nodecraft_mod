package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

@NodeInfo(
    id = "geometry.primitives.octahedron",
    displayName = "Octahedron By Center Size",
    description = "Constructs octahedron geometry from a center point, vertex radius, and optional orientation",
    category = "geometry.primitives",
    order = 10
)
public class OctahedronByCenterSizeNode extends AbstractPolyhedronNode<OctahedronGeometryData> {

    private static final String INPUT_SIZE_ID = "input_size";

    private static final String OUTPUT_OCTAHEDRON_ID = "output_octahedron";
    private static final String OUTPUT_SIZE_ID = "output_size";

    public OctahedronByCenterSizeNode() {
        super(
            "geometry.primitives.octahedron",
            INPUT_SIZE_ID,
            "Size",
            "Distance from center to vertices",
            OUTPUT_OCTAHEDRON_ID,
            "Octahedron",
            "Constructed octahedron geometry",
            NodeDataType.OCTAHEDRON_GEOMETRY,
            OUTPUT_SIZE_ID,
            "Size",
            "Resolved vertex radius"
        );
    }

    @Override
    public String getDescription() {
        return "Constructs octahedron geometry from a center point, vertex radius, and optional orientation";
    }

    protected OctahedronGeometryData createGeometry(Vector3d center, double size, Matrix3d orientation) {
        return new OctahedronGeometryData(center, size, orientation);
    }

    @Override
    protected List<Vector3d> extractVertices(OctahedronGeometryData geometry) {
        return geometry.getVertices();
    }
}
