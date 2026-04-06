package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "spatial.construct.tetrahedron_by_center_edge",
    displayName = "Tetrahedron By Center Edge",
    description = "Constructs tetrahedron geometry from a center point and edge length",
    category = "spatial.construct"
)
public class TetrahedronByCenterEdgeNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_EDGE_ID = "input_edge";

    private static final String OUTPUT_TETRAHEDRON_ID = "output_tetrahedron";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_ID = "output_edge";
    private static final String OUTPUT_CIRCUMRADIUS_ID = "output_circumradius";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TetrahedronByCenterEdgeNode() {
        super(UUID.randomUUID(), "spatial.construct.tetrahedron_by_center_edge");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Tetrahedron center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_EDGE_ID, "Edge Length", "Regular tetrahedron edge length", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_TETRAHEDRON_ID, "Tetrahedron", "Constructed tetrahedron geometry", NodeDataType.TETRAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_ID, "Edge Length", "Resolved edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CIRCUMRADIUS_ID, "Circumradius", "Distance from center to vertices", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved tetrahedron vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a tetrahedron could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs tetrahedron geometry from a center point and edge length";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object edgeObj = inputValues.get(INPUT_EDGE_ID);
        if (center == null || !(edgeObj instanceof Number edgeNumber)) {
            writeEmptyOutputs();
            return;
        }

        double edgeLength = edgeNumber.doubleValue();
        if (!Double.isFinite(edgeLength) || edgeLength < 0.0d) {
            writeEmptyOutputs();
            return;
        }

        TetrahedronGeometryData tetrahedron = new TetrahedronGeometryData(center, edgeLength);
        outputValues.put(OUTPUT_TETRAHEDRON_ID, tetrahedron);
        outputValues.put(OUTPUT_GEOMETRY_ID, tetrahedron);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_EDGE_ID, edgeLength);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, tetrahedron.getCircumradius());
        outputValues.put(OUTPUT_VERTICES_ID, tetrahedron.getVertices());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_TETRAHEDRON_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_ID, 0.0d);
        outputValues.put(OUTPUT_CIRCUMRADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, java.util.List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) return pointData.getPosition();
        if (value instanceof Vector3d vector) return new Vector3d(vector);
        if (value instanceof BlockPos blockPos) return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        return null;
    }
}
