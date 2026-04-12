package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_regions",
    displayName = "Preview Region",
    description = "Previews a region boundary as a reference box",
    category = "output.preview",
    order = 7
)
public class PreviewRegionsNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Pulse Animation", category = "Preview", order = 2)
    private boolean pulse = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 3)
    private int duration = 30;

    public PreviewRegionsNode() {
        super(UUID.randomUUID(), "output.preview.preview_regions");
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to preview", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews a region boundary as a reference box";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String previewId = null;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (inputValues.get(INPUT_REGION_ID) instanceof RegionData region && region.isComplete()) {
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                PreviewManager.hideNodePreviews(getId().toString());
                PreviewOptions options = PreviewOptions.createRegionBox().setDuration(duration);
                if (!pulse) {
                    options.pulseAnimation = false;
                }
                previewId = PreviewManager.showRegionBox(
                    getId().toString(),
                    new Vec3d(min.getX(), min.getY(), min.getZ()),
                    new Vec3d(max.getX() + 1.0d, max.getY() + 1.0d, max.getZ() + 1.0d),
                    options
                );
                success = previewId != null;
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("duration", duration);
        state.put("pulse", pulse);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
            if (map.get("pulse") instanceof Boolean bool) {
                pulse = bool;
            }
        }
    }
}
