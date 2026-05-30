package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

@NodeInfo(
    id = "geometry.primitives.icosahedron",
    displayName = "Icosahedron By Center Edge",
    description = "Constructs a regular icosahedron from a center point, edge length, and optional orientation",
    category = "geometry.primitives",
    order = 23
)
public class IcosahedronByCenterEdgeNode extends AbstractPolyhedronNode<IcosahedronGeometryData> {

    private static final String INPUT_EDGE_LENGTH_ID = "input_edge_length";

    private static final String OUTPUT_ICOSAHEDRON_ID = "output_icosahedron";
    private static final String OUTPUT_EDGE_LENGTH_ID = "output_edge_length";

    public IcosahedronByCenterEdgeNode() {
        super(
            "geometry.primitives.icosahedron",
            INPUT_EDGE_LENGTH_ID,
            "Edge Length",
            "Length of each edge",
            OUTPUT_ICOSAHEDRON_ID,
            "Icosahedron",
            "Constructed icosahedron geometry",
            NodeDataType.ICOSAHEDRON_GEOMETRY,
            OUTPUT_EDGE_LENGTH_ID,
            "Edge Length",
            "Resolved edge length"
        );
    }

    @Override
    public String getDescription() {
        return "Constructs a regular icosahedron from a center point, edge length, and optional orientation";
    }

    protected IcosahedronGeometryData createGeometry(Vector3d center, double size, Matrix3d orientation) {
        return new IcosahedronGeometryData(center, size, orientation);
    }

    @Override
    protected List<Vector3d> extractVertices(IcosahedronGeometryData geometry) {
        return geometry.getVertices();
    }
}
