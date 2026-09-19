package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.transform_frame",
    displayName = "Transform Frame",
    description = "Applies translation, rotation, and uniform scale to an input frame",
    category = "reference.frames",
    order = 3
)
public class TransformFrameNode extends BaseNode {

    @NodeProperty(displayName = "Translation X", category = "Transform", order = 1)
    private double translationX = 0.0d;
    @NodeProperty(displayName = "Translation Y", category = "Transform", order = 2)
    private double translationY = 0.0d;
    @NodeProperty(displayName = "Translation Z", category = "Transform", order = 3)
    private double translationZ = 0.0d;
    @NodeProperty(displayName = "Rotation X", category = "Transform", order = 4)
    private double rotationX = 0.0d;
    @NodeProperty(displayName = "Rotation Y", category = "Transform", order = 5)
    private double rotationY = 0.0d;
    @NodeProperty(displayName = "Rotation Z", category = "Transform", order = 6)
    private double rotationZ = 0.0d;
    @NodeProperty(displayName = "Scale", category = "Transform", order = 7)
    private double scale = 1.0d;

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";
    private static final String INPUT_Z_AXIS_ID = "input_z_axis";
    private static final String INPUT_TRANSLATION_ID = "input_translation";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";
    private static final String INPUT_SCALE_ID = "input_scale";

    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransformFrameNode() {
        super(UUID.randomUUID(), "reference.frames.transform_frame");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Input frame origin", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Input frame X axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Input frame Y axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_AXIS_ID, "Z Axis", "Input frame Z axis", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Optional translation vector override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around Z axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Uniform scale factor applied to axis lengths", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "Transformed frame origin", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "Transformed frame X axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Transformed frame Y axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Transformed frame Z axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane built from transformed origin and Z axis", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation, rotation, and uniform scale to an input frame";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object originObj = inputValues.get(INPUT_ORIGIN_ID);
        Vector3d origin = FrameUtils.resolvePoint(originObj);
        if (origin == null) {
            if (originObj != null) {
                writeInvalid();
                return;
            }
            origin = new Vector3d(0.0d, 0.0d, 0.0d);
        }
        if (!FrameUtils.isFinite(origin)) {
            writeInvalid();
            return;
        }
        Vector3d xAxis = axisOrDefault(inputValues.get(INPUT_X_AXIS_ID), 1.0d, 0.0d, 0.0d);
        Vector3d yAxis = axisOrDefault(inputValues.get(INPUT_Y_AXIS_ID), 0.0d, 1.0d, 0.0d);
        Vector3d zAxis = axisOrDefault(inputValues.get(INPUT_Z_AXIS_ID), 0.0d, 0.0d, 1.0d);
        if (xAxis == null || yAxis == null || zAxis == null) {
            writeInvalid();
            return;
        }

        Vector3d translation = inputValues.get(INPUT_TRANSLATION_ID) instanceof Vector3d t
            ? new Vector3d(t)
            : new Vector3d(translationX, translationY, translationZ);
        double rx = inputValues.get(INPUT_ROT_X_ID) instanceof Number n ? n.doubleValue() : rotationX;
        double ry = inputValues.get(INPUT_ROT_Y_ID) instanceof Number n ? n.doubleValue() : rotationY;
        double rz = inputValues.get(INPUT_ROT_Z_ID) instanceof Number n ? n.doubleValue() : rotationZ;
        double s = inputValues.get(INPUT_SCALE_ID) instanceof Number n ? n.doubleValue() : scale;
        if (!FrameUtils.isFinite(translation)
            || !Double.isFinite(rx)
            || !Double.isFinite(ry)
            || !Double.isFinite(rz)
            || !Double.isFinite(s)
            || Math.abs(s) <= FrameUtils.EPS) {
            writeInvalid();
            return;
        }

        Matrix3d rotation = new Matrix3d().rotateXYZ(
            Math.toRadians(rx),
            Math.toRadians(ry),
            Math.toRadians(rz)
        );

        Vector3d outX = rotation.transform(new Vector3d(xAxis)).mul(s);
        Vector3d outY = rotation.transform(new Vector3d(yAxis)).mul(s);
        Vector3d outZ = rotation.transform(new Vector3d(zAxis)).mul(s);
        Vector3d outOrigin = origin.add(translation, new Vector3d());

        if (!FrameUtils.isUsableAxis(outX) || !FrameUtils.isUsableAxis(outY) || !FrameUtils.isUsableAxis(outZ)) {
            writeInvalid();
            return;
        }

        Vector3d planeNormal = new Vector3d(outZ).normalize();

        outputValues.put(OUTPUT_ORIGIN_ID, outOrigin);
        outputValues.put(OUTPUT_X_AXIS_ID, outX);
        outputValues.put(OUTPUT_Y_AXIS_ID, outY);
        outputValues.put(OUTPUT_Z_AXIS_ID, outZ);
        outputValues.put(OUTPUT_PLANE_ID, new PlaneData(outOrigin, planeNormal));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d axisOrDefault(Object axisObj, double x, double y, double z) {
        if (axisObj == null) {
            return new Vector3d(x, y, z);
        }
        if (axisObj instanceof Vector3d v && FrameUtils.isUsableAxis(v)) {
            return new Vector3d(v);
        }
        return null;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_X_AXIS_ID, null);
        outputValues.put(OUTPUT_Y_AXIS_ID, null);
        outputValues.put(OUTPUT_Z_AXIS_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("translationX", translationX);
        state.put("translationY", translationY);
        state.put("translationZ", translationZ);
        state.put("rotationX", rotationX);
        state.put("rotationY", rotationY);
        state.put("rotationZ", rotationZ);
        state.put("scale", scale);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        translationX = finiteOrCurrent(map.get("translationX"), translationX);
        translationY = finiteOrCurrent(map.get("translationY"), translationY);
        translationZ = finiteOrCurrent(map.get("translationZ"), translationZ);
        rotationX = finiteOrCurrent(map.get("rotationX"), rotationX);
        rotationY = finiteOrCurrent(map.get("rotationY"), rotationY);
        rotationZ = finiteOrCurrent(map.get("rotationZ"), rotationZ);
        double loadedScale = finiteOrCurrent(map.get("scale"), scale);
        if (Math.abs(loadedScale) > FrameUtils.EPS) {
            scale = loadedScale;
        }
    }

    private double finiteOrCurrent(Object value, double current) {
        if (value instanceof Number number) {
            double candidate = number.doubleValue();
            if (Double.isFinite(candidate)) {
                return candidate;
            }
        }
        return current;
    }
}
