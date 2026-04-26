package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
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
    id = "geometry.primitives.octahedron",
    displayName = "Octahedron By Center Size",
    description = "Constructs octahedron geometry from a center point, vertex radius, and optional orientation",
    category = "geometry.primitives",
    order = 10
)
public class OctahedronByCenterSizeNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String INPUT_ORIENTATION_ID = "input_orientation";

    private static final String OUTPUT_OCTAHEDRON_ID = "output_octahedron";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_SIZE_ID = "output_size";
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

    public OctahedronByCenterSizeNode() {
        super(UUID.randomUUID(), "geometry.primitives.octahedron");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Octahedron center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Distance from center to vertices", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIENTATION_ID, "Orientation", "Optional rotation matrix (local → world)", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_OCTAHEDRON_ID, "Octahedron", "Constructed octahedron geometry", NodeDataType.OCTAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Resolved vertex radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "Resolved octahedron vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an octahedron could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs octahedron geometry from a center point, vertex radius, and optional orientation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);
        if (center == null || !(sizeObj instanceof Number sizeNumber)) {
            writeEmptyOutputs();
            return;
        }

        double size = sizeNumber.doubleValue();
        if (!Double.isFinite(size) || size < 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Matrix3d orient = PolyhedronOrientationUtil.resolveFromPortOrEuler(
            inputValues.get(INPUT_ORIENTATION_ID),
            rotationXDeg,
            rotationYDeg,
            rotationZDeg
        );
        OctahedronGeometryData octahedron = new OctahedronGeometryData(center, size, orient);
        outputValues.put(OUTPUT_OCTAHEDRON_ID, octahedron);
        outputValues.put(OUTPUT_GEOMETRY_ID, octahedron);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_SIZE_ID, size);
        outputValues.put(OUTPUT_VERTICES_ID, octahedron.getVertices());
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
        outputValues.put(OUTPUT_OCTAHEDRON_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_SIZE_ID, 0.0d);
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
