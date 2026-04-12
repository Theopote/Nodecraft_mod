package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "output.execute.clear_preview",
    displayName = "Clear Preview",
    description = "Clears all active previews in the current world",
    category = "output.execute",
    order = 1
)
public class ClearAllPreviewsNode extends BaseCustomUINode {

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_CLEARED_COUNT_ID = "output_cleared_count";

    private int lastClearedCount = 0;

    public ClearAllPreviewsNode() {
        super(UUID.randomUUID(), "output.execute.clear_preview");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Any non-null value triggers preview cleanup", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether previews were cleared", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CLEARED_COUNT_ID, "Cleared Count", "Number of previews removed", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Clears all active previews in the current world";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int clearedCount = 0;

        if (inputValues.get(INPUT_TRIGGER_ID) != null) {
            try {
                clearedCount = PreviewManager.getActivePreviewCount();
                PreviewManager.clearAllPreviews();
                lastClearedCount = clearedCount;
                success = true;
            } catch (Exception e) {
                System.err.println("Error clearing previews: " + e.getMessage());
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_CLEARED_COUNT_ID, clearedCount);
    }

    @Override
    protected float calculateUIHeight() {
        return 0.0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }
}
