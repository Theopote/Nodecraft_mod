package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
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
    description = "Applies translation and Euler rotation (degrees) to a FRAME. Output is orthonormal orientation-only.",
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

    private static final String INPUT_FRAME_ID = "input_frame";
    private static final String INPUT_TRANSLATION_ID = "input_translation";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransformFrameNode() {
        super(UUID.randomUUID(), "reference.frames.transform_frame");
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Input frame to transform", NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Optional translation vector override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around Z axis in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Transformed orthonormal frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation and Euler rotation (degrees) to a FRAME. Output is orthonormal orientation-only.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeInvalid();
            return;
        }

        Vector3d origin = frame.getOrigin();
        Vector3d xAxis = frame.getXAxis();
        Vector3d yAxis = frame.getYAxis();
        Vector3d zAxis = frame.getZAxis();

        Vector3d translation = SpatialValueResolver.resolveVector(inputValues.get(INPUT_TRANSLATION_ID));
        if (translation == null) {
            translation = new Vector3d(translationX, translationY, translationZ);
        }
        double rx = inputValues.get(INPUT_ROT_X_ID) instanceof Number n ? n.doubleValue() : rotationX;
        double ry = inputValues.get(INPUT_ROT_Y_ID) instanceof Number n ? n.doubleValue() : rotationY;
        double rz = inputValues.get(INPUT_ROT_Z_ID) instanceof Number n ? n.doubleValue() : rotationZ;
        if (!FrameUtils.isFinite(translation)
            || !Double.isFinite(rx)
            || !Double.isFinite(ry)
            || !Double.isFinite(rz)) {
            writeInvalid();
            return;
        }

        Matrix3d rotation = new Matrix3d().rotateXYZ(
            Math.toRadians(rx),
            Math.toRadians(ry),
            Math.toRadians(rz)
        );

        Vector3d outX = rotation.transform(new Vector3d(xAxis));
        Vector3d outY = rotation.transform(new Vector3d(yAxis));
        Vector3d outZ = rotation.transform(new Vector3d(zAxis));
        Vector3d outOrigin = origin.add(translation, new Vector3d());

        FrameData outFrame = FrameData.orthonormal(outOrigin, outX, outY, outZ);
        if (outFrame == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_FRAME_ID, outFrame);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FRAME_ID, null);
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
