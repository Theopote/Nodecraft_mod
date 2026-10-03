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
import com.nodecraft.nodesystem.util.VectorUtils;
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
    description = "Applies translation and world-axis Euler XYZ rotation (degrees) to a FRAME. Output is orthonormal orientation-only.",
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
    @NodeProperty(displayName = "World Rotation X", category = "Transform", order = 4)
    private double rotationX = 0.0d;
    @NodeProperty(displayName = "World Rotation Y", category = "Transform", order = 5)
    private double rotationY = 0.0d;
    @NodeProperty(displayName = "World Rotation Z", category = "Transform", order = 6)
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
        addInputPort(new BasePort(INPUT_ROT_X_ID, "World Rotation X", "Rotation around world X axis in degrees (Euler XYZ order)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "World Rotation Y", "Rotation around world Y axis in degrees (Euler XYZ order)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "World Rotation Z", "Rotation around world Z axis in degrees (Euler XYZ order)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Transformed orthonormal frame", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frame transform succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation and world-axis Euler XYZ rotation (degrees) to a FRAME. Output is orthonormal orientation-only.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(frameObj instanceof FrameData frame)) {
            writeInvalid("Frame input must be FRAME");
            return;
        }
        if (!frame.isCanonical()) {
            writeInvalid("Frame must be canonical");
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
            writeInvalid("World Rotation X connected but invalid");
            return;
        }
        Double ry = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROT_Y_ID, rotationY);
        if (ry == null) {
            writeInvalid("World Rotation Y connected but invalid");
            return;
        }
        Double rz = OptionalPortDrive.resolveOptionalDouble(this, INPUT_ROT_Z_ID, rotationZ);
        if (rz == null) {
            writeInvalid("World Rotation Z connected but invalid");
            return;
        }

        double rxRad = Math.toRadians(rx);
        double ryRad = Math.toRadians(ry);
        double rzRad = Math.toRadians(rz);
        if (!Double.isFinite(rxRad) || !Double.isFinite(ryRad) || !Double.isFinite(rzRad)) {
            writeInvalid("World Rotation is too large");
            return;
        }

        Matrix3d rotation = new Matrix3d().rotateXYZ(rxRad, ryRad, rzRad);

        Vector3d outX = rotation.transform(new Vector3d(frame.getXAxis()));
        Vector3d outY = rotation.transform(new Vector3d(frame.getYAxis()));
        Vector3d outZ = rotation.transform(new Vector3d(frame.getZAxis()));
        Vector3d outOrigin = VectorUtils.safeAdd(frame.getOrigin(), translation);
        if (outOrigin == null) {
            writeInvalid("Transformed frame origin became non-finite");
            return;
        }

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
        if (map.get("translationX") instanceof Number n) {
            translationX = n.doubleValue();
        }
        if (map.get("translationY") instanceof Number n) {
            translationY = n.doubleValue();
        }
        if (map.get("translationZ") instanceof Number n) {
            translationZ = n.doubleValue();
        }
        if (map.get("rotationX") instanceof Number n) {
            rotationX = n.doubleValue();
        }
        if (map.get("rotationY") instanceof Number n) {
            rotationY = n.doubleValue();
        }
        if (map.get("rotationZ") instanceof Number n) {
            rotationZ = n.doubleValue();
        }
    }
}
