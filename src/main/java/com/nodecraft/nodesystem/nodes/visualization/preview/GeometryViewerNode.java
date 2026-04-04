package com.nodecraft.nodesystem.nodes.visualization.preview;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * Geometry Viewer 节点: 几何体查看器
 * 将输入的方块坐标列表显示为幽灵方块预览，并提供"永久放置"按钮将几何体实际放置到Minecraft世界中。
 * 
 * 工作流:
 *   几何体生成器 → GeometryViewer → 预览（幽灵方块）
 *                                  → 按钮点击 → 永久放置到世界
 */
@NodeInfo(
    id = "visualization.preview.geometry_viewer",
    displayName = "几何体查看器",
    description = "预览几何体（幽灵方块）并可永久放置到世界中",
    category = "visualization.preview"
)
public class GeometryViewerNode extends BaseCustomUINode {

    @NodeProperty(displayName = "预览颜色", category = "显示", order = 1)
    private String previewColor = "#4CAF50";

    @NodeProperty(displayName = "透明度", category = "显示", order = 2)
    private float transparency = 0.4f;

    @NodeProperty(displayName = "显示轮廓", category = "显示", order = 3)
    private boolean showOutline = true;

    @NodeProperty(displayName = "方块类型", category = "放置", order = 4)
    private String blockType = "minecraft:stone";

    @NodeProperty(displayName = "预览开启", category = "显示", order = 5)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Geometry Solid Preview", category = "Display", order = 6)
    private boolean previewSolidGeometry = true;

    @NodeProperty(displayName = "已放置", category = "状态", order = 6)
    private boolean placed = false;

    @NodeProperty(displayName = "放置模式", category = "放置", order = 7,
                  description = "覆盖：直接替换目标位置；增量：仅在空气位置放置")
    private PlacementMode placementMode = PlacementMode.OVERWRITE;

    @NodeProperty(displayName = "异步放置", category = "放置", order = 8,
                  description = "开启后分批放置，避免大体量时卡顿")
    private boolean useAsyncBake = true;

    @NodeProperty(displayName = "记录撤销", category = "放置", order = 9,
                  description = "记录被覆盖的方块，支持撤销")
    private boolean recordUndo = true;

    private UUID previewId = UUID.randomUUID();
    private boolean placementRequested = false;
    private int lastBlockCount = 0;
    private String statusMessage = "等待输入...";

    /** 虚拟几何体缓存：仅当参数变化时重绘 (脏标记) */
    private int cachedGeometrySignature = 0;
    private float cachedTransparency = -1f;
    private String cachedColor = null;

    // --- 输入端口 IDs ---
    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_COLOR_ID = "input_color";
    private static final String INPUT_TRANSPARENCY_ID = "input_transparency";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_PLACED_ID = "output_placed";

    // --- ImGui 缓冲 ---
    private transient ImString colorBuffer = new ImString(16);
    private transient ImString blockTypeBuffer = new ImString(128);
    private transient boolean colorNeedsSync = true;
    private transient boolean blockTypeNeedsSync = true;

    public GeometryViewerNode() {
        super(UUID.randomUUID(), "visualization.preview.geometry_viewer");

        // 输入端口
        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Geometry", "几何体方块坐标列表（来自几何体生成器）", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry Input", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to preview", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to preview", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to preview", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to preview", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "放置时使用的方块类型（默认stone）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COLOR_ID, "Preview Color", "预览颜色（十六进制）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TRANSPARENCY_ID, "Transparency", "预览透明度（0.0-1.0）", NodeDataType.FLOAT, this));

        // 输出端口
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "传递的方块坐标列表", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Block Count", "方块数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PLACED_ID, "Is Placed", "是否已永久放置", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "预览几何体（幽灵方块）并可永久放置到世界中";
    }

    // --- 核心逻辑 ---
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

        // 从输入获取参数
        String color = (colorObj instanceof String) ? (String) colorObj : this.previewColor;
        float trans = (transparencyObj instanceof Number) ? Math.max(0f, Math.min(1f, ((Number) transparencyObj).floatValue())) : this.transparency;
        String bType = (blockTypeObj instanceof String) ? (String) blockTypeObj : this.blockType;

        BlockPosList blocksList = resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj);

        int blockCount = (blocksList != null) ? blocksList.size() : 0;
        lastBlockCount = blockCount;

        // === 幽灵方块预览（脏标记：仅当几何体或显示参数变化时重绘）===
        int geometrySignature = computeGeometrySignature(blocksList);
        boolean previewDirty = (geometrySignature != cachedGeometrySignature)
                || (trans != cachedTransparency)
                || !Objects.equals(color, cachedColor);

        if (previewEnabled && blocksList != null && !blocksList.isEmpty()) {
            if (previewDirty) {
                try {
                    cachedGeometrySignature = geometrySignature;
                    cachedTransparency = trans;
                    cachedColor = color;

                    PreviewManager.hideNodePreviews(getId().toString());
                    List<Coordinate> coordinates = new ArrayList<>();
                    for (BlockPos pos : blocksList) {
                        coordinates.add(new Coordinate(pos.getX(), pos.getY(), pos.getZ()));
                    }
                    PreviewOptions options = new PreviewOptions()
                        .ghostBlockMode()
                        .setOpacity(trans);
                    PreviewManager.showGhostBlocks(getId().toString(), coordinates, options);
                } catch (Exception e) {
                    statusMessage = "预览失败: " + e.getMessage();
                    System.err.println("GeometryViewerNode preview error: " + e.getMessage());
                    return;
                }
            }
            statusMessage = "预览中: " + blockCount + " 方块";
        } else if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
            statusMessage = blockCount > 0 ? "预览已关闭 (" + blockCount + " 方块)" : "等待输入...";
        } else {
            cachedGeometrySignature = 0;
            cachedTransparency = -1f;
            cachedColor = null;
            PreviewManager.hideNodePreviews(getId().toString());
            statusMessage = "等待输入...";
        }

        // === 永久放置逻辑 ===
        if (placementRequested && blocksList != null && !blocksList.isEmpty()) {
            placementRequested = false;

            if (context != null && context instanceof com.nodecraft.nodesystem.execution.ExecutionContext execCtx
                    && execCtx.getWorld() != null) {
                BlockState targetState = resolveBlockState(bType);
                if (targetState == null) {
                    statusMessage = "无效方块类型: " + bType;
                } else {
                    List<BlockPos> posList = new ArrayList<>();
                    for (BlockPos pos : blocksList) {
                        posList.add(pos.toImmutable());
                    }

                    if (useAsyncBake) {
                        BakePlacementService.getInstance().enqueue(
                                execCtx.getWorld(), posList, targetState, placementMode,
                                recordUndo, 1000, null);
                        statusMessage = "已提交放置任务 (" + blockCount + " 方块，异步)";
                    } else {
                        int successCount = 0;
                        for (BlockPos pos : posList) {
                            if (placementMode == PlacementMode.INCREMENTAL
                                    && !execCtx.getWorld().isAir(pos)) {
                                continue;
                            }
                            if (execCtx.getWorld().setBlockState(pos, targetState, Block.NOTIFY_ALL)) {
                                successCount++;
                            }
                        }
                        placed = true;
                        statusMessage = "已放置: " + successCount + "/" + blockCount + " 方块";
                    }
                }
            } else {
                statusMessage = "无法放置: 需要执行上下文";
            }
        }

        // 设置输出
        outputValues.put(OUTPUT_BLOCKS_ID, blocksList);
        outputValues.put(OUTPUT_COUNT_ID, blockCount);
        outputValues.put(OUTPUT_PLACED_ID, placed);
    }

    private BlockPosList resolveBlocks(Object blocksObj, Object geometryObj, Object boxGeometryObj, Object cylinderGeometryObj, Object sphereGeometryObj, Object torusGeometryObj) {
        return GeometryVoxelizer.resolveBlocks(blocksObj, geometryObj, boxGeometryObj, cylinderGeometryObj, sphereGeometryObj, torusGeometryObj, previewSolidGeometry);
    }

    /** 计算几何体签名，用于脏标记检测 */
    private int computeGeometrySignature(BlockPosList blocks) {
        if (blocks == null || blocks.isEmpty()) return 0;
        int n = blocks.size();
        int h = n;
        int i = 0;
        for (BlockPos pos : blocks) {
            if (pos != null) h = 31 * h + pos.hashCode();
            if (++i >= 5) break;
        }
        return h;
    }

    /**
     * 将方块ID字符串解析为 BlockState
     */
    private BlockState resolveBlockState(String blockId) {
        if (blockId == null || blockId.isEmpty()) return null;
        try {
            Identifier id = Identifier.of(blockId);
            Block block = Registries.BLOCK.get(id);
            if (block != null) {
                return block.getDefaultState();
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve block state for: " + blockId);
        }
        return null;
    }

    // === UI 渲染 ===

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight();     // 状态行
        h += getSmallPadding();
        h += ImGui.getTextLineHeight();     // 方块计数
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 预览颜色输入
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 透明度滑条
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 方块类型输入
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 预览开关
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 轮廓开关
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 放置模式
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 异步放置
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 记录撤销
        h += getSmallPadding();
        h += ImGui.getFrameHeight();        // 放置按钮
        h += getMediumPadding();
        return h;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float aw = l.getAvailableContentWidth(width);
                l.addVerticalSpacing(getMediumPadding());

                // 状态行
                int statusColor = placed ? 0xFF44DD44 : (previewEnabled ? 0xFF44AADD : 0xFF888888);
                ImGui.pushStyleColor(ImGuiCol.Text, statusColor);
                ImGui.text(statusMessage);
                ImGui.popStyleColor();
                l.addVerticalSpacing(getSmallPadding());

                // 方块计数
                ImGui.text("方块数: " + lastBlockCount);
                l.addVerticalSpacing(getSmallPadding());

                // 预览颜色输入
                ensureColorBuffer();
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(aw / zoom);
                if (ImGui.inputTextWithHint("##gv_color", "#4CAF50", colorBuffer)) {
                    setPreviewColor(colorBuffer.get());
                    changed = true;
                }
                l.popItemWidth();
                l.popStyleVar();
                l.addVerticalSpacing(getSmallPadding());

                // 透明度滑条
                float[] trans = {transparency};
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(aw / zoom);
                if (ImGui.sliderFloat("##gv_trans", trans, 0.0f, 1.0f, "透明度: %.2f")) {
                    setTransparency(trans[0]);
                    changed = true;
                }
                l.popItemWidth();
                l.popStyleVar();
                l.addVerticalSpacing(getSmallPadding());

                // 方块类型输入
                ensureBlockTypeBuffer();
                l.pushFramePadding(4.0f, 3.0f);
                l.setItemWidth(aw / zoom);
                if (ImGui.inputTextWithHint("##gv_blocktype", "minecraft:stone", blockTypeBuffer)) {
                    setBlockType(blockTypeBuffer.get());
                    changed = true;
                }
                l.popItemWidth();
                l.popStyleVar();
                l.addVerticalSpacing(getSmallPadding());

                // 预览开关
                ImBoolean prevBool = new ImBoolean(previewEnabled);
                if (ImGui.checkbox("预览##gv_prev", prevBool)) {
                    setPreviewEnabled(prevBool.get());
                    changed = true;
                }
                l.addVerticalSpacing(getSmallPadding());

                // 轮廓开关
                ImBoolean solidBool = new ImBoolean(previewSolidGeometry);
                if (ImGui.checkbox("Solid Geometry##gv_solid", solidBool)) {
                    setPreviewSolidGeometry(solidBool.get());
                    changed = true;
                }
                l.addVerticalSpacing(getSmallPadding());

                ImBoolean olBool = new ImBoolean(showOutline);
                if (ImGui.checkbox("轮廓##gv_ol", olBool)) {
                    setShowOutline(olBool.get());
                    changed = true;
                }
                l.addVerticalSpacing(getSmallPadding());

                // 放置模式
                if (ImGui.beginCombo("##gv_placement_mode", placementMode == PlacementMode.OVERWRITE ? "覆盖" : "增量")) {
                    if (ImGui.selectable("覆盖", placementMode == PlacementMode.OVERWRITE)) {
                        setPlacementMode(PlacementMode.OVERWRITE);
                        changed = true;
                    }
                    if (ImGui.selectable("增量", placementMode == PlacementMode.INCREMENTAL)) {
                        setPlacementMode(PlacementMode.INCREMENTAL);
                        changed = true;
                    }
                    ImGui.endCombo();
                }
                l.addVerticalSpacing(getSmallPadding());

                // 异步放置
                ImBoolean asyncBool = new ImBoolean(useAsyncBake);
                if (ImGui.checkbox("异步放置##gv_async", asyncBool)) {
                    setUseAsyncBake(asyncBool.get());
                    changed = true;
                }
                l.addVerticalSpacing(getSmallPadding());

                // 记录撤销
                ImBoolean undoBool = new ImBoolean(recordUndo);
                if (ImGui.checkbox("记录撤销##gv_undo", undoBool)) {
                    setRecordUndo(undoBool.get());
                    changed = true;
                }
                l.addVerticalSpacing(getSmallPadding());

                // ======= 永久放置按钮 =======
                if (placed) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFF44DD44);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF55EE55);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF33CC33);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFFDD8844);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFFEE9955);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFFCC7733);
                }
                
                String btnText = placed ? "✓ 已放置到世界" : "▶ 永久放置到世界";
                if (ImGui.button(btnText, aw, 0)) {
                    placementRequested = true;
                    placed = false; // 重置状态以允许重新放置
                    changed = true;
                }
                ImGui.popStyleColor(3);

                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("GeometryViewerNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }

    // === ImGui 缓冲同步 ===

    private void ensureColorBuffer() {
        if (colorBuffer == null) colorBuffer = new ImString(16);
        if (colorNeedsSync) {
            colorBuffer.set(previewColor != null ? previewColor : "#4CAF50");
            colorNeedsSync = false;
        }
    }

    private void ensureBlockTypeBuffer() {
        if (blockTypeBuffer == null) blockTypeBuffer = new ImString(128);
        if (blockTypeNeedsSync) {
            blockTypeBuffer.set(blockType != null ? blockType : "minecraft:stone");
            blockTypeNeedsSync = false;
        }
    }

    // === Getters/Setters ===

    public String getPreviewColor() { return previewColor; }
    public void setPreviewColor(String v) {
        if (v != null) { this.previewColor = v; colorNeedsSync = true; markDirty(); }
    }

    public float getTransparency() { return transparency; }
    public void setTransparency(float v) {
        v = Math.max(0f, Math.min(1f, v));
        if (this.transparency != v) { this.transparency = v; markDirty(); }
    }

    public boolean isShowOutline() { return showOutline; }
    public void setShowOutline(boolean v) {
        if (this.showOutline != v) { this.showOutline = v; markDirty(); }
    }

    public String getBlockType() { return blockType; }
    public void setBlockType(String v) {
        if (v != null) { this.blockType = v; blockTypeNeedsSync = true; markDirty(); }
    }

    public boolean isPreviewEnabled() { return previewEnabled; }
    public void setPreviewEnabled(boolean v) {
        if (this.previewEnabled != v) { this.previewEnabled = v; markDirty(); }
    }

    public boolean isPreviewSolidGeometry() { return previewSolidGeometry; }
    public void setPreviewSolidGeometry(boolean v) {
        if (this.previewSolidGeometry != v) { this.previewSolidGeometry = v; markDirty(); }
    }

    public boolean isPlaced() { return placed; }

    public PlacementMode getPlacementMode() { return placementMode; }
    public void setPlacementMode(PlacementMode v) {
        if (placementMode != v) { placementMode = v; markDirty(); }
    }

    public boolean isUseAsyncBake() { return useAsyncBake; }
    public void setUseAsyncBake(boolean v) {
        if (useAsyncBake != v) { useAsyncBake = v; markDirty(); }
    }

    public boolean isRecordUndo() { return recordUndo; }
    public void setRecordUndo(boolean v) {
        if (recordUndo != v) { recordUndo = v; markDirty(); }
    }

    // === 状态序列化 ===

    @Override
    public @Nullable Object getNodeState() {
        java.util.Map<String, Object> s = new java.util.HashMap<>();
        s.put("previewColor", previewColor);
        s.put("transparency", transparency);
        s.put("showOutline", showOutline);
        s.put("blockType", blockType);
        s.put("previewEnabled", previewEnabled);
        s.put("previewSolidGeometry", previewSolidGeometry);
        s.put("placed", placed);
        s.put("previewId", previewId.toString());
        s.put("placementMode", placementMode.name());
        s.put("useAsyncBake", useAsyncBake);
        s.put("recordUndo", recordUndo);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("previewColor") instanceof String) setPreviewColor((String) m.get("previewColor"));
            if (m.get("transparency") instanceof Number) setTransparency(((Number) m.get("transparency")).floatValue());
            if (m.get("showOutline") instanceof Boolean) setShowOutline(Boolean.TRUE.equals(m.get("showOutline")));
            if (m.get("blockType") instanceof String) setBlockType((String) m.get("blockType"));
            if (m.get("previewEnabled") instanceof Boolean) setPreviewEnabled(Boolean.TRUE.equals(m.get("previewEnabled")));
            if (m.get("previewSolidGeometry") instanceof Boolean) setPreviewSolidGeometry(Boolean.TRUE.equals(m.get("previewSolidGeometry")));
            if (m.get("placed") instanceof Boolean) placed = Boolean.TRUE.equals(m.get("placed"));
            if (m.get("previewId") instanceof String) {
                try { previewId = UUID.fromString((String) m.get("previewId")); }
                catch (IllegalArgumentException e) { previewId = UUID.randomUUID(); }
            }
            if (m.get("placementMode") instanceof String) {
                try { setPlacementMode(PlacementMode.valueOf((String) m.get("placementMode"))); }
                catch (Exception ignored) {}
            }
            if (m.get("useAsyncBake") instanceof Boolean) setUseAsyncBake(Boolean.TRUE.equals(m.get("useAsyncBake")));
            if (m.get("recordUndo") instanceof Boolean) setRecordUndo(Boolean.TRUE.equals(m.get("recordUndo")));
        }
    }
}
