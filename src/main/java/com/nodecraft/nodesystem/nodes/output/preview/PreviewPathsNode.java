package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.protocol.PreviewCurvePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPayloadAdapters;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
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
    private static final String OUTPUT_PREVIEW_IDS_ID = "output_preview_ids";
    private static final String OUTPUT_PREVIEW_COUNT_ID = "output_preview_count";

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
        addInputPort(new BasePort(INPUT_POINTS_ID, "Paths / Points", "Fallback path list or ordered point list used as preview input", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_IDS_ID, "Preview IDs", "Active preview identifiers", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_COUNT_ID, "Preview Count", "Number of rendered path previews", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews lines, polylines and curves as reference paths";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> previewItems = resolvePreviewItems();
        List<String> previewIds = new ArrayList<>();
        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (!previewItems.isEmpty()) {
            Color parsedColor = Color.fromHex(pathColor);
            PreviewOptions options = new PreviewOptions()
                .setColor(parsedColor.getRed(), parsedColor.getGreen(), parsedColor.getBlue())
                .setLineWidth(Math.max(0.25f, lineWidth))
                .setDuration(duration);
            options.smoothCurves = smoothCurves;
            options.showArrows = showDirection;
            options.arrowSize = Math.max(0.05f, arrowSize);
            List<PreviewCurvePayload> curves = new ArrayList<>();
            for (Object previewItem : previewItems) {
                PreviewCurvePayload payload = PreviewPayloadAdapters.tryCurvePayloadFromPreviewSource(previewItem);
                if (payload != null) {
                    curves.add(payload);
                }
            }
            previewIds.addAll(PreviewManager.showPathCurves(getId().toString(), curves, options));
        } else {
            PreviewManager.hideNodePreviews(getId().toString());
        }

        outputValues.put(OUTPUT_SUCCESS_ID, !previewIds.isEmpty());
        outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.copyOf(previewIds));
        outputValues.put(OUTPUT_PREVIEW_COUNT_ID, previewIds.size());
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

    private List<Object> resolvePreviewItems() {
        List<Object> previewItems = new ArrayList<>();

        if (inputValues.get(INPUT_LINE_ID) instanceof LineData line) {
            previewItems.add(line);
        }
        if (inputValues.get(INPUT_POLYLINE_ID) instanceof PolylineData polyline) {
            previewItems.add(polyline);
        }
        if (inputValues.get(INPUT_CURVE_ID) instanceof Curve curve) {
            previewItems.add(curve);
        }

        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (pointsObj instanceof List<?> list && !list.isEmpty()) {
            if (isPointList(list)) {
                PolylineData polyline = createPolylineFromPoints(list);
                if (polyline != null) {
                    previewItems.add(polyline);
                }
            } else {
                for (Object entry : list) {
                    if (entry instanceof LineData || entry instanceof PolylineData || entry instanceof Curve) {
                        previewItems.add(entry);
                    } else if (entry instanceof List<?> nested && isPointList(nested)) {
                        PolylineData polyline = createPolylineFromPoints(nested);
                        if (polyline != null) {
                            previewItems.add(polyline);
                        }
                    }
                }
            }
        }

        return previewItems;
    }

    private boolean isPointList(Collection<?> list) {
        if (list.size() < 2) {
            return false;
        }
        for (Object entry : list) {
            if (resolveVec(entry) == null) {
                return false;
            }
        }
        return true;
    }

    private @Nullable PolylineData createPolylineFromPoints(Collection<?> list) {
        List<Vec3d> points = new ArrayList<>(list.size());
        for (Object entry : list) {
            Vec3d point = resolveVec(entry);
            if (point != null) {
                points.add(point);
            }
        }
        return points.size() >= 2 ? new PolylineData(points) : null;
    }

    private @Nullable Vec3d resolveVec(Object value) {
        if (value instanceof PointData pointData) {
            Vector3d p = pointData.getPosition();
            return new Vec3d(p.x, p.y, p.z);
        }
        if (value instanceof Vector3d vector) {
            return new Vec3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        if (value instanceof Coordinate coordinate) {
            return new Vec3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof Vec3d vec) {
            return vec;
        }
        return null;
    }
}
