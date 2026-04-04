package com.nodecraft.nodesystem.nodes.visualization.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import com.nodecraft.nodesystem.preview.elements.GhostBlockElement;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@NodeInfo(
    id = "visualization.preview.geometry_viewer",
    displayName = "Geometry Viewer",
    description = "Previews geometry and can commit the current result into the world.",
    category = "visualization.preview"
)
public class GeometryViewerNode extends BaseCustomUINode {

    @NodeProperty(displayName = "Preview Color", category = "Display", order = 1)
    private String previewColor = "#4CAF50";

    @NodeProperty(displayName = "Transparency", category = "Display", order = 2)
    private float transparency = 0.4f;

    @NodeProperty(displayName = "Show Outline", category = "Display", order = 3)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Block Type", category = "Placement", order = 4)
    private String blockType = "minecraft:stone";

    @NodeProperty(displayName = "Preview Enabled", category = "Display", order = 5)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Preview Backend", category = "Display", order = 6)
    private PreviewBackend previewBackend = PreviewBackend.TRACKED_WORLD;

    @NodeProperty(displayName = "Solid Geometry", category = "Display", order = 7)
    private boolean previewSolidGeometry = true;

    @NodeProperty(displayName = "Placed", category = "State", order = 8)
    private boolean placed = false;

    @NodeProperty(displayName = "Placement Mode", category = "Placement", order = 9)
    private PlacementMode placementMode = PlacementMode.OVERWRITE;

    @NodeProperty(displayName = "Async Placement", category = "Placement", order = 10)
    private boolean useAsyncBake = true;

    @NodeProperty(displayName = "Record Undo", category = "Placement", order = 11)
    private boolean recordUndo = true;

    private boolean placementRequested = false;
    private boolean placementPendingLogged = false;
    private boolean trackedPreviewDeferredLogged = false;
    private int lastBlockCount = 0;
    private String statusMessage = "Waiting for input...";

    private int cachedGeometrySignature = 0;
    private float cachedTransparency = -1f;
    private String cachedColor = null;
    private String cachedBlockType = null;
    private PreviewBackend cachedPreviewBackend = null;

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_COLOR_ID = "input_color";
    private static final String INPUT_TRANSPARENCY_ID = "input_transparency";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_PLACED_ID = "output_placed";
    private static final String OUTPUT_PREVIEW_TRACKING_ID = "output_preview_tracking_id";

    private transient ImString colorBuffer = new ImString(16);
    private transient ImString blockTypeBuffer = new ImString(128);
    private transient boolean colorNeedsSync = true;
    private transient boolean blockTypeNeedsSync = true;

    public GeometryViewerNode() {
        super(UUID.randomUUID(), "visualization.preview.geometry_viewer");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Geometry", "Geometry block list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry Input", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Preview or placement block type", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COLOR_ID, "Preview Color", "Hex preview color", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TRANSPARENCY_ID, "Transparency", "Preview transparency", NodeDataType.FLOAT, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Forwarded block list", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Block Count", "Block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLACED_ID, "Is Placed", "Whether build has been committed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_TRACKING_ID, "Preview Tracking ID", "Tracked-world preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews geometry and can commit the current result into the world.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object blocksObj = inputValues.get(INPUT_BLOCKS_ID);
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object boxGeometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object cylinderGeometryObj = inputValues.get(INPUT_CYLINDER_GEOMETRY_ID);
        Object sphereGeometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        Object torusGeometryObj = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        Object blockTypeObj = inputValues.get(INPUT_BLOCK_TYPE_ID);
        Object colorObj = inputValues.get(INPUT_COLOR_ID);
        Object transparencyObj = inputValues.get(INPUT_TRANSPARENCY_ID);

        String color = (colorObj instanceof String value) ? value : previewColor;
        float trans = (transparencyObj instanceof Number value)
                ? Math.max(0f, Math.min(1f, value.floatValue()))
                : transparency;
        String effectiveBlockType = (blockTypeObj instanceof String value) ? value : blockType;
        boolean hasWorldContext = context != null && context.getWorld() != null;

        BlockPosList blocksList = resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
        int blockCount = blocksList == null ? 0 : blocksList.size();
        lastBlockCount = blockCount;
        NodeCraft.LOGGER.debug(
                "GeometryViewerNode[{}] processNode: previewEnabled={}, backend={}, blockCount={}, contextWorldPresent={}",
                getId(), previewEnabled, previewBackend, blockCount, context != null && context.getWorld() != null
        );

        int geometrySignature = computeGeometrySignature(blocksList);
        boolean previewDirty = geometrySignature != cachedGeometrySignature
                || trans != cachedTransparency
                || !Objects.equals(color, cachedColor)
                || !Objects.equals(effectiveBlockType, cachedBlockType)
                || cachedPreviewBackend != previewBackend;

        if (previewEnabled && blocksList != null && !blocksList.isEmpty()) {
            if (previewDirty) {
                placed = false;

                if (previewBackend == PreviewBackend.TRACKED_WORLD) {
                    if (refreshTrackedPreview(context, blocksList, effectiveBlockType)) {
                        cachePreviewState(geometrySignature, trans, color, effectiveBlockType);
                        trackedPreviewDeferredLogged = false;
                    } else {
                        cachedPreviewBackend = null;
                    }
                } else {
                    trackedPreviewDeferredLogged = false;
                    refreshGhostPreview(context, blocksList, effectiveBlockType, trans);
                    cachePreviewState(geometrySignature, trans, color, effectiveBlockType);
                    statusMessage = "Previewing " + blockCount + " blocks";
                }
            } else if (previewBackend == PreviewBackend.TRACKED_WORLD && hasWorldContext) {
                statusMessage = "Previewing "
                        + TrackedPreviewPlacementService.getInstance().getTrackedCount(context.getWorld(), getId().toString())
                        + " blocks (tracked)";
            } else if (previewBackend == PreviewBackend.TRACKED_WORLD) {
                statusMessage = "Tracked preview waiting for execution context";
            }
        } else {
            trackedPreviewDeferredLogged = false;
            clearAllPreviewState(context);
            statusMessage = previewEnabled ? "Waiting for input..." : "Preview disabled";
        }

        if (placementRequested && blocksList != null && !blocksList.isEmpty()) {
            if (!hasWorldContext) {
                statusMessage = "Placement queued, waiting for execution context";
                if (!placementPendingLogged) {
                    NodeCraft.LOGGER.info(
                            "GeometryViewerNode[{}] placement still pending: backend={}, blockCount={}, blockType={}, contextWorldPresent=false",
                            getId(), previewBackend, blockCount, effectiveBlockType
                    );
                    placementPendingLogged = true;
                }
            } else {
                placementRequested = false;
                placementPendingLogged = false;
                NodeCraft.LOGGER.info(
                        "GeometryViewerNode[{}] placement requested: backend={}, blockCount={}, blockType={}",
                        getId(), previewBackend, blockCount, effectiveBlockType
                );
                if (previewBackend == PreviewBackend.TRACKED_WORLD) {
                boolean committed = TrackedPreviewPlacementService.getInstance().commitTrackedPreview(context.getWorld(), getId().toString());
                placed = committed;
                statusMessage = committed ? "Build committed: " + blockCount + " blocks" : "No tracked preview to commit";
                NodeCraft.LOGGER.info(
                        "GeometryViewerNode[{}] tracked preview commit result: committed={}, trackedCountAfter={}",
                        getId(),
                        committed,
                        TrackedPreviewPlacementService.getInstance().getTrackedCount(context.getWorld(), getId().toString())
                );
                } else {
                    BlockState targetState = resolveBlockState(effectiveBlockType);
                    if (targetState == null) {
                        statusMessage = "Invalid block type: " + effectiveBlockType;
                        NodeCraft.LOGGER.warn("GeometryViewerNode[{}] invalid block type for placement: {}", getId(), effectiveBlockType);
                    } else {
                        placeBlocks(context, blocksList, targetState);
                    }
                }
            }
        }
        if (!placementRequested) {
            placementPendingLogged = false;
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocksList);
        outputValues.put(OUTPUT_COUNT_ID, blockCount);
        outputValues.put(OUTPUT_PLACED_ID, placed);
        outputValues.put(OUTPUT_PREVIEW_TRACKING_ID, getId().toString());
    }

    private void cachePreviewState(int geometrySignature, float trans, String color, String effectiveBlockType) {
        cachedGeometrySignature = geometrySignature;
        cachedTransparency = trans;
        cachedColor = color;
        cachedBlockType = effectiveBlockType;
        cachedPreviewBackend = previewBackend;
    }

    private void refreshGhostPreview(@Nullable ExecutionContext context,
                                     BlockPosList blocksList,
                                     String effectiveBlockType,
                                     float trans) {
        if (context != null && context.getWorld() != null) {
            TrackedPreviewPlacementService.getInstance().clearTrackedPreview(context.getWorld(), getId().toString());
        }

        PreviewManager.hideNodePreviews(getId().toString());
        List<GhostBlockElement.BlockPlacement> placements = new ArrayList<>();
        for (BlockPos pos : blocksList) {
            placements.add(new GhostBlockElement.BlockPlacement(
                    new Vec3d(pos.getX(), pos.getY(), pos.getZ()),
                    effectiveBlockType,
                    trans
            ));
        }

        PreviewOptions options = new PreviewOptions()
                .ghostBlockMode()
                .setOpacity(trans);
        PreviewManager.showGhostBlockPlacements(getId().toString(), placements, options);
        NodeCraft.LOGGER.debug(
                "GeometryViewerNode[{}] ghost preview refreshed: placements={}, blockType={}, transparency={}",
                getId(), placements.size(), effectiveBlockType, trans
        );
    }

    private boolean refreshTrackedPreview(@Nullable ExecutionContext context,
                                          BlockPosList blocksList,
                                          String effectiveBlockType) {
        PreviewManager.hideNodePreviews(getId().toString());

        if (context == null || context.getWorld() == null) {
            statusMessage = "Tracked preview waiting for execution context";
            if (!trackedPreviewDeferredLogged) {
                NodeCraft.LOGGER.info("GeometryViewerNode[{}] tracked preview deferred: missing world context", getId());
                trackedPreviewDeferredLogged = true;
            }
            return false;
        }
        trackedPreviewDeferredLogged = false;

        BlockState trackedState = resolveBlockState(effectiveBlockType);
        if (trackedState == null) {
            statusMessage = "Invalid block type: " + effectiveBlockType;
            NodeCraft.LOGGER.warn("GeometryViewerNode[{}] tracked preview skipped: invalid block type {}", getId(), effectiveBlockType);
            return false;
        }

        int trackedCount = TrackedPreviewPlacementService.getInstance().updateTrackedPreview(
                context.getWorld(),
                getId().toString(),
                new ArrayList<>(blocksList.getPositions()),
                trackedState,
                placementMode
        );
        statusMessage = "Previewing " + trackedCount + " blocks (tracked)";
        NodeCraft.LOGGER.info(
                "GeometryViewerNode[{}] tracked preview refreshed: requested={}, placed={}, blockType={}, placementMode={}",
                getId(), blocksList.size(), trackedCount, effectiveBlockType, placementMode
        );
        return true;
    }

    private void clearAllPreviewState(@Nullable ExecutionContext context) {
        trackedPreviewDeferredLogged = false;
        cachedGeometrySignature = 0;
        cachedTransparency = -1f;
        cachedColor = null;
        cachedBlockType = null;
        cachedPreviewBackend = null;
        PreviewManager.hideNodePreviews(getId().toString());
        if (context != null && context.getWorld() != null) {
            int cleared = TrackedPreviewPlacementService.getInstance().clearTrackedPreview(context.getWorld(), getId().toString());
            if (cleared > 0) {
                NodeCraft.LOGGER.debug("GeometryViewerNode[{}] cleared {} tracked preview blocks", getId(), cleared);
            }
        }
    }

    private void placeBlocks(ExecutionContext context, BlockPosList blocksList, BlockState targetState) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : blocksList) {
            positions.add(pos.toImmutable());
        }

        if (useAsyncBake) {
            BakePlacementService.getInstance().enqueue(
                    context.getWorld(),
                    positions,
                    targetState,
                    placementMode,
                    recordUndo,
                    1000,
                    null
            );
            statusMessage = "Queued placement task (" + positions.size() + " blocks, async)";
            placed = true;
            NodeCraft.LOGGER.info("GeometryViewerNode[{}] queued async placement for {} blocks", getId(), positions.size());
            return;
        }

        int successCount = 0;
        for (BlockPos pos : positions) {
            if (placementMode == PlacementMode.INCREMENTAL && !context.getWorld().isAir(pos)) {
                continue;
            }
            if (context.getWorld().setBlockState(pos, targetState, Block.NOTIFY_ALL)) {
                successCount++;
            }
        }

        placed = true;
        statusMessage = "Placed " + successCount + "/" + positions.size() + " blocks";
        NodeCraft.LOGGER.info(
                "GeometryViewerNode[{}] completed direct placement: placed={}, requested={}, mode={}",
                getId(), successCount, positions.size(), placementMode
        );
    }

    private BlockPosList resolveBlocks(Object blocksObj,
                                       Object geometryObj,
                                       Object boxGeometryObj,
                                       Object cylinderGeometryObj,
                                       Object sphereGeometryObj,
                                       Object torusGeometryObj) {
        return GeometryVoxelizer.resolveBlocks(
                blocksObj,
                geometryObj,
                boxGeometryObj,
                cylinderGeometryObj,
                sphereGeometryObj,
                torusGeometryObj,
                previewSolidGeometry
        );
    }

    private int computeGeometrySignature(BlockPosList blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }

        int hash = blocks.size();
        int i = 0;
        for (BlockPos pos : blocks) {
            if (pos != null) {
                hash = 31 * hash + pos.hashCode();
            }
            if (++i >= 5) {
                break;
            }
        }
        return hash;
    }

    private BlockState resolveBlockState(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        try {
            Identifier id = Identifier.of(blockId);
            var block = Registries.BLOCK.get(id);
            return block != null ? block.getDefaultState() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        for (int i = 0; i < 5; i++) {
            height += ImGui.getFrameHeight();
            height += getSmallPadding();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 220f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;
            float availableWidth = layout.getAvailableContentWidth(width);

            layout.addVerticalSpacing(getMediumPadding());

            int statusColor = placed ? 0xFF44DD44 : (previewEnabled ? 0xFF44AADD : 0xFF888888);
            ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
            ImGui.text(statusMessage);
            ImGui.popStyleColor();
            layout.addVerticalSpacing(getSmallPadding());

            ImGui.text("Blocks: " + lastBlockCount);
            layout.addVerticalSpacing(getSmallPadding());

            ensureBlockTypeBuffer();
            layout.pushFramePadding(4.0f, 3.0f);
            layout.setItemWidth(availableWidth / zoom);
            if (ImGui.inputTextWithHint("##gv_block_type", "minecraft:stone", blockTypeBuffer)) {
                setBlockType(blockTypeBuffer.get());
                changed = true;
            }
            layout.popItemWidth();
            layout.popStyleVar();
            layout.addVerticalSpacing(getSmallPadding());

            ImBoolean previewEnabledValue = new ImBoolean(previewEnabled);
            if (ImGui.checkbox("Preview##gv_preview_enabled", previewEnabledValue)) {
                setPreviewEnabled(previewEnabledValue.get());
                changed = true;
            }
            layout.addVerticalSpacing(getSmallPadding());

            if (ImGui.beginCombo("##gv_preview_backend", previewBackend == PreviewBackend.TRACKED_WORLD ? "Tracked World" : "Ghost")) {
                if (ImGui.selectable("Ghost", previewBackend == PreviewBackend.GHOST)) {
                    setPreviewBackend(PreviewBackend.GHOST);
                    changed = true;
                }
                if (ImGui.selectable("Tracked World", previewBackend == PreviewBackend.TRACKED_WORLD)) {
                    setPreviewBackend(PreviewBackend.TRACKED_WORLD);
                    changed = true;
                }
                ImGui.endCombo();
            }
            layout.addVerticalSpacing(getSmallPadding());

            ImBoolean solidValue = new ImBoolean(previewSolidGeometry);
            if (ImGui.checkbox("Solid Geometry##gv_solid", solidValue)) {
                setPreviewSolidGeometry(solidValue.get());
                changed = true;
            }
            layout.addVerticalSpacing(getSmallPadding());

            if (placed) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFF44DD44);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF55EE55);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF33CC33);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0xFFDD8844);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFFEE9955);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFFCC7733);
            }

            String buttonText = previewBackend == PreviewBackend.TRACKED_WORLD
                    ? (placed ? "Committed" : "Finish Build")
                    : (placed ? "Placed" : "Place In World");
            if (ImGui.button(buttonText, availableWidth, 0)) {
                placementRequested = true;
                placed = false;
                changed = true;
            }
            ImGui.popStyleColor(3);

            layout.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private void ensureColorBuffer() {
        if (colorBuffer == null) {
            colorBuffer = new ImString(16);
        }
        if (colorNeedsSync) {
            colorBuffer.set(previewColor != null ? previewColor : "#4CAF50");
            colorNeedsSync = false;
        }
    }

    private void ensureBlockTypeBuffer() {
        if (blockTypeBuffer == null) {
            blockTypeBuffer = new ImString(128);
        }
        if (blockTypeNeedsSync) {
            blockTypeBuffer.set(blockType != null ? blockType : "minecraft:stone");
            blockTypeNeedsSync = false;
        }
    }

    public String getPreviewColor() {
        return previewColor;
    }

    public void setPreviewColor(String value) {
        if (value != null) {
            previewColor = value;
            colorNeedsSync = true;
            markDirty();
        }
    }

    public float getTransparency() {
        return transparency;
    }

    public void setTransparency(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (transparency != clamped) {
            transparency = clamped;
            markDirty();
        }
    }

    public boolean isShowOutline() {
        return showOutline;
    }

    public void setShowOutline(boolean value) {
        if (showOutline != value) {
            showOutline = value;
            markDirty();
        }
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String value) {
        if (value != null) {
            blockType = value;
            blockTypeNeedsSync = true;
            markDirty();
        }
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(boolean value) {
        if (previewEnabled != value) {
            previewEnabled = value;
            markDirty();
        }
    }

    public PreviewBackend getPreviewBackend() {
        return previewBackend;
    }

    public void setPreviewBackend(PreviewBackend value) {
        if (value != null && previewBackend != value) {
            previewBackend = value;
            markDirty();
        }
    }

    public boolean isPreviewSolidGeometry() {
        return previewSolidGeometry;
    }

    public void setPreviewSolidGeometry(boolean value) {
        if (previewSolidGeometry != value) {
            previewSolidGeometry = value;
            markDirty();
        }
    }

    public boolean isPlaced() {
        return placed;
    }

    public PlacementMode getPlacementMode() {
        return placementMode;
    }

    public void setPlacementMode(PlacementMode value) {
        if (value != null && placementMode != value) {
            placementMode = value;
            markDirty();
        }
    }

    public boolean isUseAsyncBake() {
        return useAsyncBake;
    }

    public void setUseAsyncBake(boolean value) {
        if (useAsyncBake != value) {
            useAsyncBake = value;
            markDirty();
        }
    }

    public boolean isRecordUndo() {
        return recordUndo;
    }

    public void setRecordUndo(boolean value) {
        if (recordUndo != value) {
            recordUndo = value;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("previewColor", previewColor);
        state.put("transparency", transparency);
        state.put("showOutline", showOutline);
        state.put("blockType", blockType);
        state.put("previewEnabled", previewEnabled);
        state.put("previewBackend", previewBackend.name());
        state.put("previewSolidGeometry", previewSolidGeometry);
        state.put("placed", placed);
        state.put("placementMode", placementMode.name());
        state.put("useAsyncBake", useAsyncBake);
        state.put("recordUndo", recordUndo);
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("previewColor") instanceof String value) {
            setPreviewColor(value);
        }
        if (map.get("transparency") instanceof Number value) {
            setTransparency(value.floatValue());
        }
        if (map.get("showOutline") instanceof Boolean value) {
            setShowOutline(value);
        }
        if (map.get("blockType") instanceof String value) {
            setBlockType(value);
        }
        if (map.get("previewEnabled") instanceof Boolean value) {
            setPreviewEnabled(value);
        }
        if (map.get("previewBackend") instanceof String value) {
            try {
                setPreviewBackend(PreviewBackend.valueOf(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("previewSolidGeometry") instanceof Boolean value) {
            setPreviewSolidGeometry(value);
        }
        if (map.get("placed") instanceof Boolean value) {
            placed = value;
        }
        if (map.get("placementMode") instanceof String value) {
            try {
                setPlacementMode(PlacementMode.valueOf(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("useAsyncBake") instanceof Boolean value) {
            setUseAsyncBake(value);
        }
        if (map.get("recordUndo") instanceof Boolean value) {
            setRecordUndo(value);
        }
    }
}
