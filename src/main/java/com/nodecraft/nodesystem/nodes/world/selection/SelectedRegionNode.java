package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.interaction.AreaPreviewStyleSettings;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.Vector3;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "world.selection.selected_region",
    displayName = "Selected Region",
    description = "Gets the player's selected region defined by two corner points.",
    category = "world.selection",
    order = 1
)
public class SelectedRegionNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Auto Update",
        category = "Selection",
        order = 1,
        description = "Whether the node should refresh the selected region from the player automatically."
    )
    private boolean autoUpdate = true;

    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_BLOCK_ID = "output_min_block";
    private static final String OUTPUT_MAX_BLOCK_ID = "output_max_block";
    private static final String OUTPUT_HAS_SELECTION_ID = "output_has_selection";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private volatile Vector3 pos1;
    private volatile Vector3 pos2;
    private volatile boolean selecting = false;
    private volatile boolean waitingSecondPoint = false;
    private volatile String selectionHint = "Click Start Area Selection, then left-click two corner points in the world.";
    private volatile String completedRegionPreviewId;
    private volatile String completedBlocksPreviewId;
    private static final int MAX_BLOCK_PREVIEW_COUNT = 8192;

    private final AreaSelectionCallback areaSelectionCallback = new AreaSelectionCallback();

    private class AreaSelectionCallback implements NodeEditorInteractionManager.IAreaSelectionCallback {
        @Override
        public void onAreaSelected(Coordinate startPos, Coordinate endPos) {
            pos1 = new Vector3(startPos.x(), startPos.y(), startPos.z());
            pos2 = new Vector3(endPos.x(), endPos.y(), endPos.z());
            selecting = false;
            waitingSecondPoint = false;
            selectionHint = "Area selection complete.";
            updateOutputsFromPositions();
            syncCompletedRegionPreview();
            invalidateCache();
            markDirty();
        }

        @Override
        public void onFirstPointSelected(Coordinate position) {
            pos1 = new Vector3(position.x(), position.y(), position.z());
            pos2 = null;
            selecting = true;
            waitingSecondPoint = true;
            selectionHint = "First point selected. Choose the second point.";
            clearCompletedRegionPreview();
            clearCompletedBlocksPreview();
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
            outputValues.put(OUTPUT_VALID_ID, true);
            invalidateCache();
            markDirty();
        }

        @Override
        public void onInteractionCancelled() {
            selecting = false;
            waitingSecondPoint = false;
            selectionHint = "Area selection cancelled.";
            markDirty();
        }
    }

    public SelectedRegionNode() {
        super(UUID.randomUUID(), "world.selection.selected_region");

        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Selected region data for downstream world nodes", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_BLOCK_ID, "Min Block", "Minimum corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_BLOCK_ID, "Max Block", "Maximum corner as a block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_HAS_SELECTION_ID, "Has Selection", "Whether a complete two-corner selection exists", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether selection outputs are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));

        resetOutputs();
    }

    @Override
    public String getDescription() {
        return "Gets the player's selected region defined by two corner points.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean pending = NodeEditorInteractionManager.getInstance().isPendingAreaSelection(getId().toString());
        if (selecting != pending) {
            selecting = pending;
            if (!pending && waitingSecondPoint) {
                waitingSecondPoint = false;
            }
            markDirty();
        }

        if (pos1 == null || pos2 == null) {
            clearCompletedRegionPreview();
            // Keep the temporary first-point state. Full outputs are produced by updateOutputsFromPositions().
            if (pos1 == null) {
                resetOutputs();
            }
            return;
        }

        updateOutputsFromPositions();
        syncCompletedRegionPreview();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getSmallPadding();
        height += ImGui.getTextLineHeightWithSpacing();
        height += ImGui.getFrameHeight() * 3.0f;
        height += getSmallPadding();
        height += ImGui.getTextLineHeightWithSpacing() * 2.0f;
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float buttonPadding = 20.0f;
        float labelWidth = ImGui.calcTextSize("Start Area Selection").x;
        return Math.max(144.0f, labelWidth + buttonPadding);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        if (pos1 != null && pos2 != null) {
            syncCompletedRegionPreview();
        }

        return layout(zoom, layout -> {
            boolean changed = false;
            boolean hasSelection = pos1 != null && pos2 != null;
            float edgeMargin = layout.toPixels(getSmallPadding());
            float buttonWidth = Math.max(0.0f, layout.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            layout.addVerticalSpacing(getSmallPadding());

            ImGui.textWrapped(selectionHint);

            layout.addVerticalSpacing(getSmallPadding());

            if (!selecting) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF2E7D32);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF388E3C);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF1B5E20);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Start Area Selection##start_area_pick", buttonWidth, 0)) {
                    startAreaSelection();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF8A6D1A);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF9C7C1F);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF6E560D);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Cancel Area Selection##cancel_area_pick", buttonWidth, 0)) {
                    cancelAreaSelection();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            }

            layout.addVerticalSpacing(getSmallPadding());

            if (hasSelection) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF4444AA);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF5555CC);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF3333AA);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Clear Selection##clear", buttonWidth, 0)) {
                    clearSelection();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF333333);
                ImGui.pushStyleColor(ImGuiCol.Text, 0xFF666666);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.button("Clear Selection##clearDisabled", buttonWidth, 0);
                ImGui.popStyleColor(4);
            }

            layout.addVerticalSpacing(getSmallPadding());

            if (pos1 != null) {
                ImGui.text("Pos1: (" + (int) pos1.x() + ", " + (int) pos1.y() + ", " + (int) pos1.z() + ")");
            } else {
                ImGui.textDisabled("Pos1: Not set");
            }
            if (pos2 != null) {
                ImGui.text("Pos2: (" + (int) pos2.x() + ", " + (int) pos2.y() + ", " + (int) pos2.z() + ")");
            } else if (waitingSecondPoint) {
                ImGui.textDisabled("Pos2: Waiting for second point...");
            } else {
                ImGui.textDisabled("Pos2: Not set");
            }

            return changed;
        });
    }

    private void startAreaSelection() {
        selecting = true;
        waitingSecondPoint = false;
        selectionHint = "Choose the first point.";
        NodeEditorInteractionManager.getInstance().requestAreaSelection(getId().toString(), areaSelectionCallback);
        markDirty();
    }

    private void cancelAreaSelection() {
        NodeEditorInteractionManager.getInstance().cancelAreaSelection();
        selecting = false;
        waitingSecondPoint = false;
        selectionHint = "Area selection cancelled.";
        markDirty();
    }

    public void setPos1(int x, int y, int z) {
        pos1 = new Vector3(x, y, z);
        if (pos2 != null) {
            updateOutputsFromPositions();
        } else {
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
            outputValues.put(OUTPUT_VALID_ID, true);
        }
        invalidateCache();
        markDirty();
    }

    public void setPos2(int x, int y, int z) {
        pos2 = new Vector3(x, y, z);
        if (pos1 != null) {
            updateOutputsFromPositions();
        } else {
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
            outputValues.put(OUTPUT_VALID_ID, true);
        }
        invalidateCache();
        markDirty();
    }

    public void clearSelection() {
        if (NodeEditorInteractionManager.getInstance().isPendingAreaSelection(getId().toString())) {
            NodeEditorInteractionManager.getInstance().cancelAreaSelection();
        }
        clearCompletedRegionPreview();
        clearCompletedBlocksPreview();
        pos1 = null;
        pos2 = null;
        selecting = false;
        waitingSecondPoint = false;
        selectionHint = "Click Start Area Selection, then left-click two corner points in the world.";
        resetOutputs();
        invalidateCache();
        markDirty();
    }

    private void updateOutputsFromPositions() {
        if (pos1 == null || pos2 == null) {
            resetOutputs();
            return;
        }

        BlockPos minBlock = new BlockPos(
            (int) Math.min(pos1.x(), pos2.x()),
            (int) Math.min(pos1.y(), pos2.y()),
            (int) Math.min(pos1.z(), pos2.z())
        );
        BlockPos maxBlock = new BlockPos(
            (int) Math.max(pos1.x(), pos2.x()),
            (int) Math.max(pos1.y(), pos2.y()),
            (int) Math.max(pos1.z(), pos2.z())
        );

        outputValues.put(OUTPUT_REGION_ID, new RegionData(minBlock, maxBlock));
        outputValues.put(OUTPUT_MIN_BLOCK_ID, minBlock);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, maxBlock);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, true);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        if (this.autoUpdate != autoUpdate) {
            this.autoUpdate = autoUpdate;
            markDirty();
        }
    }

    public void onNodeRemoved() {
        if (NodeEditorInteractionManager.getInstance().isPendingAreaSelection(getId().toString())) {
            NodeEditorInteractionManager.getInstance().cancelAreaSelection();
        }
        clearCompletedRegionPreview();
        clearCompletedBlocksPreview();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("autoUpdate", isAutoUpdate());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.containsKey("autoUpdate")) {
                Object value = map.get("autoUpdate");
                if (value instanceof Boolean bool) {
                    setAutoUpdate(bool);
                }
            }
            // Ignore legacy pos1/pos2 — selection is session/runtime only.
            pos1 = null;
            pos2 = null;
            clearCompletedRegionPreview();
            clearCompletedBlocksPreview();
            resetOutputs();

            selecting = false;
            waitingSecondPoint = false;
            selectionHint = "Click Start Area Selection, then left-click two corner points in the world.";
        }
    }

    private void syncCompletedRegionPreview() {
        if (pos1 == null || pos2 == null) {
            clearCompletedRegionPreview();
            clearCompletedBlocksPreview();
            return;
        }

        Vec3d min = new Vec3d(
            Math.min(pos1.x(), pos2.x()),
            Math.min(pos1.y(), pos2.y()),
            Math.min(pos1.z(), pos2.z())
        );
        Vec3d max = new Vec3d(
            Math.max(pos1.x(), pos2.x()) + 1.0d,
            Math.max(pos1.y(), pos2.y()) + 1.0d,
            Math.max(pos1.z(), pos2.z()) + 1.0d
        );

        PreviewOptions options = createCompletedRegionPreviewOptions();
        Object[] regionData = new Object[] { min, max };

        if (completedRegionPreviewId == null) {
            completedRegionPreviewId = PreviewManager.showRegionBox(getId().toString(), min, max, options);
        } else {
            PreviewManager.updatePreview(completedRegionPreviewId, regionData);
            PreviewManager.updatePreviewOptions(completedRegionPreviewId, options);
        }

        syncCompletedBlocksPreview();
    }

    private PreviewOptions createCompletedRegionPreviewOptions() {
        AreaPreviewStyleSettings style = NodeEditorInteractionManager.getInstance().getAreaPreviewStyle();
        boolean showFill = style.isShowFill();
        boolean showOutline = style.isShowOutline();
        if (!showFill && !showOutline) {
            showOutline = true;
        }

        float[] outlineColor = style.getOutlineColor();
        float[] fillColor = style.getFillColor();

        PreviewOptions options = new PreviewOptions()
            .setColor(outlineColor[0], outlineColor[1], outlineColor[2])
            .setTintColor(fillColor[0], fillColor[1], fillColor[2])
            .setOpacity(Math.max(0.32f, style.getOpacity()))
            .setLineWidth(Math.max(2.2f, style.getLineWidth()))
            .setShowFill(showFill)
            .setShowOutline(showOutline);

        if (style.isEnablePulse()) {
            options.enablePulse();
        }

        return options;
    }

    private void clearCompletedRegionPreview() {
        if (completedRegionPreviewId != null) {
            PreviewManager.hidePreview(completedRegionPreviewId);
            completedRegionPreviewId = null;
        }
    }

    private void syncCompletedBlocksPreview() {
        if (pos1 == null || pos2 == null) {
            clearCompletedBlocksPreview();
            return;
        }

        int minX = (int) Math.min(pos1.x(), pos2.x());
        int minY = (int) Math.min(pos1.y(), pos2.y());
        int minZ = (int) Math.min(pos1.z(), pos2.z());
        int maxX = (int) Math.max(pos1.x(), pos2.x());
        int maxY = (int) Math.max(pos1.y(), pos2.y());
        int maxZ = (int) Math.max(pos1.z(), pos2.z());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long total = (long) sizeX * sizeY * sizeZ;

        if (total <= 0 || total > MAX_BLOCK_PREVIEW_COUNT) {
            clearCompletedBlocksPreview();
            return;
        }

        List<Coordinate> blocks = new ArrayList<>((int) total);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(new Coordinate(x, y, z));
                }
            }
        }

        AreaPreviewStyleSettings areaStyle = NodeEditorInteractionManager.getInstance().getAreaPreviewStyle();
        PreviewOptions options = createCompletedRegionPreviewOptions()
            .setLineWidth(Math.max(2.2f, areaStyle.getLineWidth()))
            .setOpacity(Math.max(0.3f, areaStyle.getOpacity()));

        if (completedBlocksPreviewId == null) {
            completedBlocksPreviewId = PreviewManager.highlightBlocks(getId().toString(), blocks, options);
        } else {
            PreviewManager.updatePreview(completedBlocksPreviewId, blocks);
            PreviewManager.updatePreviewOptions(completedBlocksPreviewId, options);
        }
    }

    private void clearCompletedBlocksPreview() {
        if (completedBlocksPreviewId != null) {
            PreviewManager.hidePreview(completedBlocksPreviewId);
            completedBlocksPreviewId = null;
        }
    }
}
