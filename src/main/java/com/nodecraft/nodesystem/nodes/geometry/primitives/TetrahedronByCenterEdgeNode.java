package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Map;
import java.util.List;

@NodeInfo(
    id = "geometry.primitives.tetrahedron",
    displayName = "Tetrahedron By Center Edge",
    description = "Constructs tetrahedron geometry from a center point, edge length, and optional orientation",
    category = "geometry.primitives",
    order = 11
)
public class TetrahedronByCenterEdgeNode extends AbstractPolyhedronNode<TetrahedronGeometryData> {

    private static final String INPUT_EDGE_ID = "input_edge";

    private static final String OUTPUT_TETRAHEDRON_ID = "output_tetrahedron";
    private static final String OUTPUT_EDGE_ID = "output_edge";
    private static final String OUTPUT_CIRCUMRADIUS_ID = "output_circumradius";

    public TetrahedronByCenterEdgeNode() {
        super(
            "geometry.primitives.tetrahedron",
            INPUT_EDGE_ID,
            "Edge Length",
            "Regular tetrahedron edge length",
            OUTPUT_TETRAHEDRON_ID,
            "Tetrahedron",
            "Constructed tetrahedron geometry",
            NodeDataType.TETRAHEDRON_GEOMETRY,
            OUTPUT_EDGE_ID,
            "Edge Length",
            "Resolved edge length"
        );
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Distance from center to vertices", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Constructs tetrahedron geometry from a center point, edge length, and optional orientation";
    }

    protected TetrahedronGeometryData createGeometry(Vector3d center, double size, Matrix3d orientation) {
        return new TetrahedronGeometryData(center, size, orientation);
    }

    @Override
    protected List<Vector3d> extractVertices(TetrahedronGeometryData geometry) {
        return geometry.getVertices();
    }

    @Override
    protected void writeAdditionalOutputs(TetrahedronGeometryData geometry, double size) {
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, geometry.getCircumradius());
    }

    @Override
    protected void clearAdditionalOutputs() {
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, 0.0d);
    }
}
