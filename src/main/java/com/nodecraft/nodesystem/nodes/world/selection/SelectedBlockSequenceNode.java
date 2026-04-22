package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.interaction.IBlockPickerCallback;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.minecraft.client.MinecraftClientController;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.protocol.PreviewCurvePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPayloadAdapters;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "world.selection.selected_block_sequence",
    displayName = "Selected Block Sequence",
    description = "Collects multiple picked blocks in click order and outputs an ordered block sequence",
    category = "world.selection",
    order = 6
)
public class SelectedBlockSequenceNode extends BaseCustomUINode implements IBlockPickerCallback {

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_POINT_LIST_ID = "output_point_list";
    private static final String OUTPUT_LINE_ID = "output_line";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_CENTERS_ID = "output_centers";
    private static final String OUTPUT_FIRST_ID = "output_first";
    private static final String OUTPUT_LAST_ID = "output_last";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_SEGMENT_COUNT_ID = "output_segment_count";
    private static final String OUTPUT_IS_CLOSED_ID = "output_is_closed";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private final List<Coordinate> pickedBlocks = new ArrayList<>();

    private volatile boolean pickingActive = false;
    private volatile boolean pendingRepick = false;
    private volatile long lastPathPreviewFailureNoticeAt = 0L;
    private volatile String pathPreviewId = null;

    @NodeProperty(displayName = "Pick Distance", category = "Picking", order = 1,
            description = "Maximum distance used for each block pick request.")
    private float maxDistance = 100.0f;

    @NodeProperty(displayName = "Include Fluids", category = "Picking", order = 2,
            description = "Whether fluid blocks can be picked.")
    private boolean includeFluids = false;

    @NodeProperty(displayName = "Allow Duplicates", category = "Sequence", order = 3,
            description = "Whether the same block can appear multiple times in the picked order.")
    private boolean allowDuplicates = false;

    @NodeProperty(displayName = "Auto Preview Path", category = "Preview", order = 4,
            description = "Automatically preview the ordered path while picking.")
    private boolean autoPreviewPath = true;

    @NodeProperty(displayName = "Close Path", category = "Preview", order = 5,
            description = "Treat the sequence as closed by repeating the first point at the end.")
    private boolean closePath = false;

    @NodeProperty(displayName = "Preview Path Color", category = "Preview", order = 6,
            description = "Hex color used for the automatic path preview.")
    private String previewPathColor = "#FFD933";

    public SelectedBlockSequenceNode() {
        super(UUID.randomUUID(), "world.selection.selected_block_sequence");

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Ordered list of picked block coordinates", NodeDataType.COORDINATE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Ordered list of picked block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINT_LIST_ID, "Point List", "Ordered geometric point list derived from the picked blocks", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_LINE_ID, "Line", "Line built directly from the ordered point list when exactly 2 points exist", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Polyline built directly from the ordered point list when at least 2 points exist", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_CENTERS_ID, "Centers", "Ordered list of block center points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_ID, "First", "First picked block in the sequence", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_LAST_ID, "Last", "Last picked block in the sequence", NodeDataType.COORDINATE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of picked blocks in the sequence", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SEGMENT_COUNT_ID, "Segment Count", "Number of path segments implied by the current ordered sequence", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_IS_CLOSED_ID, "Is Closed", "Whether the ordered sequence is currently treated as a closed path", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the sequence currently contains at least one picked block", NodeDataType.BOOLEAN, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Collects multiple picked blocks in click order and outputs an ordered block sequence";
    }

    @Override
    public String getDisplayName() {
        return "Selected Block Sequence";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (pickingActive) {
            int pickedCount = snapshotPickedBlocks().size();
            MinecraftClientController.getInstance().showHudMessage(
                "Picking blocks: LMB add, RMB finish (selected " + pickedCount + ")"
            );
        }

        if (pickingActive && pendingRepick) {
            NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
            if (!interactionManager.isPendingBlockPick(getId().toString())) {
                pendingRepick = false;
                requestNextPick();
            }
        }
        updateOutputs();
        updatePathPreview();
    }

    @Override
    public void onBlockPicked(Coordinate position, String blockId, com.nodecraft.nodesystem.util.BlockStateData blockStateData) {
        if (position == null) {
            return;
        }

        synchronized (pickedBlocks) {
            if (allowDuplicates || !pickedBlocks.contains(position)) {
                pickedBlocks.add(position);
            }
        }

        updateOutputs();
        markDirty();

        if (pickingActive) {
            pendingRepick = true;
        }

        updatePathPreview();
    }

    @Override
    public void onPickingCancelled() {
        pickingActive = false;
        pendingRepick = false;
        MinecraftClientController.getInstance().showHudMessage("Block sequence picking finished");
        updatePathPreview();
        markDirty();
    }

    @Override
    public BlockPickingConfig getPickingConfig() {
        BlockPickingConfig config = new BlockPickingConfig();
        config.setMaxDistance(maxDistance);
        config.setIncludeFluids(includeFluids);
        return config;
    }

    public void clearSequence() {
        synchronized (pickedBlocks) {
            pickedBlocks.clear();
        }
        updateOutputs();
        updatePathPreview();
        markDirty();
    }

    public void removeLast() {
        synchronized (pickedBlocks) {
            if (pickedBlocks.isEmpty()) {
                return;
            }
            pickedBlocks.remove(pickedBlocks.size() - 1);
        }
        updateOutputs();
        updatePathPreview();
        markDirty();
    }

    private void startPicking() {
        pickingActive = true;
        pendingRepick = false;
        requestNextPick();
        MinecraftClientController.getInstance().showHudMessage("Picking blocks: LMB add, RMB finish");
        updatePathPreview();
        markDirty();
    }

    private void stopPicking() {
        pickingActive = false;
        pendingRepick = false;
        NodeEditorInteractionManager.getInstance().cancelBlockPick();
        MinecraftClientController.getInstance().showHudMessage("Block sequence picking finished");
        updatePathPreview();
        markDirty();
    }

    private void requestNextPick() {
        NodeEditorInteractionManager.getInstance().requestBlockPick(getId().toString(), this);
    }

    private void updateOutputs() {
        List<Coordinate> snapshot = snapshotPickedBlocks();
        List<Coordinate> coordinates = new ArrayList<>(snapshot);
        BlockPosList blocks = new BlockPosList();
        List<PointData> pointList = new ArrayList<>();
        List<Vec3d> polylinePoints = new ArrayList<>();
        List<Vector3d> centers = new ArrayList<>();

        for (Coordinate coordinate : snapshot) {
            appendOutputs(coordinate, blocks, pointList, polylinePoints, centers);
        }

        if (closePath && snapshot.size() >= 2) {
            Coordinate first = snapshot.getFirst();
            Coordinate last = snapshot.getLast();
            if (!first.equals(last)) {
                coordinates.add(first);
                appendOutputs(first, blocks, pointList, polylinePoints, centers);
            }
        }

        LineData line = polylinePoints.size() == 2 ? new LineData(polylinePoints.get(0), polylinePoints.get(1)) : null;
        PolylineData polyline = polylinePoints.size() >= 2 ? new PolylineData(polylinePoints) : null;

        outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_POINT_LIST_ID, pointList);
        outputValues.put(OUTPUT_LINE_ID, line);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_CENTERS_ID, centers);
        outputValues.put(OUTPUT_FIRST_ID, snapshot.isEmpty() ? null : snapshot.getFirst());
        outputValues.put(OUTPUT_LAST_ID, snapshot.isEmpty() ? null : snapshot.getLast());
        outputValues.put(OUTPUT_COUNT_ID, snapshot.size());
        outputValues.put(OUTPUT_SEGMENT_COUNT_ID, Math.max(0, coordinates.size() - 1));
        outputValues.put(OUTPUT_IS_CLOSED_ID, closePath && snapshot.size() >= 2);
        outputValues.put(OUTPUT_VALID_ID, !snapshot.isEmpty());
    }

    private void updatePathPreview() {
        List<Coordinate> snapshot = snapshotPickedBlocks();

        if (!snapshot.isEmpty()) {
            SelectionVisualFeedback.getInstance().showMultiBlockSelection(
                getId().toString(),
                snapshot,
                SelectionVisualFeedback.SelectionState.SELECTED
            );
        } else {
            SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        }

        if (!autoPreviewPath || snapshot.size() < 2) {
            if (pathPreviewId != null) {
                PreviewManager.hidePreview(pathPreviewId);
                pathPreviewId = null;
            }
            return;
        }

        // Use geometric centers so preview path semantics match output polyline data.
        final double previewYOffset = 0.5d;
        List<Vec3d> points = new ArrayList<>();
        for (Coordinate coordinate : snapshot) {
            points.add(new Vec3d(
                coordinate.getX() + 0.5d,
                coordinate.getY() + previewYOffset,
                coordinate.getZ() + 0.5d
            ));
        }
        if (closePath) {
            Coordinate first = snapshot.getFirst();
            Coordinate last = snapshot.getLast();
            if (!first.equals(last)) {
                points.add(new Vec3d(
                    first.getX() + 0.5d,
                    first.getY() + previewYOffset,
                    first.getZ() + 0.5d
                ));
            }
        }

        Color color = Color.fromHex(previewPathColor);
        PreviewOptions options = new PreviewOptions()
            .setColor(color.getRed(), color.getGreen(), color.getBlue())
            .setLineWidth(3.0f)
            .setDuration(30);
        options.showArrows = true;
        options.arrowSize = 0.24f;
        options.renderPriority = 30;

        PolylineData polylineData = new PolylineData(points);
        PreviewCurvePayload curvePayload = PreviewPayloadAdapters.curveFromPolylineData(polylineData, false);
        if (pathPreviewId == null) {
            pathPreviewId = PreviewManager.showCurve(getId().toString(), curvePayload, options);
        } else {
            PreviewManager.updatePreview(pathPreviewId, curvePayload);
            PreviewManager.updatePreviewOptions(pathPreviewId, options);
        }

        if (pathPreviewId == null) {
            long now = System.currentTimeMillis();
            if (now - lastPathPreviewFailureNoticeAt > 2000L) {
                lastPathPreviewFailureNoticeAt = now;
                NodeCraft.LOGGER.warn("SelectedBlockSequenceNode path preview creation failed: nodeId={}, points={}", getId(), points.size());
                MinecraftClientController.getInstance().showHudMessage("Path preview creation failed, please check logs");
            }
        }
    }

    private void appendOutputs(Coordinate coordinate, BlockPosList blocks, List<PointData> pointList, List<Vec3d> polylinePoints, List<Vector3d> centers) {
        blocks.add(new BlockPos(coordinate.getX(), coordinate.getY(), coordinate.getZ()));
        Vec3d centerPos = new Vec3d(
            coordinate.getX() + 0.5d,
            coordinate.getY() + 0.5d,
            coordinate.getZ() + 0.5d
        );
        pointList.add(new PointData(
            centerPos.x,
            centerPos.y,
            centerPos.z
        ));
        polylinePoints.add(centerPos);
        centers.add(new Vector3d(
            centerPos.x,
            centerPos.y,
            centerPos.z
        ));
    }

    private List<Coordinate> snapshotPickedBlocks() {
        synchronized (pickedBlocks) {
            return new ArrayList<>(pickedBlocks);
        }
    }

    @Override
    protected float calculateUIHeight() {
        float frame = ImGui.getFrameHeight();
        float text = ImGui.getTextLineHeightWithSpacing();
        float buttonGap = 0.5f;
        float topMargin = getSmallPadding();
        float bottomMargin = getSmallPadding();

        float height = topMargin;
        height += text;
        height += frame * 3.0f; // Start/Stop + Remove Last + Clear Sequence
        height += buttonGap * 2.0f;
        height += bottomMargin;
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float buttonPadding = 20.0f;
        float labelWidth = Math.max(
                Math.max(ImGui.calcTextSize("Start Picking").x, ImGui.calcTextSize("Stop Picking").x),
                Math.max(ImGui.calcTextSize("Remove Last").x, ImGui.calcTextSize("Clear Sequence").x)
        );
        return Math.max(144.0f, labelWidth + buttonPadding);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;
            float edgeMargin = layout.toPixels(getSmallPadding());
            float buttonWidth = Math.max(0.0f, layout.toPixelsExact(width) - edgeMargin * 2.0f);

            layout.addVerticalSpacing(getSmallPadding());
            float baseCursorX = ImGui.getCursorPosX();

            int count = snapshotPickedBlocks().size();
            String statusText = pickingActive
                ? "Selecting... LMB add / RMB finish (" + count + ")"
                : "Idle (selected " + count + ")";
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            ImGui.text(statusText);
            layout.addVerticalSpacing(0.35f);

            if (!pickingActive) {
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Start Picking##startPicking", buttonWidth, 0)) {
                    startPicking();
                    changed = true;
                }
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.80f, 0.25f, 0.25f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.90f, 0.30f, 0.30f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.70f, 0.15f, 0.15f, 1.0f);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Stop Picking##stopPicking", buttonWidth, 0)) {
                    stopPicking();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            }

            layout.addVerticalSpacing(0.5f);

            boolean hasBlocks = !snapshotPickedBlocks().isEmpty();
            if (hasBlocks) {
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                if (ImGui.button("Remove Last##removeLast", buttonWidth, 0)) {
                    removeLast();
                    changed = true;
                }
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.25f, 0.25f, 0.25f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.25f, 0.25f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.25f, 0.25f, 0.25f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.45f, 0.45f, 0.45f, 1.0f);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.button("Remove Last##removeLastDisabled", buttonWidth, 0);
                ImGui.popStyleColor(4);
            }

            layout.addVerticalSpacing(0.5f);

            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            if (ImGui.button("Clear Sequence##clearSequence", buttonWidth, 0)) {
                clearSequence();
                changed = true;
            }
            layout.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("pickingActive", false);
        state.put("maxDistance", maxDistance);
        state.put("includeFluids", includeFluids);
        state.put("allowDuplicates", allowDuplicates);
        state.put("autoPreviewPath", autoPreviewPath);
        state.put("closePath", closePath);
        state.put("previewPathColor", previewPathColor);

        List<Coordinate> snapshot = snapshotPickedBlocks();
        List<Map<String, Integer>> blocks = new ArrayList<>(snapshot.size());
        for (Coordinate coordinate : snapshot) {
            Map<String, Integer> item = new HashMap<>();
            item.put("x", coordinate.getX());
            item.put("y", coordinate.getY());
            item.put("z", coordinate.getZ());
            blocks.add(item);
        }
        state.put("pickedBlocks", blocks);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        PreviewManager.hideNodePreviews(getId().toString());
        pathPreviewId = null;

        synchronized (pickedBlocks) {
            pickedBlocks.clear();
        }

        if (map.get("maxDistance") instanceof Number number) {
            maxDistance = Math.max(1.0f, Math.min(300.0f, number.floatValue()));
        }
        if (map.get("includeFluids") instanceof Boolean bool) {
            includeFluids = bool;
        }
        if (map.get("allowDuplicates") instanceof Boolean bool) {
            allowDuplicates = bool;
        }
        if (map.get("autoPreviewPath") instanceof Boolean bool) {
            autoPreviewPath = bool;
        }
        if (map.get("closePath") instanceof Boolean bool) {
            closePath = bool;
        }
        if (map.get("previewPathColor") instanceof String value) {
            previewPathColor = value;
        }
        if (map.get("pickedBlocks") instanceof List<?> list) {
            List<Coordinate> restored = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> blockMap) {
                    Object x = blockMap.get("x");
                    Object y = blockMap.get("y");
                    Object z = blockMap.get("z");
                    if (x instanceof Number xNum && y instanceof Number yNum && z instanceof Number zNum) {
                        restored.add(new Coordinate(xNum.intValue(), yNum.intValue(), zNum.intValue()));
                    }
                }
            }
            synchronized (pickedBlocks) {
                pickedBlocks.addAll(restored);
            }
        }

        pickingActive = false;
        pendingRepick = false;
        updateOutputs();
        updatePathPreview();
    }

    public void onNodeRemoved() {
        if (pickingActive) {
            NodeEditorInteractionManager.getInstance().cancelBlockPick();
        }
        PreviewManager.hideNodePreviews(getId().toString());
        pathPreviewId = null;
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
    }
}
