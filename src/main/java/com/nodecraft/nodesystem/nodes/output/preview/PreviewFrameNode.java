package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.FrameAxesPreviewData;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_frame",
    displayName = "Preview Frame",
    description = "Previews a local coordinate frame with X, Y and Z axes",
    category = "output.preview",
    order = 5
)
public class PreviewFrameNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";
    private static final String INPUT_Z_AXIS_ID = "input_z_axis";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Axis Length", category = "Preview", order = 2)
    private double axisLength = 4.0d;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 3)
    private int duration = 30;

    public PreviewFrameNode() {
        super(UUID.randomUUID(), "output.preview.preview_frame");
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Frame origin", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to derive a frame", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional X axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Optional Y axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_AXIS_ID, "Z Axis", "Optional Z axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews a local coordinate frame with X, Y and Z axes";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String previewId = null;

        Vec3d origin = resolveOrigin();
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (origin != null) {
            FrameAxesPreviewData data = buildFrame(origin);
            if (data != null) {
                PreviewManager.hideNodePreviews(getId().toString());
                PreviewOptions options = new PreviewOptions().setDuration(duration);
                previewId = PreviewManager.showFrameAxes(getId().toString(), data, options);
                success = previewId != null;
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    private Vec3d resolveOrigin() {
        if (inputValues.get(INPUT_ORIGIN_ID) instanceof BlockPos pos) {
            return pos.toCenterPos();
        }
        if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            Vector3d point = plane.getPoint();
            return new Vec3d(point.x, point.y, point.z);
        }
        return null;
    }

    private FrameAxesPreviewData buildFrame(Vec3d origin) {
        Vec3d xAxis = resolveAxis(INPUT_X_AXIS_ID);
        Vec3d yAxis = resolveAxis(INPUT_Y_AXIS_ID);
        Vec3d zAxis = resolveAxis(INPUT_Z_AXIS_ID);

        if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            Vec3d normal = new Vec3d(plane.getNormal().x, plane.getNormal().y, plane.getNormal().z);
            if (zAxis == null) {
                zAxis = normal;
            }
            if (xAxis == null || yAxis == null) {
                Vec3d tangent = buildTangent(normal);
                Vec3d bitangent = normal.normalize().crossProduct(tangent).normalize();
                if (xAxis == null) {
                    xAxis = tangent;
                }
                if (yAxis == null) {
                    yAxis = bitangent;
                }
            }
        }

        if (xAxis == null) {
            xAxis = new Vec3d(1.0d, 0.0d, 0.0d);
        }
        if (yAxis == null) {
            yAxis = new Vec3d(0.0d, 1.0d, 0.0d);
        }
        if (zAxis == null) {
            zAxis = new Vec3d(0.0d, 0.0d, 1.0d);
        }

        return new FrameAxesPreviewData(origin, xAxis, yAxis, zAxis, axisLength);
    }

    private Vec3d resolveAxis(String key) {
        if (inputValues.get(key) instanceof Vector3d vector) {
            return new Vec3d(vector.x, vector.y, vector.z);
        }
        return null;
    }

    private Vec3d buildTangent(Vec3d normal) {
        Vec3d normalized = normal.normalize();
        Vec3d reference = Math.abs(normalized.y) < 0.99d ? new Vec3d(0.0d, 1.0d, 0.0d) : new Vec3d(1.0d, 0.0d, 0.0d);
        Vec3d tangent = reference.crossProduct(normalized);
        if (tangent.lengthSquared() < 1.0e-9d) {
            tangent = new Vec3d(1.0d, 0.0d, 0.0d);
        }
        return tangent.normalize();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("axisLength", axisLength);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("axisLength") instanceof Number number) {
                axisLength = Math.max(0.25d, number.doubleValue());
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
        }
    }
}
