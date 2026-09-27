package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    order = 5
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public TransformFrameNode() {
        super(UUID.randomUUID(), "reference.frames.transform_frame");
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Input frame to transform", NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Optional translation vector override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around Z axis in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Transformed orthonormal frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation and Euler rotation (degrees) to a FRAME. Output is orthonormal orientation-only.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeInvalid("Frame input must be FRAME");
            return;
        }

        FrameData canonical = frame.orthonormalized();
        if (canonical == null) {
            writeInvalid("Input frame must be usable and finite");
            return;
        }

        Vector3d propertyTranslation = new Vector3d(translationX, translationY, translationZ);
        Vector3d translation = OptionalPortDrive.resolveOptionalVector(this, INPUT_TRANSLATION_ID, propertyTranslation);
        if (translation == null) {
            writeInvalid("Translation connected but invalid");
            return;
        }
        if (!FrameUtils.isFinite(translation)) {
            writeInvalid("Translation must be finite");
            return;
        }

        Double rx = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROT_X_ID, rotationX);
        if (rx == null) {
            writeInvalid("Rotation X connected but invalid");
            return;
        }
        Double ry = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROT_Y_ID, rotationY);
        if (ry == null) {
            writeInvalid("Rotation Y connected but invalid");
            return;
        }
        Double rz = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROT_Z_ID, rotationZ);
        if (rz == null) {
            writeInvalid("Rotation Z connected but invalid");
            return;
        }

        Matrix3d rotation = new Matrix3d().rotateXYZ(
            Math.toRadians(rx),
            Math.toRadians(ry),
            Math.toRadians(rz)
        );

        Vector3d outX = rotation.transform(new Vector3d(canonical.getXAxis()));
        Vector3d outY = rotation.transform(new Vector3d(canonical.getYAxis()));
        Vector3d outZ = rotation.transform(new Vector3d(canonical.getZAxis()));
        Vector3d outOrigin = canonical.getOrigin().add(translation, new Vector3d());

        FrameData outFrame = FrameData.orthonormal(outOrigin, outX, outY, outZ);
        if (outFrame == null) {
            writeInvalid("Transformed frame became degenerate");
            return;
        }

        outputValues.put(OUTPUT_FRAME_ID, outFrame);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FRAME_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
