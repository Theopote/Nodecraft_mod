package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PolyhedronOrientationUtil;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.icosahedron",
    displayName = "Icosahedron By Center Edge",
    description = "Constructs a regular icosahedron from a center point, edge length, and optional orientation",
    category = "geometry.primitives",
    order = 23
)
public class IcosahedronByCenterEdgeNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_EDGE_LENGTH_ID = "input_edge_length";
    private static final String INPUT_ORIENTATION_ID = "input_orientation";

    private static final String OUTPUT_ICOSAHEDRON_ID = "output_icosahedron";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_EDGE_LENGTH_ID = "output_edge_length";
    private static final String OUTPUT_VERTICES_ID = "output_vertices";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Rotation X (°)", category = "Orientation", order = 1,
        description = "Euler rotation about X in degrees when orientation port is not connected")
    private double rotationXDeg = 0.0d;

    @NodeProperty(displayName = "Rotation Y (°)", category = "Orientation", order = 2,
        description = "Euler rotation about Y in degrees when orientation port is not connected")
    private double rotationYDeg = 0.0d;

    @NodeProperty(displayName = "Rotation Z (°)", category = "Orientation", order = 3,
        description = "Euler rotation about Z in degrees when orientation port is not connected")
    private double rotationZDeg = 0.0d;

    public IcosahedronByCenterEdgeNode() {
        super(UUID.randomUUID(), "geometry.primitives.icosahedron");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Icosahedron center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_EDGE_LENGTH_ID, "Edge Length", "Length of each edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIENTATION_ID, "Orientation", "Optional rotation matrix (local → world)", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_ICOSAHEDRON_ID, "Icosahedron", "Constructed icosahedron geometry", NodeDataType.ICOSAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_EDGE_LENGTH_ID, "Edge Length", "Resolved edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "World-space vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an icosahedron could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a regular icosahedron from a center point, edge length, and optional orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object edgeObj = inputValues.get(INPUT_EDGE_LENGTH_ID);
        if (center == null || !(edgeObj instanceof Number edgeNum)) {
            writeEmptyOutputs();
            return;
        }

        double edge = edgeNum.doubleValue();
        if (!Double.isFinite(edge) || edge <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Matrix3d orient = PolyhedronOrientationUtil.resolveFromPortOrEuler(
            inputValues.get(INPUT_ORIENTATION_ID),
            rotationXDeg,
            rotationYDeg,
            rotationZDeg
        );
        IcosahedronGeometryData icosahedron = new IcosahedronGeometryData(center, edge, orient);
        outputValues.put(OUTPUT_ICOSAHEDRON_ID, icosahedron);
        outputValues.put(OUTPUT_GEOMETRY_ID, icosahedron);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_EDGE_LENGTH_ID, edge);
        outputValues.put(OUTPUT_VERTICES_ID, icosahedron.getVertices());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("rotationXDeg", rotationXDeg);
        state.put("rotationYDeg", rotationYDeg);
        state.put("rotationZDeg", rotationZDeg);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("rotationXDeg") instanceof Number n) {
            rotationXDeg = n.doubleValue();
        }
        if (map.get("rotationYDeg") instanceof Number n) {
            rotationYDeg = n.doubleValue();
        }
        if (map.get("rotationZDeg") instanceof Number n) {
            rotationZDeg = n.doubleValue();
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_ICOSAHEDRON_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_EDGE_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, java.util.List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
