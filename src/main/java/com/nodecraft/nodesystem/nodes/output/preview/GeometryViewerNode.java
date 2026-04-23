package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewBackend;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPayloadAdapters;
import com.nodecraft.nodesystem.preview.protocol.PreviewRequest;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 几何预览：GHOST / TRACKED_WORLD 共用 {@link com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload}，
 * 通过 {@link com.nodecraft.nodesystem.preview.PreviewManager#showPreview} 下发。
 * 不要在此构造渲染器内部类型或已废弃的 placement DTO 作为跨层协议（v1.1 清单）。
 */
@NodeInfo(
    id = "output.preview.geometry_viewer",
    displayName = "Geometry Viewer",
    description = "Previews geometry visually without committing changes to the world.",
    category = "output.preview",
    order = 0
)
public class GeometryViewerNode extends BaseCustomUINode {

    @NodeProperty(displayName = "Preview Color", category = "Display", order = 1)
    private String previewColor = "#4CAF50";

    @NodeProperty(displayName = "Transparency", category = "Display", order = 2)
    private float transparency = 0.4f;

    @NodeProperty(displayName = "Show Outline", category = "Display", order = 3)
    private boolean showOutline = true;

    @NodeProperty(displayName = "Block Type", category = "Preview", order = 4)
    private String blockType = "minecraft:stone";

    @NodeProperty(displayName = "Preview Enabled", category = "Display", order = 5)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Preview Backend", category = "Display", order = 6)
    private PreviewBackend previewBackend = PreviewBackend.GHOST;

    @NodeProperty(displayName = "Solid Geometry", category = "Display", order = 7)
    private boolean previewSolidGeometry = true;

    private volatile int lastBlockCount = 0;
    private volatile String statusMessage = "Waiting for input...";

    private volatile int cachedGeometrySignature = 0;
    private volatile float cachedTransparency = -1f;
    private volatile String cachedColor = null;
    private volatile String cachedBlockType = null;
    private volatile PreviewBackend cachedPreviewBackend = null;

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

    public GeometryViewerNode() {
        super(UUID.randomUUID(), "output.preview.geometry_viewer");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks", "Block list input (already voxelized)", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input (auto-voxelized for preview)", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Preview block type", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COLOR_ID, "Preview Color", "Hex preview color", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TRANSPARENCY_ID, "Transparency", "Preview transparency", NodeDataType.FLOAT, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Forwarded block list", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Block Count", "Block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Previews geometry visually without building into the world.";
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
        // Editor-side port resolution (e.g. NodeOutputResolver.compute without full graph) can feed 0.0f for
        // FLOAT inputs; alternating with graph runs leaves a fully transparent ghost preview and no render logs.
        if (previewBackend == PreviewBackend.GHOST && trans <= 0.01f) {
            trans = transparency;
        }
        String requestedBlockType = (blockTypeObj instanceof String value) ? value : blockType;
        String effectiveBlockType = sanitizeBlockType(requestedBlockType);
        boolean hasWorldContext = context != null && context.getWorld() != null;

        BlockPosList blocksList = resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);
        int blockCount = blocksList == null ? 0 : blocksList.size();
        lastBlockCount = blockCount;

        int geometrySignature = computeGeometrySignature(blocksList);
        boolean previewDirty = geometrySignature != cachedGeometrySignature
            || trans != cachedTransparency
            || !Objects.equals(color, cachedColor)
            || !Objects.equals(effectiveBlockType, cachedBlockType)
            || cachedPreviewBackend != previewBackend;

        if (previewEnabled && blocksList != null && !blocksList.isEmpty()) {
            if (previewDirty) {
                if (refreshPreview(context, blocksList, effectiveBlockType, trans)) {
                    cachePreviewState(geometrySignature, trans, color, effectiveBlockType);
                } else {
                    cachedPreviewBackend = null;
                }
            } else if (hasWorldContext) {
                statusMessage = buildSteadyStateStatus(context);
            } else {
                statusMessage = previewBackend == PreviewBackend.TRACKED_WORLD
                    ? "Preview waiting for execution context"
                    : "Previewing " + blockCount + " ghost blocks";
            }
        } else {
            clearAllPreviewState(context);
            statusMessage = previewEnabled ? "Waiting for input..." : "Preview disabled";
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocksList);
        outputValues.put(OUTPUT_COUNT_ID, blockCount);
    }

    private void cachePreviewState(int geometrySignature, float trans, String color, String effectiveBlockType) {
        cachedGeometrySignature = geometrySignature;
        cachedTransparency = trans;
        cachedColor = color;
        cachedBlockType = effectiveBlockType;
        cachedPreviewBackend = previewBackend;
    }

    private boolean refreshPreview(
        @Nullable ExecutionContext context,
        BlockPosList blocksList,
        String effectiveBlockType,
        float trans
    ) {
        try {
        if (previewBackend == PreviewBackend.TRACKED_WORLD) {
            BlockState trackedState = resolveBlockState(effectiveBlockType);
            if (trackedState == null) {
                statusMessage = "Invalid block type: " + effectiveBlockType;
                NodeCraft.LOGGER.warn("GeometryViewerNode[{}] tracked preview skipped: invalid block type {}", getId(), effectiveBlockType);
                return false;
            }
        }

        PreviewBlocksPayload payload = PreviewPayloadAdapters.fromBlockPosList(blocksList, effectiveBlockType);
        if (payload.getBlocks().isEmpty()) {
            statusMessage = "Waiting for input...";
            return false;
        }

        Color parsedColor = Color.fromHex(cachedColor != null ? cachedColor : previewColor);
        PreviewStyle style = PreviewStyle.forGhostBlocks(
            parsedColor.getRed(),
            parsedColor.getGreen(),
            parsedColor.getBlue(),
            trans,
            showOutline,
            "solid_color",
            2.0f,
            0.1f,
            0
        );

        if (previewBackend == PreviewBackend.GHOST) {
            NodeCraft.LOGGER.info(
                "GeometryViewerNode[{}] ghost request: blocks={}, blockType={}, opacity={}, outline={}",
                getId(),
                payload.getBlocks().size(),
                effectiveBlockType,
                trans,
                showOutline
            );
        }

        String previewId = PreviewManager.showPreview(
            new PreviewRequest(getId().toString(), payload, style, previewBackend, context)
        );
        if (previewId == null) {
            if (previewBackend == PreviewBackend.TRACKED_WORLD) {
                statusMessage = context == null || context.getWorld() == null
                    ? "Preview waiting for execution context"
                    : "Tracked preview failed";
            } else {
                statusMessage = "Ghost preview failed";
                NodeCraft.LOGGER.warn("GeometryViewerNode[{}] ghost preview failed: renderer returned null", getId());
            }
            return false;
        }

        if (previewBackend == PreviewBackend.GHOST) {
            NodeCraft.LOGGER.info("GeometryViewerNode[{}] ghost preview created: previewId={}", getId(), previewId);
            statusMessage = "Previewing " + payload.getBlocks().size() + " ghost blocks";
        } else {
            int trackedCount = context != null && context.getWorld() != null
                ? TrackedPreviewPlacementService.getInstance().getTrackedCount(context.getWorld(), getId().toString())
                : 0;
            statusMessage = "Previewing " + trackedCount + " tracked blocks";
        }
        return true;
        } catch (Exception e) {
            statusMessage = "Preview error";
            NodeCraft.LOGGER.error(
                "GeometryViewerNode[{}] refreshPreview failed: blockType={}, backend={}",
                getId(),
                effectiveBlockType,
                previewBackend,
                e
            );
            return false;
        }
    }

    private String sanitizeBlockType(@Nullable String requestedBlockType) {
        String candidate = requestedBlockType != null ? requestedBlockType.trim() : "";
        if (!candidate.isEmpty()) {
            return candidate;
        }
        String fallback = blockType != null ? blockType.trim() : "";
        if (!fallback.isEmpty()) {
            return fallback;
        }
        return "minecraft:stone";
    }

    private String buildSteadyStateStatus(@Nullable ExecutionContext context) {
        if (previewBackend == PreviewBackend.TRACKED_WORLD) {
            if (context == null || context.getWorld() == null) {
                return "Preview waiting for execution context";
            }
            return "Previewing "
                + TrackedPreviewPlacementService.getInstance().getTrackedCount(context.getWorld(), getId().toString())
                + " tracked blocks";
        }
        return "Previewing " + lastBlockCount + " ghost blocks";
    }

    private void clearAllPreviewState(@Nullable ExecutionContext context) {
        cachedGeometrySignature = 0;
        cachedTransparency = -1f;
        cachedColor = null;
        cachedBlockType = null;
        cachedPreviewBackend = null;
        PreviewManager.hideNodePreviews(getId().toString());
        if (context != null && context.getWorld() != null) {
            TrackedPreviewPlacementService.getInstance().clearTrackedPreview(context.getWorld(), getId().toString());
        }
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
            return block.getDefaultState();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float labelWidth = ImGui.calcTextSize("Preview waiting for execution context").x;
        return Math.max(180f, labelWidth + 16.0f);
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            layout.addVerticalSpacing(getMediumPadding());
            int statusColor = previewEnabled ? 0xFF44AADD : 0xFF888888;
            ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
            ImGui.text(statusMessage + "  |  " + lastBlockCount + " blocks");
            ImGui.popStyleColor();
            layout.addVerticalSpacing(getMediumPadding());
            return false;
        });
    }

    public String getPreviewColor() {
        return previewColor;
    }

    public void setPreviewColor(String value) {
        if (value != null) {
            previewColor = value;
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
        PreviewBackend sanitized = value != null ? value : PreviewBackend.GHOST;
        if (previewBackend != sanitized) {
            previewBackend = sanitized;
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

    @Override
    public void markDirty() {
        super.markDirty();
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
    }
}
