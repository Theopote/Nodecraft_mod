package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

@NodeInfo(
    id = "geometry.primitives.dodecahedron",
    displayName = "Dodecahedron By Center Edge",
    description = "Constructs a regular dodecahedron from a center point, edge length, and optional orientation",
    category = "geometry.primitives",
    order = 24
)
public class DodecahedronByCenterEdgeNode extends AbstractPolyhedronNode<DodecahedronGeometryData> {

    private static final String INPUT_EDGE_LENGTH_ID = "input_edge_length";

    private static final String OUTPUT_DODECAHEDRON_ID = "output_dodecahedron";
    private static final String OUTPUT_EDGE_LENGTH_ID = "output_edge_length";

    public DodecahedronByCenterEdgeNode() {
        super(
            "geometry.primitives.dodecahedron",
            INPUT_EDGE_LENGTH_ID,
            "Edge Length",
            "Length of each edge",
            OUTPUT_DODECAHEDRON_ID,
            "Dodecahedron",
            "Constructed dodecahedron geometry",
            NodeDataType.DODECAHEDRON_GEOMETRY,
            OUTPUT_EDGE_LENGTH_ID,
            "Edge Length",
            "Resolved edge length"
        );
    }

    @Override
    public String getDescription() {
        return "Constructs a regular dodecahedron from a center point, edge length, and optional orientation";
    }

    protected DodecahedronGeometryData createGeometry(Vector3d center, double size, Matrix3d orientation) {
        return new DodecahedronGeometryData(center, size, orientation);
    }

    @Override
    protected List<Vector3d> extractVertices(DodecahedronGeometryData geometry) {
        return geometry.getVertices();
    }
}
