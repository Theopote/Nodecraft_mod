package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.FrameAxesPreviewData;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PREVIEW_WRITE,
    id = "output.preview.preview_frame",
    displayName = "Preview Frame",
    description = "Previews a local coordinate frame with X, Y and Z axes",
    category = "output.preview",
    order = 5
)
public class PreviewFrameNode extends BaseNode {

    private static final String INPUT_FRAME_ID = "input_frame";
    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";
    private static final String INPUT_Y_AXIS_ID = "input_y_axis";
    private static final String INPUT_Z_AXIS_ID = "input_z_axis";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_STATUS_ID = "output_status";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    // Execution throttling: prevents rapid re-execution when node is selected (which causes flickering)
    private volatile long lastExecutionTime = 0;
    private static final long MIN_EXECUTION_INTERVAL_MS = 50;

    @NodeProperty(displayName = "Axis Length", category = "Preview", order = 2)
    private double axisLength = 4.0d;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 3)
    private int duration = 30;

    public PreviewFrameNode() {
        super(UUID.randomUUID(), "output.preview.preview_frame");
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Optional packed frame (overrides origin/axes)", NodeDataType.FRAME, this));
        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Frame origin point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Optional plane used to derive a frame", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional X axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Y_AXIS_ID, "Y Axis", "Optional Y axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_Z_AXIS_ID, "Z Axis", "Optional Z axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Diagnostic status for preview result", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // Throttle rapid re-execution when node is selected (prevents flickering)
        long now = System.currentTimeMillis();
        if (previewEnabled && now - lastExecutionTime < MIN_EXECUTION_INTERVAL_MS) {
            // Skip execution if called too soon
            return;
        }
        lastExecutionTime = now;
        boolean success = false;
        String previewId = null;
        String status = "preview_not_shown";

        Vec3d origin = resolveOrigin();
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            status = "preview_disabled";
        } else if (origin != null) {
            FrameAxesPreviewData data = buildFrame(origin);
            if (data != null) {
                PreviewManager.hideNodePreviews(getId().toString());
                PreviewOptions options = new PreviewOptions().setDuration(duration);
                previewId = PreviewManager.showFrameAxes(getId().toString(), data, options);
                success = previewId != null;
                status = success ? "ok" : "preview_manager_returned_null";
            } else {
                status = "invalid_frame_data";
            }
        } else {
            status = "missing_origin: connect Frame, Origin (POINT), or Plane";
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
        outputValues.put(OUTPUT_STATUS_ID, status);
    }

    private Vec3d resolveOrigin() {
        if (inputValues.get(INPUT_FRAME_ID) instanceof FrameData frame) {
            Vector3d point = frame.getOrigin();
            return new Vec3d(point.x, point.y, point.z);
        }
        Vector3d resolved = SpatialValueResolver.resolvePoint(inputValues.get(INPUT_ORIGIN_ID));
        if (resolved != null) {
            return new Vec3d(resolved.x, resolved.y, resolved.z);
        }
        if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            Vector3d point = plane.getPoint();
            return new Vec3d(point.x, point.y, point.z);
        }
        return null;
    }

    private FrameAxesPreviewData buildFrame(Vec3d origin) {
        if (inputValues.get(INPUT_FRAME_ID) instanceof FrameData frame) {
            return new FrameAxesPreviewData(
                origin,
                toVec3d(frame.getXAxis()),
                toVec3d(frame.getYAxis()),
                toVec3d(frame.getZAxis()),
                axisLength
            );
        }

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
        Vector3d vector = SpatialValueResolver.resolveVector(inputValues.get(key));
        return vector == null ? null : toVec3d(vector);
    }

    private static Vec3d toVec3d(Vector3d vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
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
