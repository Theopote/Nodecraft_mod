package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.TransformGizmoPreviewData;
import com.nodecraft.nodesystem.preview.gizmo.GizmoBindingRegistry;
import com.nodecraft.nodesystem.preview.gizmo.GizmoTransformCallback;
import com.nodecraft.nodesystem.util.GeometryTransform;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.transform_geometry",
    displayName = "Transform Geometry",
    description = "Applies translation, Euler XYZ rotation, and uniform scale to analytic geometry (primitives, composites, booleans, SDF wrappers)",
    category = "transform.basic_transforms",
    order = 9
)
public class TransformGeometryNode extends BaseNode {

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

    @NodeProperty(displayName = "Show Gizmo", category = "Gizmo", order = 8)
    private boolean showGizmo = true;

    @NodeProperty(displayName = "Gizmo Mode", category = "Gizmo", order = 9)
    private String gizmoMode = "all";

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_TRANSLATION_ID = "input_translation";
    private static final String INPUT_ROT_X_ID = "input_rotation_x";
    private static final String INPUT_ROT_Y_ID = "input_rotation_y";
    private static final String INPUT_ROT_Z_ID = "input_rotation_z";
    private static final String INPUT_SCALE_ID = "input_scale";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransformGeometryNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.transform_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to transform", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_TRANSLATION_ID, "Translation", "Optional translation vector override", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ROT_X_ID, "Rotation X", "Rotation around X axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Y_ID, "Rotation Y", "Rotation around Y axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROT_Z_ID, "Rotation Z", "Rotation around Z axis in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Uniform scale factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Transformed geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when transform fails", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when transform succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies translation, Euler XYZ rotation, and uniform scale to analytic geometry (primitives, composites, booleans, SDF wrappers)";
    }

    @Override
    public String getDisplayName() {
        return "Transform Geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geomObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geomObj instanceof GeometryData geometry)) {
            hideGizmoPreview();
            writeResult(null, false, "Missing geometry input");
            return;
        }

        Vector3d translation = inputValues.get(INPUT_TRANSLATION_ID) instanceof Vector3d t
            ? new Vector3d(t)
            : new Vector3d(translationX, translationY, translationZ);
        double rx = getInputDouble(INPUT_ROT_X_ID, rotationX);
        double ry = getInputDouble(INPUT_ROT_Y_ID, rotationY);
        double rz = getInputDouble(INPUT_ROT_Z_ID, rotationZ);
        double s = getInputDouble(INPUT_SCALE_ID, scale);

        if (!isFinite(translation)) {
            hideGizmoPreview();
            writeResult(null, false, "Translation contains NaN or Infinity");
            return;
        }
        if (!Double.isFinite(rx) || !Double.isFinite(ry) || !Double.isFinite(rz)) {
            hideGizmoPreview();
            writeResult(null, false, "Rotation contains NaN or Infinity");
            return;
        }
        if (!Double.isFinite(s) || Math.abs(s) <= 1.0e-9d) {
            hideGizmoPreview();
            writeResult(null, false, "Scale must be finite and non-zero");
            return;
        }

        GeometryData transformed = GeometryTransform.transform(geometry, translation, rx, ry, rz, s);
        writeResult(transformed, transformed != null, transformed == null ? "Unsupported geometry transform" : "");
        updateGizmoPreview(translation);
    }

    private void updateGizmoPreview(Vector3d translation) {
        String ownerId = getId().toString();
        if (!showGizmo) {
            hideGizmoPreview();
            return;
        }

        GizmoBindingRegistry.register(ownerId, createGizmoCallback());
        TransformGizmoPreviewData data = new TransformGizmoPreviewData(
            new Vec3d(translation.x, translation.y, translation.z),
            new Vec3d(1.0d, 0.0d, 0.0d),
            new Vec3d(0.0d, 1.0d, 0.0d),
            new Vec3d(0.0d, 0.0d, 1.0d),
            2.0d,
            gizmoMode
        );
        PreviewOptions options = PreviewOptions.createTransformGizmo();
        options.gizmoType = gizmoMode;
        PreviewManager.showTransformGizmo(ownerId, data, options);
    }

    private GizmoTransformCallback createGizmoCallback() {
        return (translationDelta, rotationDeltaDeg, scaleDelta) -> {
            if (translationDelta.lengthSquared() > 1.0e-12d) {
                translationX += translationDelta.x;
                translationY += translationDelta.y;
                translationZ += translationDelta.z;
            }
            if (rotationDeltaDeg.lengthSquared() > 1.0e-12d) {
                rotationX += rotationDeltaDeg.x;
                rotationY += rotationDeltaDeg.y;
                rotationZ += rotationDeltaDeg.z;
            }
            if (Math.abs(scaleDelta - 1.0d) > 1.0e-9d && Double.isFinite(scaleDelta) && scaleDelta > 1.0e-9d) {
                scale *= scaleDelta;
            }
            markDirty();
        };
    }

    private void hideGizmoPreview() {
        GizmoBindingRegistry.unregister(getId().toString());
        PreviewManager.hideNodePreviewType(getId().toString(), "transformation_gizmo");
    }

    @Override
    public void onNodeRemoved() {
        hideGizmoPreview();
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
        state.put("showGizmo", showGizmo);
        state.put("gizmoMode", gizmoMode);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("translationX") instanceof Number value) translationX = value.doubleValue();
        if (map.get("translationY") instanceof Number value) translationY = value.doubleValue();
        if (map.get("translationZ") instanceof Number value) translationZ = value.doubleValue();
        if (map.get("rotationX") instanceof Number value) rotationX = value.doubleValue();
        if (map.get("rotationY") instanceof Number value) rotationY = value.doubleValue();
        if (map.get("rotationZ") instanceof Number value) rotationZ = value.doubleValue();
        if (map.get("scale") instanceof Number value) scale = value.doubleValue();
        if (map.get("showGizmo") instanceof Boolean value) showGizmo = value;
        if (map.get("gizmoMode") instanceof String value) gizmoMode = value;
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid, String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
