package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PolyhedronOrientationUtil;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractPolyhedronNode<T extends GeometryData> extends BaseNode {

    protected static final String INPUT_CENTER_ID = "input_center";
    protected static final String INPUT_ORIENTATION_ID = "input_orientation";

    protected static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    protected static final String OUTPUT_CENTER_ID = "output_center";
    protected static final String OUTPUT_VERTICES_ID = "output_vertices";
    protected static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Rotation X (deg)", category = "Orientation", order = 1,
        description = "Euler rotation about X in degrees when orientation port is not connected")
    protected double rotationXDeg = 0.0d;

    @NodeProperty(displayName = "Rotation Y (deg)", category = "Orientation", order = 2,
        description = "Euler rotation about Y in degrees when orientation port is not connected")
    protected double rotationYDeg = 0.0d;

    @NodeProperty(displayName = "Rotation Z (deg)", category = "Orientation", order = 3,
        description = "Euler rotation about Z in degrees when orientation port is not connected")
    protected double rotationZDeg = 0.0d;

    private final String inputSizeId;
    private final String outputPrimaryId;
    private final String outputSizeId;

    protected AbstractPolyhedronNode(
        String typeId,
        String inputSizeId,
        String inputSizeName,
        String inputSizeDescription,
        String outputPrimaryId,
        String outputPrimaryName,
        String outputPrimaryDescription,
        NodeDataType outputPrimaryType,
        String outputSizeId,
        String outputSizeName,
        String outputSizeDescription
    ) {
        super(UUID.randomUUID(), typeId);
        this.inputSizeId = inputSizeId;
        this.outputPrimaryId = outputPrimaryId;
        this.outputSizeId = outputSizeId;

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Polyhedron center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(inputSizeId, inputSizeName, inputSizeDescription, NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ORIENTATION_ID, "Orientation", "Optional rotation matrix (local -> world)", NodeDataType.ANY, this));

        addOutputPort(new BasePort(outputPrimaryId, outputPrimaryName, outputPrimaryDescription, outputPrimaryType, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(outputSizeId, outputSizeName, outputSizeDescription, NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VERTICES_ID, "Vertices", "World-space vertices", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object sizeObj = inputValues.get(inputSizeId);
        if (center == null || !(sizeObj instanceof Number sizeNumber)) {
            writeEmptyOutputs();
            return;
        }

        double size = sizeNumber.doubleValue();
        if (!Double.isFinite(size) || size <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Matrix3d orientation = PolyhedronOrientationUtil.resolveFromPortOrEuler(
            inputValues.get(INPUT_ORIENTATION_ID),
            rotationXDeg,
            rotationYDeg,
            rotationZDeg
        );

        T geometry = createGeometry(center, size, orientation);
        outputValues.put(outputPrimaryId, geometry);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(outputSizeId, size);
        outputValues.put(OUTPUT_VERTICES_ID, extractVertices(geometry));
        writeAdditionalOutputs(geometry, size);
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

    protected void writeEmptyOutputs() {
        outputValues.put(outputPrimaryId, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(outputSizeId, 0.0d);
        outputValues.put(OUTPUT_VERTICES_ID, List.of());
        clearAdditionalOutputs();
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    protected Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }

    protected abstract T createGeometry(Vector3d center, double size, Matrix3d orientation);

    protected abstract List<Vector3d> extractVertices(T geometry);

    protected void writeAdditionalOutputs(T geometry, double size) {
    }

    protected void clearAdditionalOutputs() {
    }
}