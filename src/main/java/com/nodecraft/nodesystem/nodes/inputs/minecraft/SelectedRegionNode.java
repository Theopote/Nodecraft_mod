package com.nodecraft.nodesystem.nodes.inputs.minecraft;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.util.Vector3;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.minecraft.PlayerAccessor;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 获取玩家选定区域（两个坐标点）的节点。
 * 显示自动更新复选框、选区坐标信息和体积。
 */
@NodeInfo(
    id = "inputs.minecraft.selected_region",
    displayName = "选定区域",
    description = "获取玩家选定的区域（两个坐标点）",
    category = "inputs.minecraft"
)
public class SelectedRegionNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "自动更新", category = "选区", order = 1,
                  description = "是否自动从玩家获取选区更新")
    private boolean autoUpdate = true;
    
    // --- 输出端口 ---
    private static final String OUTPUT_POS1_ID = "output_pos1";
    private static final String OUTPUT_POS2_ID = "output_pos2";
    private static final String OUTPUT_POS1_X_ID = "output_pos1_x";
    private static final String OUTPUT_POS1_Y_ID = "output_pos1_y";
    private static final String OUTPUT_POS1_Z_ID = "output_pos1_z";
    private static final String OUTPUT_POS2_X_ID = "output_pos2_x";
    private static final String OUTPUT_POS2_Y_ID = "output_pos2_y";
    private static final String OUTPUT_POS2_Z_ID = "output_pos2_z";
    private static final String OUTPUT_MIN_POS_ID = "output_min_pos";
    private static final String OUTPUT_MAX_POS_ID = "output_max_pos";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_HAS_SELECTION_ID = "output_has_selection";
    
    // 内部状态
    private Vector3 pos1 = null;
    private Vector3 pos2 = null;
    
    public SelectedRegionNode() {
        super(UUID.randomUUID(), "inputs.minecraft.selected_region");
        
        addOutputPort(new BasePort(OUTPUT_POS1_ID, "Position 1", "The first corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POS2_ID, "Position 2", "The second corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POS1_X_ID, "Pos1 X", "X coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS1_Y_ID, "Pos1 Y", "Y coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS1_Z_ID, "Pos1 Z", "Z coordinate of position 1", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_X_ID, "Pos2 X", "X coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_Y_ID, "Pos2 Y", "Y coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POS2_Z_ID, "Pos2 Z", "Z coordinate of position 2", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MIN_POS_ID, "Min Position", "The minimum corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAX_POS_ID, "Max Position", "The maximum corner position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X", "Width of the selection (X)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y", "Height of the selection (Y)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z", "Depth of the selection (Z)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Total volume of the selection in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HAS_SELECTION_ID, "Has Selection", "Whether a valid selection exists", NodeDataType.BOOLEAN, this));
        
        resetOutputs();
    }
    
    @Override
    public String getDescription() { return "获取玩家选定的区域（两个坐标点）"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            if (pos1 == null || pos2 == null) { resetOutputs(); } else { updateOutputsFromPositions(); }
            return;
        }
        if (autoUpdate) {
            PlayerAccessor playerAccessor = context.getPlayerAccessor();
            if (playerAccessor == null) {
                if (pos1 == null || pos2 == null) resetOutputs();
                return;
            }
            fetchSelectionFromPlayer(playerAccessor);
        }
        updateOutputsFromPositions();
    }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight(); // 自动更新复选框
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // pos1 标签
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // pos2 标签
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // 体积信息
        height += getSmallPadding();
        height += ImGui.getFrameHeight(); // 清除按钮
        height += getMediumPadding();
        return height;
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
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                // === 自动更新复选框 ===
                ImBoolean autoBool = new ImBoolean(autoUpdate);
                if (ImGui.checkbox("自动更新##auto", autoBool)) {
                    setAutoUpdate(autoBool.get());
                    changed = true;
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 选区坐标显示 ===
                boolean hasSelection = (pos1 != null && pos2 != null);
                
                if (hasSelection) {
                    // Pos1 显示 (绿色)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFF44CC88);
                    ImGui.text(String.format("P1: %.0f, %.0f, %.0f", pos1.getX(), pos1.getY(), pos1.getZ()));
                    ImGui.popStyleColor();
                    
                    l.addVerticalSpacing(getSmallPadding());
                    
                    // Pos2 显示 (蓝色)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFF4488FF);
                    ImGui.text(String.format("P2: %.0f, %.0f, %.0f", pos2.getX(), pos2.getY(), pos2.getZ()));
                    ImGui.popStyleColor();
                    
                    l.addVerticalSpacing(getSmallPadding());
                    
                    // 体积信息
                    int sizeX = (int)(Math.abs(pos2.getX() - pos1.getX())) + 1;
                    int sizeY = (int)(Math.abs(pos2.getY() - pos1.getY())) + 1;
                    int sizeZ = (int)(Math.abs(pos2.getZ() - pos1.getZ())) + 1;
                    int volume = sizeX * sizeY * sizeZ;
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFAAAACC);
                    ImGui.text(String.format("%dx%dx%d = %d", sizeX, sizeY, sizeZ, volume));
                    ImGui.popStyleColor();
                } else {
                    // 无选区
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFF888888);
                    ImGui.text("P1: 未设置");
                    l.addVerticalSpacing(getSmallPadding());
                    ImGui.text("P2: 未设置");
                    l.addVerticalSpacing(getSmallPadding());
                    ImGui.text("无选区");
                    ImGui.popStyleColor();
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 清除选区按钮 ===
                if (hasSelection) {
                    l.pushFramePadding(4.0f, 2.0f);
                    l.setItemWidth(availableWidth / zoom);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFF4444AA);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF5555CC);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF3333AA);
                    if (ImGui.button("清除选区##clear", availableWidth / zoom, 0)) {
                        clearSelection();
                        changed = true;
                    }
                    ImGui.popStyleColor(3);
                    l.popItemWidth();
                    l.popStyleVar();
                } else {
                    // 灰化的占位按钮
                    l.pushFramePadding(4.0f, 2.0f);
                    l.setItemWidth(availableWidth / zoom);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0xFF333333);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0xFF333333);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0xFF333333);
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFF666666);
                    ImGui.button("清除选区##clear_d", availableWidth / zoom, 0);
                    ImGui.popStyleColor(4);
                    l.popItemWidth();
                    l.popStyleVar();
                }
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("SelectedRegionNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void fetchSelectionFromPlayer(PlayerAccessor playerAccessor) {
        Vector3 playerPos = playerAccessor.getPlayerPosition();
        pos1 = new Vector3(playerPos.getX() - 2, playerPos.getY() - 2, playerPos.getZ() - 2);
        pos2 = new Vector3(playerPos.getX() + 2, playerPos.getY() + 2, playerPos.getZ() + 2);
    }
    
    public void setPos1(int x, int y, int z) {
        pos1 = new Vector3(x, y, z);
        if (pos2 != null) { updateOutputsFromPositions(); } else {
            outputValues.put(OUTPUT_POS1_ID, pos1);
            outputValues.put(OUTPUT_POS1_X_ID, (int)pos1.getX());
            outputValues.put(OUTPUT_POS1_Y_ID, (int)pos1.getY());
            outputValues.put(OUTPUT_POS1_Z_ID, (int)pos1.getZ());
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
        }
        invalidateCache();
        markDirty();
    }
    
    public void setPos2(int x, int y, int z) {
        pos2 = new Vector3(x, y, z);
        if (pos1 != null) { updateOutputsFromPositions(); } else {
            outputValues.put(OUTPUT_POS2_ID, pos2);
            outputValues.put(OUTPUT_POS2_X_ID, (int)pos2.getX());
            outputValues.put(OUTPUT_POS2_Y_ID, (int)pos2.getY());
            outputValues.put(OUTPUT_POS2_Z_ID, (int)pos2.getZ());
            outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
        }
        invalidateCache();
        markDirty();
    }
    
    public void clearSelection() {
        pos1 = null;
        pos2 = null;
        resetOutputs();
        invalidateCache();
        markDirty();
    }
    
    private void updateOutputsFromPositions() {
        if (pos1 == null || pos2 == null) { resetOutputs(); return; }
        
        outputValues.put(OUTPUT_POS1_ID, pos1);
        outputValues.put(OUTPUT_POS1_X_ID, (int)pos1.getX());
        outputValues.put(OUTPUT_POS1_Y_ID, (int)pos1.getY());
        outputValues.put(OUTPUT_POS1_Z_ID, (int)pos1.getZ());
        outputValues.put(OUTPUT_POS2_ID, pos2);
        outputValues.put(OUTPUT_POS2_X_ID, (int)pos2.getX());
        outputValues.put(OUTPUT_POS2_Y_ID, (int)pos2.getY());
        outputValues.put(OUTPUT_POS2_Z_ID, (int)pos2.getZ());
        
        float minX = Math.min(pos1.getX(), pos2.getX());
        float minY = Math.min(pos1.getY(), pos2.getY());
        float minZ = Math.min(pos1.getZ(), pos2.getZ());
        float maxX = Math.max(pos1.getX(), pos2.getX());
        float maxY = Math.max(pos1.getY(), pos2.getY());
        float maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        outputValues.put(OUTPUT_MIN_POS_ID, new Vector3(minX, minY, minZ));
        outputValues.put(OUTPUT_MAX_POS_ID, new Vector3(maxX, maxY, maxZ));
        
        int sizeX = (int)(maxX - minX) + 1;
        int sizeY = (int)(maxY - minY) + 1;
        int sizeZ = (int)(maxZ - minZ) + 1;
        
        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_VOLUME_ID, sizeX * sizeY * sizeZ);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, true);
    }
    
    private void resetOutputs() {
        Vector3 zeroVec = new Vector3(0, 0, 0);
        outputValues.put(OUTPUT_POS1_ID, zeroVec);
        outputValues.put(OUTPUT_POS2_ID, zeroVec);
        outputValues.put(OUTPUT_POS1_X_ID, 0);
        outputValues.put(OUTPUT_POS1_Y_ID, 0);
        outputValues.put(OUTPUT_POS1_Z_ID, 0);
        outputValues.put(OUTPUT_POS2_X_ID, 0);
        outputValues.put(OUTPUT_POS2_Y_ID, 0);
        outputValues.put(OUTPUT_POS2_Z_ID, 0);
        outputValues.put(OUTPUT_MIN_POS_ID, zeroVec);
        outputValues.put(OUTPUT_MAX_POS_ID, zeroVec);
        outputValues.put(OUTPUT_SIZE_X_ID, 0);
        outputValues.put(OUTPUT_SIZE_Y_ID, 0);
        outputValues.put(OUTPUT_SIZE_Z_ID, 0);
        outputValues.put(OUTPUT_VOLUME_ID, 0);
        outputValues.put(OUTPUT_HAS_SELECTION_ID, false);
    }
    
    public boolean isAutoUpdate() { return autoUpdate; }
    
    public void setAutoUpdate(boolean autoUpdate) {
        if (this.autoUpdate != autoUpdate) {
            this.autoUpdate = autoUpdate;
            markDirty();
        }
    }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("autoUpdate", isAutoUpdate());
        if (pos1 != null) {
            java.util.Map<String, Float> pos1Map = new java.util.HashMap<>();
            pos1Map.put("x", pos1.getX()); pos1Map.put("y", pos1.getY()); pos1Map.put("z", pos1.getZ());
            state.put("pos1", pos1Map);
        }
        if (pos2 != null) {
            java.util.Map<String, Float> pos2Map = new java.util.HashMap<>();
            pos2Map.put("x", pos2.getX()); pos2Map.put("y", pos2.getY()); pos2Map.put("z", pos2.getZ());
            state.put("pos2", pos2Map);
        }
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("autoUpdate")) {
                Object v = m.get("autoUpdate");
                if (v instanceof Boolean) setAutoUpdate((Boolean) v);
            }
            if (m.containsKey("pos1")) {
                Object p = m.get("pos1");
                if (p instanceof java.util.Map) {
                    java.util.Map<?, ?> pm = (java.util.Map<?, ?>) p;
                    if (pm.containsKey("x") && pm.containsKey("y") && pm.containsKey("z")) {
                        pos1 = new Vector3(((Number) pm.get("x")).floatValue(), ((Number) pm.get("y")).floatValue(), ((Number) pm.get("z")).floatValue());
                    }
                }
            } else { pos1 = null; }
            if (m.containsKey("pos2")) {
                Object p = m.get("pos2");
                if (p instanceof java.util.Map) {
                    java.util.Map<?, ?> pm = (java.util.Map<?, ?>) p;
                    if (pm.containsKey("x") && pm.containsKey("y") && pm.containsKey("z")) {
                        pos2 = new Vector3(((Number) pm.get("x")).floatValue(), ((Number) pm.get("y")).floatValue(), ((Number) pm.get("z")).floatValue());
                    }
                }
            } else { pos2 = null; }
            if (pos1 != null && pos2 != null) { updateOutputsFromPositions(); } else { resetOutputs(); }
        }
    }
}