package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.gui.components.customui.BlockPaletteWidget;
import com.nodecraft.gui.components.customui.CustomUIPropertyHistory;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import imgui.ImGui;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a typed {@link BlockPaletteData} from block ids and optional weights.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.create_block_palette",
    displayName = "Create Block Palette",
    description = "Builds a BLOCK_PALETTE from STRING_LIST block ids and optional DOUBLE_LIST weights",
    category = "material.basic_assignment",
    order = 0
)
public class CreateBlockPaletteNode extends BaseCustomUINode {

    private static final String INPUT_BLOCK_IDS_ID = "input_block_ids";
    private static final String INPUT_WEIGHTS_ID = "input_weights";
    private static final String INPUT_BLOCK_A_ID = "input_block_a";
    private static final String INPUT_BLOCK_B_ID = "input_block_b";
    private static final String INPUT_BLOCK_C_ID = "input_block_c";
    private static final String INPUT_BLOCK_D_ID = "input_block_d";

    private static final String OUTPUT_PALETTE_ID = "output_palette";
    private static final String OUTPUT_SIZE_ID = "output_size";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private List<String> draftBlockIds = new ArrayList<>();
    private List<Double> draftWeights = new ArrayList<>();

    /** Undo baseline captured when a weight field becomes active; cleared on commit/cancel. */
    private transient Object weightEditBeforeState;

    public CreateBlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.create_block_palette");

        addInputPort(new BasePort(INPUT_BLOCK_IDS_ID, "Block IDs", "Ordered block id STRING_LIST", NodeDataType.STRING_LIST, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional weights aligned with Block IDs", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_A_ID, "Block A", "Optional first block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_B_ID, "Block B", "Optional second block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_C_ID, "Block C", "Optional third block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_D_ID, "Block D", "Optional fourth block type", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PALETTE_ID, "Palette", "Typed block palette", NodeDataType.BLOCK_PALETTE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Number of palette entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a BLOCK_PALETTE from STRING_LIST block ids and optional DOUBLE_LIST weights.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!hasAnyPalettePortDriven()) {
            processFromDraft();
            return;
        }

        BasicAssignmentUtils.ParseResult<String> blockIdsResult =
            BasicAssignmentUtils.parseStringList(inputValues.get(INPUT_BLOCK_IDS_ID), "Block IDs");
        if (!blockIdsResult.valid()) {
            emitFail(blockIdsResult.error());
            return;
        }

        List<String> blockIds = new ArrayList<>(blockIdsResult.values());
        if (!appendBlock(blockIds, INPUT_BLOCK_A_ID, "Block A")
            || !appendBlock(blockIds, INPUT_BLOCK_B_ID, "Block B")
            || !appendBlock(blockIds, INPUT_BLOCK_C_ID, "Block C")
            || !appendBlock(blockIds, INPUT_BLOCK_D_ID, "Block D")) {
            return;
        }

        if (blockIds.size() > GenerationLimits.MAX_BLOCK_PALETTE_ENTRIES) {
            emitFail("Palette exceeds MAX_BLOCK_PALETTE_ENTRIES");
            return;
        }

        List<Double> weights = null;
        if (isDriven(INPUT_WEIGHTS_ID)) {
            BasicAssignmentUtils.ParseResult<Double> weightsResult =
                BasicAssignmentUtils.parseDoubleList(inputValues.get(INPUT_WEIGHTS_ID), "Weights");
            if (!weightsResult.valid()) {
                emitFail(weightsResult.error());
                return;
            }
            weights = weightsResult.values();
            BasicAssignmentUtils.Validation weightsOk =
                BasicAssignmentUtils.validateWeights(weights, blockIds.size());
            if (!weightsOk.valid()) {
                emitFail(weightsOk.message());
                return;
            }
        }

        BlockPaletteData palette = BasicAssignmentUtils.buildPalette(blockIds, weights);
        emitOk(palette);
    }

    private void processFromDraft() {
        List<String> blockIds = new ArrayList<>(draftBlockIds.size());
        for (String blockId : draftBlockIds) {
            String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(blockId);
            if (canonical != null) {
                blockIds.add(canonical);
            }
        }
        if (blockIds.isEmpty()) {
            emitOk(BlockPaletteData.empty());
            return;
        }
        if (blockIds.size() > GenerationLimits.MAX_BLOCK_PALETTE_ENTRIES) {
            emitFail("Palette exceeds MAX_BLOCK_PALETTE_ENTRIES");
            return;
        }
        List<Double> weights = draftWeights.isEmpty() ? null : new ArrayList<>(draftWeights);
        if (weights != null) {
            BasicAssignmentUtils.Validation weightsOk =
                BasicAssignmentUtils.validateWeights(weights, blockIds.size());
            if (!weightsOk.valid()) {
                emitFail(weightsOk.message());
                return;
            }
        }
        emitOk(BasicAssignmentUtils.buildPalette(blockIds, weights));
    }

    private boolean hasAnyPalettePortDriven() {
        return isDriven(INPUT_BLOCK_IDS_ID)
            || isDriven(INPUT_WEIGHTS_ID)
            || isDriven(INPUT_BLOCK_A_ID)
            || isDriven(INPUT_BLOCK_B_ID)
            || isDriven(INPUT_BLOCK_C_ID)
            || isDriven(INPUT_BLOCK_D_ID);
    }

    private BlockPaletteData resolvePreviewPalette() {
        if (!hasAnyPalettePortDriven()) {
            return draftPaletteData();
        }
        BasicAssignmentUtils.ParseResult<String> blockIdsResult =
            BasicAssignmentUtils.parseStringList(inputValues.get(INPUT_BLOCK_IDS_ID), "Block IDs");
        if (!blockIdsResult.valid()) {
            return BlockPaletteData.empty();
        }
        List<String> blockIds = new ArrayList<>(blockIdsResult.values());
        appendBlockPreview(blockIds, INPUT_BLOCK_A_ID);
        appendBlockPreview(blockIds, INPUT_BLOCK_B_ID);
        appendBlockPreview(blockIds, INPUT_BLOCK_C_ID);
        appendBlockPreview(blockIds, INPUT_BLOCK_D_ID);
        List<Double> weights = null;
        if (isDriven(INPUT_WEIGHTS_ID)) {
            BasicAssignmentUtils.ParseResult<Double> weightsResult =
                BasicAssignmentUtils.parseDoubleList(inputValues.get(INPUT_WEIGHTS_ID), "Weights");
            if (weightsResult.valid()) {
                weights = weightsResult.values();
            }
        }
        return BasicAssignmentUtils.buildPalette(blockIds, weights);
    }

    private void appendBlockPreview(List<String> blockIds, String portId) {
        if (!isDriven(portId)) {
            return;
        }
        String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(
            inputValues.get(portId) instanceof String text ? text : null);
        if (canonical != null) {
            blockIds.add(canonical);
        }
    }

    private BlockPaletteData draftPaletteData() {
        List<BlockPaletteEntry> entries = new ArrayList<>(draftBlockIds.size());
        for (int i = 0; i < draftBlockIds.size(); i++) {
            String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(draftBlockIds.get(i));
            if (canonical == null) {
                continue;
            }
            double weight = i < draftWeights.size() && draftWeights.get(i) != null
                ? draftWeights.get(i)
                : 1.0d;
            entries.add(new BlockPaletteEntry(canonical, weight));
        }
        return new BlockPaletteData(entries);
    }

    private void setDraftEntries(List<BlockPaletteEntry> entries, boolean invalidateLayout) {
        draftBlockIds = new ArrayList<>(entries.size());
        draftWeights = new ArrayList<>(entries.size());
        for (BlockPaletteEntry entry : entries) {
            draftBlockIds.add(entry.blockId());
            draftWeights.add(entry.weight());
        }
        if (invalidateLayout) {
            markDirty();
        }
    }

    private void previewDraftEntries(List<BlockPaletteEntry> entries) {
        setDraftEntries(entries, true);
    }

    /**
     * Applies a single-row weight edit through the same transaction used by the custom UI widget.
     */
    public void applyDraftWeightEdit(int rowIndex, double newWeight) {
        List<BlockPaletteEntry> entries = new ArrayList<>(draftPaletteData().entries());
        if (rowIndex < 0 || rowIndex >= entries.size()) {
            return;
        }
        BlockPaletteEntry current = entries.get(rowIndex);
        beginWeightEdit();
        entries.set(rowIndex, new BlockPaletteEntry(current.blockId(), newWeight));
        previewDraftEntries(entries);
        commitWeightEdit();
    }

    private void beginWeightEdit() {
        if (weightEditBeforeState == null) {
            weightEditBeforeState = getNodeState();
        }
    }

    private void commitWeightEdit() {
        Object before = weightEditBeforeState;
        if (before == null) {
            return;
        }
        Object after = getNodeState();
        weightEditBeforeState = null;
        if (!java.util.Objects.equals(before, after)) {
            CustomUIPropertyHistory.recordNodeStateChange(getId(), before, after, "Edit draft palette weight");
        }
    }

    private void applyDraftEntries(List<BlockPaletteEntry> entries, String undoLabel) {
        Object before = weightEditBeforeState != null ? weightEditBeforeState : getNodeState();
        weightEditBeforeState = null;
        setDraftEntries(entries, true);
        Object after = getNodeState();
        if (!java.util.Objects.equals(before, after)) {
            CustomUIPropertyHistory.recordNodeStateChange(getId(), before, after, undoLabel);
        }
    }

    /** Package-private for transaction tests. */
    Object pendingWeightEditBaseline() {
        return weightEditBeforeState;
    }

    private boolean appendBlock(List<String> blockIds, String portId, String label) {
        if (!isDriven(portId)) {
            return true;
        }
        String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(
            inputValues.get(portId) instanceof String text ? text : null);
        if (canonical == null) {
            emitFail(label + " connected but invalid");
            return false;
        }
        blockIds.add(canonical);
        return true;
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isDriven(this, portId);
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.createPaletteFailResult(message));
    }

    private void emitOk(BlockPaletteData palette) {
        outputValues.putAll(BasicAssignmentUtils.createPaletteOkResult(palette));
    }

    @Override
    protected float calculateUIHeight() {
        BlockPaletteData palette = resolvePreviewPalette();
        return BlockPaletteWidget.preferredHeight(palette, getMediumPadding());
    }

    @Override
    protected float calculateMinUIWidth() {
        return 168.0f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(96.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            ImGui.setCursorPosX(ImGui.getCursorPosX() + edgeMargin);

            if (hasAnyPalettePortDriven()) {
                ImGui.textDisabled("Port-driven (read-only preview)");
                return BlockPaletteWidget.renderReadOnly(resolvePreviewPalette(), availableWidth, zoom);
            }
            return BlockPaletteWidget.renderEditable(
                this::draftPaletteData,
                new BlockPaletteWidget.PaletteEditSink() {
                    @Override
                    public void beginWeightEdit() {
                        CreateBlockPaletteNode.this.beginWeightEdit();
                    }

                    @Override
                    public void preview(List<BlockPaletteEntry> entries) {
                        previewDraftEntries(entries);
                    }

                    @Override
                    public void commitWeightEdit() {
                        CreateBlockPaletteNode.this.commitWeightEdit();
                    }

                    @Override
                    public void commit(List<BlockPaletteEntry> entries) {
                        applyDraftEntries(entries, "Edit draft palette");
                    }
                },
                availableWidth,
                zoom);
        });
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("draftBlockIds", new ArrayList<>(draftBlockIds));
        state.put("draftWeights", new ArrayList<>(draftWeights));
        return state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("draftBlockIds") instanceof List<?> ids) {
            draftBlockIds = new ArrayList<>();
            for (Object id : ids) {
                if (id instanceof String text) {
                    draftBlockIds.add(text);
                }
            }
        }
        if (map.get("draftWeights") instanceof List<?> weights) {
            draftWeights = new ArrayList<>();
            for (Object weight : weights) {
                if (weight instanceof Number number) {
                    draftWeights.add(number.doubleValue());
                }
            }
        }
        markDirty();
    }
}
