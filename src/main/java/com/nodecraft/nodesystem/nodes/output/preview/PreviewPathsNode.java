package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Curve;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_curves",
    displayName = "Preview Curves",
    description = "Previews lines, polylines and curves as reference paths",
    category = "output.preview",
    order = 6
)
public class PreviewPathsNode extends BaseNode {

    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_POINTS_ID = "input_points";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 2)
    private float lineWidth = 1.5f;

    @NodeProperty(displayName = "Path Color", category = "Preview", order = 3)
    private String pathColor = "#FFD933";

    @NodeProperty(displayName = "Show Direction", category = "Preview", order = 4)
    private boolean showDirection = true;

    @NodeProperty(displayName = "Arrow Size", category = "Preview", order = 5)
    private float arrowSize = 0.25f;

    @NodeProperty(displayName = "Smooth Curves", category = "Preview", order = 6)
    private boolean smoothCurves = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 7)
    private int duration = 30;

    public PreviewPathsNode() {
        super(UUID.randomUUID(), "output.preview.preview_curves");
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Single straight segment to preview", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Multi-segment path to preview", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Sampled curve path to preview", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Fallback ordered point list used as a path", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews lines, polylines and curves as reference paths";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object previewData = null;

        if (inputValues.get(INPUT_LINE_ID) instanceof LineData line) {
            previewData = line;
        } else if (inputValues.get(INPUT_POLYLINE_ID) instanceof PolylineData polyline) {
            previewData = polyline;
        } else if (inputValues.get(INPUT_CURVE_ID) instanceof Curve curve) {
            previewData = curve;
        } else if (inputValues.get(INPUT_POINTS_ID) instanceof List<?> list) {
            previewData = list;
        }

        boolean success = false;
        String previewId = null;
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (previewData != null) {
            Color parsedColor = Color.fromHex(pathColor);
            PreviewManager.hideNodePreviews(getId().toString());
            PreviewOptions options = new PreviewOptions()
                .setColor(parsedColor.getRed(), parsedColor.getGreen(), parsedColor.getBlue())
                .setLineWidth(Math.max(0.25f, lineWidth))
                .setDuration(duration);
            options.smoothCurves = smoothCurves;
            options.showArrows = showDirection;
            options.arrowSize = Math.max(0.05f, arrowSize);
            previewId = PreviewManager.showPaths(getId().toString(), previewData, options);
            success = previewId != null;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("lineWidth", lineWidth);
        state.put("pathColor", pathColor);
        state.put("showDirection", showDirection);
        state.put("arrowSize", arrowSize);
        state.put("duration", duration);
        state.put("smoothCurves", smoothCurves);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("lineWidth") instanceof Number number) {
                lineWidth = Math.max(0.25f, number.floatValue());
            }
            if (map.get("pathColor") instanceof String value) {
                pathColor = value;
            }
            if (map.get("showDirection") instanceof Boolean bool) {
                showDirection = bool;
            }
            if (map.get("arrowSize") instanceof Number number) {
                arrowSize = Math.max(0.05f, number.floatValue());
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
            if (map.get("smoothCurves") instanceof Boolean bool) {
                smoothCurves = bool;
            }
        }
    }
}
