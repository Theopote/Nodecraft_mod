package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.TextLabelPreviewData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_labels",
    displayName = "Preview Labels",
    description = "Displays a text label at a reference position",
    category = "output.preview",
    order = 8
)
public class PreviewLabelsNode extends BaseNode {

    private static final String INPUT_POSITION_ID = "input_position";
    private static final String INPUT_TEXT_ID = "input_text";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Label Text", category = "Preview", order = 2)
    private String defaultText = "Label";

    @NodeProperty(displayName = "Font Size", category = "Preview", order = 3)
    private float fontSize = 0.025f;

    @NodeProperty(displayName = "Show Background", category = "Preview", order = 4)
    private boolean showBackground = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 5)
    private int duration = 30;

    public PreviewLabelsNode() {
        super(UUID.randomUUID(), "output.preview.preview_labels");
        addInputPort(new BasePort(INPUT_POSITION_ID, "Position", "Label position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TEXT_ID, "Text", "Label text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Displays a text label at a reference position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String previewId = null;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (inputValues.get(INPUT_POSITION_ID) instanceof BlockPos pos) {
            String text = inputValues.get(INPUT_TEXT_ID) instanceof String value && !value.isBlank() ? value : defaultText;
            Vec3d position = pos.toCenterPos().add(0.0d, 0.35d, 0.0d);
            TextLabelPreviewData data = new TextLabelPreviewData(position, text);

            PreviewManager.hideNodePreviews(getId().toString());
            PreviewOptions options = new PreviewOptions().setDuration(duration);
            options.fontSize = fontSize;
            options.showBackground = showBackground;
            previewId = PreviewManager.showTextLabels(getId().toString(), data, options);
            success = previewId != null;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("defaultText", defaultText);
        state.put("duration", duration);
        state.put("fontSize", fontSize);
        state.put("showBackground", showBackground);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("defaultText") instanceof String text) {
                defaultText = text;
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
            if (map.get("fontSize") instanceof Number number) {
                fontSize = Math.max(0.01f, number.floatValue());
            }
            if (map.get("showBackground") instanceof Boolean bool) {
                showBackground = bool;
            }
        }
    }
}
