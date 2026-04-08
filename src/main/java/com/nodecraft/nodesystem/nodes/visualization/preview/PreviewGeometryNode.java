package com.nodecraft.nodesystem.nodes.visualization.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.util.Color;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "visualization.preview.preview_geometry",
    displayName = "Preview Geometry",
    description = "Previews analytic geometry directly (semi-transparent fill + outline) before voxelization",
    category = "visualization.preview"
)
public class PreviewGeometryNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_CONE_GEOMETRY_ID = "input_cone_geometry";
    private static final String INPUT_ELLIPSOID_GEOMETRY_ID = "input_ellipsoid_geometry";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Fill Color", category = "Preview", order = 2)
    private String fillColor = "#53D6FF";

    @NodeProperty(displayName = "Show Fill", category = "Preview", order = 3)
    private boolean showFill = true;

    @NodeProperty(displayName = "Show Outline", category = "Preview", order = 4)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Transparency", category = "Preview", order = 5)
    private float transparency = 0.35f;

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 6)
    private float lineWidth = 1.6f;

    @NodeProperty(displayName = "Quality", category = "Preview", order = 7)
    private int quality = 20;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 8)
    private int duration = 30;

    public PreviewGeometryNode() {
        super(UUID.randomUUID(), "visualization.preview.preview_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified analytic geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CONE_GEOMETRY_ID, "Cone Geometry", "Cone geometry data", NodeDataType.CONE_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_ELLIPSOID_GEOMETRY_ID, "Ellipsoid Geometry", "Ellipsoid geometry data", NodeDataType.ELLIPSOID_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether preview is active", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Forwarded geometry output", NodeDataType.GEOMETRY, this));
    }

    @Override
    public String getDescription() {
        return "Previews analytic geometry directly (semi-transparent fill + outline) before voxelization";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = resolveGeometryInput();
        String previewId = null;
        boolean success = false;

        PreviewManager.hideNodePreviews(getId().toString());

        if (previewEnabled && geometry != null) {
            Color parsed = Color.fromHex(fillColor);
            PreviewOptions options = new PreviewOptions()
                .setColor(parsed.getRed(), parsed.getGreen(), parsed.getBlue())
                .setTintColor(Math.max(0.0f, parsed.getRed() * 0.2f), Math.max(0.0f, parsed.getGreen() * 0.2f), Math.max(0.0f, parsed.getBlue() * 0.2f))
                .setOpacity(clamp01(transparency))
                .setShowFill(showFill)
                .setShowOutline(showOutline)
                .setLineWidth(Math.max(0.25f, lineWidth))
                .setDuration(Math.max(1, duration));
            options.particleDensity = Math.max(8, Math.min(64, quality));

            previewId = PreviewRenderer.getInstance().showPreview(
                getId().toString(),
                "geometry_surface",
                geometry,
                options
            );
            success = previewId != null;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
    }

    private GeometryData resolveGeometryInput() {
        Object unified = inputValues.get(INPUT_GEOMETRY_ID);
        if (unified instanceof GeometryData geometry) {
            return geometry;
        }

        Object box = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        if (box instanceof BoxGeometryData data) {
            return data;
        }

        Object cylinder = inputValues.get(INPUT_CYLINDER_GEOMETRY_ID);
        if (cylinder instanceof CylinderGeometryData data) {
            return data;
        }

        Object sphere = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        if (sphere instanceof SphereData data) {
            return data;
        }

        Object torus = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        if (torus instanceof TorusGeometryData data) {
            return data;
        }

        Object cone = inputValues.get(INPUT_CONE_GEOMETRY_ID);
        if (cone instanceof ConeGeometryData data) {
            return data;
        }

        Object ellipsoid = inputValues.get(INPUT_ELLIPSOID_GEOMETRY_ID);
        if (ellipsoid instanceof EllipsoidGeometryData data) {
            return data;
        }

        return null;
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("fillColor", fillColor);
        state.put("showFill", showFill);
        state.put("showOutline", showOutline);
        state.put("transparency", transparency);
        state.put("lineWidth", lineWidth);
        state.put("quality", quality);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("previewEnabled") instanceof Boolean value) {
            previewEnabled = value;
        }
        if (map.get("fillColor") instanceof String value) {
            fillColor = value;
        }
        if (map.get("showFill") instanceof Boolean value) {
            showFill = value;
        }
        if (map.get("showOutline") instanceof Boolean value) {
            showOutline = value;
        }
        if (map.get("transparency") instanceof Number value) {
            transparency = clamp01(value.floatValue());
        }
        if (map.get("lineWidth") instanceof Number value) {
            lineWidth = Math.max(0.25f, value.floatValue());
        }
        if (map.get("quality") instanceof Number value) {
            quality = Math.max(8, Math.min(64, value.intValue()));
        }
        if (map.get("duration") instanceof Number value) {
            duration = Math.max(1, value.intValue());
        }
    }
}
