package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Color;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_profiles",
    displayName = "Preview Profiles",
    description = "Previews polygon profile boundaries and optional normal indicators",
    category = "output.preview",
    order = 10
)
public class PreviewPolygonProfilesNode extends BaseNode {

    private static final String INPUT_PROFILE_ID = "input_profile";
    private static final String INPUT_PROFILES_ID = "input_profiles";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_IDS_ID = "output_preview_ids";
    private static final String OUTPUT_PREVIEW_COUNT_ID = "output_preview_count";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Boundary Color", category = "Preview", order = 2)
    private String boundaryColor = "#FFD933";

    @NodeProperty(displayName = "Show Normals", category = "Preview", order = 3)
    private boolean showNormals = false;

    @NodeProperty(displayName = "Normal Color", category = "Preview", order = 4)
    private String normalColor = "#53D6FF";

    @NodeProperty(displayName = "Normal Length", category = "Preview", order = 5)
    private double normalLength = 1.5d;

    @NodeProperty(displayName = "Line Width", category = "Preview", order = 6)
    private float lineWidth = 1.5f;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 7)
    private int duration = 30;

    public PreviewPolygonProfilesNode() {
        super(UUID.randomUUID(), "output.preview.preview_profiles");

        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Single polygon profile to preview", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_PROFILES_ID, "Profiles", "Optional polygon profile list to preview", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether any polygon profile preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_IDS_ID, "Preview IDs", "List of active preview identifiers", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_COUNT_ID, "Preview Count", "Number of previews shown", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews polygon profile boundaries and optional normal indicators";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<PolygonProfileData> profiles = resolveProfiles();
        List<String> previewIds = new ArrayList<>();

        PreviewManager.hideNodePreviews(getId().toString());
        if (!previewEnabled || profiles.isEmpty()) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.of());
            outputValues.put(OUTPUT_PREVIEW_COUNT_ID, 0);
            return;
        }

        PreviewOptions boundaryOptions = buildOptions(Color.fromHex(boundaryColor));
        for (PolygonProfileData profile : profiles) {
            PolylineData boundary = profile.getBoundary();
            String previewId = PreviewManager.showPaths(getId().toString(), boundary, boundaryOptions);
            if (previewId != null) {
                previewIds.add(previewId);
            }
        }

        if (showNormals) {
            PreviewOptions normalOptions = buildOptions(Color.fromHex(normalColor));
            for (PolygonProfileData profile : profiles) {
                LineData normalLine = buildNormalLine(profile);
                String previewId = PreviewManager.showPaths(getId().toString(), normalLine, normalOptions);
                if (previewId != null) {
                    previewIds.add(previewId);
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, !previewIds.isEmpty());
        outputValues.put(OUTPUT_PREVIEW_IDS_ID, List.copyOf(previewIds));
        outputValues.put(OUTPUT_PREVIEW_COUNT_ID, previewIds.size());
    }

    private List<PolygonProfileData> resolveProfiles() {
        List<PolygonProfileData> profiles = new ArrayList<>();
        Object singleObj = inputValues.get(INPUT_PROFILE_ID);
        Object listObj = inputValues.get(INPUT_PROFILES_ID);

        if (singleObj instanceof PolygonProfileData profile) {
            profiles.add(profile);
        }
        if (listObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof PolygonProfileData profile) {
                    profiles.add(profile);
                }
            }
        }
        return profiles;
    }

    private LineData buildNormalLine(PolygonProfileData profile) {
        Vector3d start = profile.getCenter();
        Vector3d end = new Vector3d(start).add(new Vector3d(profile.getPlane().getNormal()).normalize().mul(Math.max(0.05d, normalLength)));
        return new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );
    }

    private PreviewOptions buildOptions(Color color) {
        PreviewOptions options = new PreviewOptions()
            .setColor(color.getRed(), color.getGreen(), color.getBlue())
            .setLineWidth(Math.max(0.25f, lineWidth))
            .setDuration(duration);
        options.showArrows = false;
        options.smoothCurves = false;
        return options;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("boundaryColor", boundaryColor);
        state.put("showNormals", showNormals);
        state.put("normalColor", normalColor);
        state.put("normalLength", normalLength);
        state.put("lineWidth", lineWidth);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("boundaryColor") instanceof String value) {
                boundaryColor = value;
            }
            if (map.get("showNormals") instanceof Boolean bool) {
                showNormals = bool;
            }
            if (map.get("normalColor") instanceof String value) {
                normalColor = value;
            }
            if (map.get("normalLength") instanceof Number number) {
                normalLength = Math.max(0.05d, number.doubleValue());
            }
            if (map.get("lineWidth") instanceof Number number) {
                lineWidth = Math.max(0.25f, number.floatValue());
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
        }
    }
}
