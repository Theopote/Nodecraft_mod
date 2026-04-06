package com.nodecraft.nodesystem.nodes.visualization.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "visualization.preview.preview_surface_strip",
    displayName = "Preview Surface Strip",
    description = "Previews a surface strip as section contours, rails, or a lattice overlay",
    category = "visualization.preview"
)
public class PreviewSurfaceStripNode extends BaseNode {

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_IDS_ID = "output_preview_ids";
    private static final String OUTPUT_PREVIEW_COUNT_ID = "output_preview_count";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Mode", category = "Preview", order = 2)
    private SurfaceStripBridge.BridgeMode mode = SurfaceStripBridge.BridgeMode.LATTICE;

    @NodeProperty(displayName = "Section Color", category = "Preview", order = 3)
    private String sectionColor = "#FFD933";

    @NodeProperty(displayName = "Rail Color", category = "Preview", order = 4)
    private String railColor = "#53D6FF";

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 5)
    private float lineWidth = 1.5f;

    @NodeProperty(displayName = "Show Direction", category = "Preview", order = 6)
    private boolean showDirection = false;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 7)
    private int duration = 30;

    public PreviewSurfaceStripNode() {
        super(UUID.randomUUID(), "visualization.preview.preview_surface_strip");
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to preview", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether any surface strip preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_IDS_ID, "Preview IDs", "List of active preview identifiers", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_COUNT_ID, "Preview Count", "Number of path previews shown", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews a surface strip as section contours, rails, or a lattice overlay";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        List<String> previewIds = new ArrayList<>();

        PreviewManager.hideNodePreviews(getId().toString());
        if (!previewEnabled || !(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.of());
            outputValues.put(OUTPUT_PREVIEW_COUNT_ID, 0);
            return;
        }

        if (mode.includeSectionEdges()) {
            Color parsedSectionColor = Color.fromHex(sectionColor);
            PreviewOptions options = buildOptions(parsedSectionColor);
            for (PolylineData sectionPath : buildSectionPaths(surfaceStrip)) {
                String previewId = PreviewManager.showPaths(getId().toString(), sectionPath, options);
                if (previewId != null) {
                    previewIds.add(previewId);
                }
            }
        }

        if (mode.includeRails()) {
            Color parsedRailColor = Color.fromHex(railColor);
            PreviewOptions options = buildOptions(parsedRailColor);
            for (LineData railSegment : buildRailSegments(surfaceStrip)) {
                String previewId = PreviewManager.showPaths(getId().toString(), railSegment, options);
                if (previewId != null) {
                    previewIds.add(previewId);
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, !previewIds.isEmpty());
        outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.copyOf(previewIds));
        outputValues.put(OUTPUT_PREVIEW_COUNT_ID, previewIds.size());
    }

    private PreviewOptions buildOptions(Color color) {
        PreviewOptions options = new PreviewOptions()
            .setColor(color.getRed(), color.getGreen(), color.getBlue())
            .setLineWidth(Math.max(0.25f, lineWidth))
            .setDuration(duration);
        options.showArrows = showDirection;
        options.arrowSize = 0.2f;
        options.smoothCurves = false;
        return options;
    }

    private List<PolylineData> buildSectionPaths(SurfaceStripData surfaceStrip) {
        List<PolylineData> result = new ArrayList<>();
        List<List<Vector3d>> sections = surfaceStrip.getSections();
        List<Boolean> closedFlags = surfaceStrip.getSectionClosedFlags();
        for (int i = 0; i < sections.size(); i++) {
            PolylineData polyline = createPolyline(sections.get(i), closedFlags.get(i));
            if (polyline != null) {
                result.add(polyline);
            }
        }
        return result;
    }

    private List<LineData> buildRailSegments(SurfaceStripData surfaceStrip) {
        List<LineData> result = new ArrayList<>();
        List<List<Vector3d>> sections = surfaceStrip.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            int pairCount = Math.min(current.size(), next.size());
            for (int pointIndex = 0; pointIndex < pairCount; pointIndex++) {
                Vector3d start = current.get(pointIndex);
                Vector3d end = next.get(pointIndex);
                result.add(new LineData(
                    new Vec3d(start.x, start.y, start.z),
                    new Vec3d(end.x, end.y, end.z)
                ));
            }
        }
        return result;
    }

    private PolylineData createPolyline(List<Vector3d> points, boolean closed) {
        if (points == null || points.size() < 2) {
            return null;
        }

        List<Vec3d> polylinePoints = new ArrayList<>(points.size() + 1);
        for (Vector3d point : points) {
            polylinePoints.add(new Vec3d(point.x, point.y, point.z));
        }
        if (closed) {
            Vector3d first = points.get(0);
            Vector3d last = points.get(points.size() - 1);
            if (!first.equals(last)) {
                polylinePoints.add(new Vec3d(first.x, first.y, first.z));
            }
        }
        return polylinePoints.size() >= 2 ? new PolylineData(polylinePoints) : null;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("mode", mode.name());
        state.put("sectionColor", sectionColor);
        state.put("railColor", railColor);
        state.put("lineWidth", lineWidth);
        state.put("showDirection", showDirection);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("mode") instanceof String value) {
                setModeString(value);
            }
            if (map.get("sectionColor") instanceof String value) {
                sectionColor = value;
            }
            if (map.get("railColor") instanceof String value) {
                railColor = value;
            }
            if (map.get("lineWidth") instanceof Number number) {
                lineWidth = Math.max(0.25f, number.floatValue());
            }
            if (map.get("showDirection") instanceof Boolean bool) {
                showDirection = bool;
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
        }
    }

    public void setModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            this.mode = SurfaceStripBridge.BridgeMode.LATTICE;
            return;
        }
        try {
            this.mode = SurfaceStripBridge.BridgeMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            this.mode = SurfaceStripBridge.BridgeMode.LATTICE;
        }
    }
}
