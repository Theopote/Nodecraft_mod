package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PlaneGridPreviewData;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_plane",
    displayName = "Preview Plane",
    description = "Previews a plane as a square grid with axes and normal direction",
    category = "output.preview",
    order = 4
)
public class PreviewPlaneNode extends BaseNode {

    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_GRID_SIZE_ID = "input_grid_size";
    private static final String INPUT_GRID_SPACING_ID = "input_grid_spacing";
    private static final String INPUT_SHOW_AXES_ID = "input_show_axes";
    private static final String INPUT_SHOW_NORMAL_ID = "input_show_normal";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Grid Size", category = "Preview", order = 2)
    private double gridSize = 8.0d;

    @NodeProperty(displayName = "Grid Spacing", category = "Preview", order = 3)
    private double gridSpacing = 1.0d;

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 4)
    private float lineWidth = 1.5f;

    @NodeProperty(displayName = "Show Axes", category = "Preview", order = 5)
    private boolean showAxes = true;

    @NodeProperty(displayName = "Show Normal", category = "Preview", order = 6)
    private boolean showNormal = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 7)
    private int duration = 30;

    public PreviewPlaneNode() {
        super(UUID.randomUUID(), "output.preview.preview_plane");

        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Plane to preview", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_GRID_SIZE_ID, "Grid Size", "Preview grid size", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_GRID_SPACING_ID, "Grid Spacing", "Preview grid spacing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SHOW_AXES_ID, "Show Axes", "Whether to render local axes", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SHOW_NORMAL_ID, "Show Normal", "Whether to render plane normal", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews a plane as a square grid with axes and normal direction";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String previewId = null;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (inputValues.get(INPUT_PLANE_ID) instanceof PlaneData plane) {
            PreviewManager.hideNodePreviews(getId().toString());

            PlaneGridPreviewData previewData = new PlaneGridPreviewData(
                plane,
                resolveDouble(INPUT_GRID_SIZE_ID, gridSize, 1.0d),
                resolveDouble(INPUT_GRID_SPACING_ID, gridSpacing, 0.25d),
                resolveBoolean(INPUT_SHOW_AXES_ID, showAxes),
                resolveBoolean(INPUT_SHOW_NORMAL_ID, showNormal)
            );

            PreviewOptions options = new PreviewOptions()
                .setColor(0.35f, 0.75f, 1.0f)
                .setLineWidth(Math.max(0.5f, lineWidth))
                .setDuration(duration);

            previewId = PreviewManager.showPlaneGrid(getId().toString(), previewData, options);
            success = previewId != null;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    private double resolveDouble(String key, double fallback, double minValue) {
        Object value = inputValues.get(key);
        if (value instanceof Number number) {
            return Math.max(minValue, number.doubleValue());
        }
        return Math.max(minValue, fallback);
    }

    private boolean resolveBoolean(String key, boolean fallback) {
        Object value = inputValues.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("gridSize", gridSize);
        state.put("gridSpacing", gridSpacing);
        state.put("showAxes", showAxes);
        state.put("showNormal", showNormal);
        state.put("duration", duration);
        state.put("lineWidth", lineWidth);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("previewEnabled") instanceof Boolean bool) {
            previewEnabled = bool;
        }
        if (map.get("gridSize") instanceof Number number) {
            gridSize = Math.max(1.0d, number.doubleValue());
        }
        if (map.get("gridSpacing") instanceof Number number) {
            gridSpacing = Math.max(0.25d, number.doubleValue());
        }
        if (map.get("showAxes") instanceof Boolean bool) {
            showAxes = bool;
        }
        if (map.get("showNormal") instanceof Boolean bool) {
            showNormal = bool;
        }
        if (map.get("duration") instanceof Number number) {
            duration = Math.max(1, number.intValue());
        }
        if (map.get("lineWidth") instanceof Number number) {
            lineWidth = Math.max(0.5f, number.floatValue());
        }
    }
}
