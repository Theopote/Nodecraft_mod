package com.nodecraft.nodesystem.nodes.world.selection;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.style.MinecraftUITheme;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.interaction.IBlockPickerCallback;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewKind;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

// Minecraft 相关导入，用于世界状态检查与预览时的瞬时方块 ID 读取
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * 重构后的选定方块节点
 * 职责明确：仅负责输出拾取到的方块数据，不直接管理交互模式
 */
@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "world.selection.selected_block",
    displayName = "Selected Block",
    description = "Selection source only: Block Position / Has Selection / Valid / Error",
    category = "world.selection",
    order = 0
)
public class SelectedBlockNode extends BaseCustomUINode implements IBlockPickerCallback {

    public enum SourceMode {
        AUTO("Auto"),
        PICKED("Picked Block"),
        COORDINATES("Coordinates");

        private final String label;

        SourceMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private enum ActiveSource {
        NONE,
        PICKED,
        COORDINATES
    }
    
    // --- 节点设置（影响拾取行为） ---
    @NodeProperty(
        displayName = "Source Mode",
        category = "Source",
        order = 0,
        description = "Auto: any connected X/Y/Z owns the source (all three exact integers required); otherwise uses a picked block. Explicit modes never silently ignore the other source."
    )
    private SourceMode sourceMode = SourceMode.AUTO;

    @NodeProperty(
        displayName = "Max Distance",
        category = "Picking",
        order = 1,
        description = "Maximum distance used when picking a block."
    )
    private float maxDistance = 100.0f;

    @NodeProperty(
        displayName = "Include Fluids",
        category = "Picking",
        order = 2,
        description = "Whether fluid blocks can be selected."
    )
    private boolean includeFluids = false;

    @NodeProperty(
        displayName = "Show Block Preview",
        category = "Preview",
        order = 3,
        description = "Whether the selected block should show a preview in the world."
    )
    private boolean showBlockPreview = true;
    
    // --- 核心数据状态：拾取与坐标输入分开存储，由 Source Mode 选择谁驱动输出 ---
    // Position-only selection source — no cached block id / BlockState (compose Get Block for world data).
    private volatile Coordinate pickedBlockPosition = null;
    private volatile boolean hasPickedBlock = false;

    private volatile Coordinate inputBlockPosition = null;
    private volatile boolean hasInputBlock = false;

    @NodeProperty(
        displayName = "Active Source",
        readOnly = true,
        category = "Source",
        order = 1,
        description = "Which source currently drives this node's outputs."
    )
    public String getActiveSourceLabel() {
        return switch (resolveActiveSource()) {
            case PICKED -> "Picked Block";
            case COORDINATES -> "Coordinates";
            case NONE -> "None";
        };
    }

    @NodeProperty(
        displayName = "Has Selection",
        readOnly = true,
        category = "Selection",
        order = 10,
        description = "Whether this node currently has a selected block."
    )
    public boolean isSelectionActive() {
        return resolveActiveSource() != ActiveSource.NONE;
    }

    @NodeProperty(
        displayName = "Selected Position",
        readOnly = true,
        category = "Selection",
        order = 13,
        description = "Grid-aligned position of the current block."
    )
    public String getSelectedPositionForPanel() {
        ActiveBlock active = resolveActiveBlock();
        if (active == null || active.position() == null) {
            return "";
        }
        Coordinate position = active.position();
        return position.x() + ", " + position.y() + ", " + position.z();
    }

    // --- 输入验证状态 ---
    private volatile String inputValidationError = null;
    private transient boolean infoSectionExpanded = true;
    private transient boolean settingsSectionExpanded = false;

    // --- UI折叠状态（影响节点高度计算） ---
    
    // --- 预览管理 ---
    private volatile String currentGhostBlockPreviewId = null;
    
    // --- 输入端口 ---
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    
    // --- 输出端口 (Graph V62 slim selection source) ---
    private static final String OUTPUT_POSITION = "output_position";
    private static final String OUTPUT_HAS_SELECTION = "output_has_selection";
    private static final String OUTPUT_VALID = "output_valid";
    private static final String OUTPUT_ERROR = "output_error";
    
    public SelectedBlockNode() {
        super(UUID.randomUUID(), "world.selection.selected_block");
        
        // 创建输入端口
        addInputPort(new BasePort(INPUT_X_ID, "X", 
                "Block X (used when Source is Coordinates, or Auto when any of X/Y/Z is connected)", NodeDataType.INTEGER, this));
        
        addInputPort(new BasePort(INPUT_Y_ID, "Y", 
                "Block Y (used when Source is Coordinates, or Auto when any of X/Y/Z is connected)", NodeDataType.INTEGER, this));
        
        addInputPort(new BasePort(INPUT_Z_ID, "Z", 
                "Block Z (used when Source is Coordinates, or Auto when any of X/Y/Z is connected)", NodeDataType.INTEGER, this));
        
        // Slim selection-source outputs only
        addOutputPort(new BasePort(OUTPUT_POSITION, "Block Position",
                "Selected block grid position", NodeDataType.BLOCK_POS, this));

        addOutputPort(new BasePort(OUTPUT_HAS_SELECTION, "Has Selection",
                "Whether a block selection is currently active", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_VALID, "Valid",
                "Whether the current selection source resolved without error", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_ERROR, "Error",
                "Error message when Valid is false", NodeDataType.STRING, this));
        
        resetOutputs();
    }
    
    @Override
    public String getDescription() {
        return "Selection source only: Block Position / Has Selection / Valid / Error. Pick or drive via X/Y/Z.";
    }
    
    @Override
    public String getDisplayName() {
        return "Selected Block";
    }
    
    // === 核心节点逻辑 ===
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null) {
            resetOutputs();
            return;
        }

        ActiveSource active = resolveActiveSource();
        switch (active) {
            case COORDINATES -> tryProcessInputCoordinates(context);
            case PICKED -> {
                // Pick data already stored; drop stale coordinate errors.
                clearInputValidationState();
            }
            case NONE -> {
                clearInputValidationState();
                if (!hasPickedBlock) {
                    clearInputBlockData();
                }
            }
        }

        updateOutputsWithActiveBlock();
    }

    private ActiveSource resolveActiveSource() {
        SourceMode mode = sourceMode == null ? SourceMode.AUTO : sourceMode;
        return switch (mode) {
            case PICKED -> hasPickedBlock ? ActiveSource.PICKED : ActiveSource.NONE;
            case COORDINATES -> hasAnyCoordinateConnected() ? ActiveSource.COORDINATES : ActiveSource.NONE;
            case AUTO -> {
                // Any connected X/Y/Z owns the source — never fall back to picked while coords are wired.
                if (hasAnyCoordinateConnected()) {
                    yield ActiveSource.COORDINATES;
                }
                if (hasPickedBlock) {
                    yield ActiveSource.PICKED;
                }
                yield ActiveSource.NONE;
            }
        };
    }

    private record ActiveBlock(Coordinate position) {
    }

    private @Nullable ActiveBlock resolveActiveBlock() {
        return switch (resolveActiveSource()) {
            case PICKED -> hasPickedBlock && pickedBlockPosition != null
                ? new ActiveBlock(pickedBlockPosition)
                : null;
            case COORDINATES -> hasInputBlock && inputBlockPosition != null
                ? new ActiveBlock(inputBlockPosition)
                : null;
            case NONE -> null;
        };
    }

    private boolean isPortConnected(String portId) {
        return OptionalPortDrive.isConnected(this, portId);
    }

    private boolean hasAnyCoordinateConnected() {
        return isPortConnected(INPUT_X_ID) || isPortConnected(INPUT_Y_ID) || isPortConnected(INPUT_Z_ID);
    }
    
    /**
     * 更新输入端口的可用性
     * 当已拾取方块时，断开坐标输入端口的连接
     */
    private void updateInputPortAvailability() {
        try {
            if (hasPickedBlock) {
                // 如果已拾取方块，断开输入端口的连接
                IPort xPort = getInputPort(INPUT_X_ID);
                IPort yPort = getInputPort(INPUT_Y_ID);
                IPort zPort = getInputPort(INPUT_Z_ID);
                
                if (xPort != null && xPort.isConnected()) {
                    NodeCraft.LOGGER.debug("Keeping X input connection for picked block node {}", getId());
                }
                if (yPort != null && yPort.isConnected()) {
                    NodeCraft.LOGGER.debug("Keeping Y input connection for picked block node {}", getId());
                }
                if (zPort != null && zPort.isConnected()) {
                    NodeCraft.LOGGER.debug("Keeping Z input connection for picked block node {}", getId());
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("节点 {} 更新输入端口可用性失败: {}", getId(), e.getMessage());
        }
    }
    
    /**
     * 尝试从输入端口处理坐标（写入 input* 存储，不影响 pick 存储）。
     * Does not read the Minecraft world — stores exact integer coordinates only.
     */
    private void tryProcessInputCoordinates(@Nullable ExecutionContext context) {
        if (context == null) {
            return;
        }
        
        // 清除之前的验证状态
        clearInputValidationState();
        
        try {
            Integer x = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_X_ID));
            Integer y = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_Y_ID));
            Integer z = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_Z_ID));
            
            if (x == null || y == null || z == null) {
                inputValidationError = "X, Y, and Z must all be exact integers when any coordinate input is connected.";
                clearInputBlockData();
                return;
            }

            ValidationResult<Coordinate> rangeValidation = validateCoordinateRange(x, y, z);
            if (!rangeValidation.isValid()) {
                inputValidationError = rangeValidation.getMessage();
                clearInputBlockData();
                return;
            }
            
            Coordinate inputPosition = new Coordinate(x, y, z);
            
            // Store coordinates only — no world lookup / block-id cache.
            this.inputBlockPosition = inputPosition;
            this.hasInputBlock = true;

            refreshBlockPreview();
            SelectionVisualFeedback.getInstance().showBlockSelection(
                getId().toString(),
                inputPosition,
                SelectionVisualFeedback.SelectionState.SELECTED
            );
            
            NodeCraft.LOGGER.debug("节点 {} 从输入坐标设置位置: {}", getId(), inputPosition);
        } catch (Exception e) {
            inputValidationError = "处理输入坐标时发生异常: " + e.getMessage();
            clearInputBlockData();
            NodeCraft.LOGGER.debug("节点 {} 处理输入坐标失败: {}", getId(), e.getMessage());
        }
    }
    
    /**
     * 清除输入方块数据（不影响拾取存储）
     */
    private void clearInputBlockData() {
        this.inputBlockPosition = null;
        this.hasInputBlock = false;

        if (resolveActiveSource() != ActiveSource.PICKED) {
            clearBlockPreview();
            SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        }

        NodeCraft.LOGGER.debug("节点 {} 清除输入方块数据", getId());
    }
    
    /**
     * 通过ID查找输入端口
     * @param portId 端口ID
     * @return 找到的端口，如果未找到则返回null
     */
    protected IPort getInputPort(String portId) {
        for (IPort port : inputPorts) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    /**
     * 清除输入验证状态
     */
    private void clearInputValidationState() {
        inputValidationError = null;
    }
    
    /**
     * 验证坐标范围是否在 Minecraft 世界范围内
     * @param x X坐标
     * @param y Y坐标  
     * @param z Z坐标
     * @return 验证结果
     */
    private ValidationResult<Coordinate> validateCoordinateRange(int x, int y, int z) {
        // Minecraft 世界范围限制
        final int MIN_Y = -64;
        final int MAX_Y = 319;
        final int MAX_XZ = 30000000; // ±30M
        final int MIN_XZ = -30000000;
        
        // 检查Y坐标范围
        if (y < MIN_Y || y > MAX_Y) {
            return ValidationResult.failure(String.format("Y坐标超出世界范围 (%d 至 %d): %d", MIN_Y, MAX_Y, y));
        }
        
        // 检查X坐标范围
        if (x < MIN_XZ || x > MAX_XZ) {
            return ValidationResult.failure(String.format("X坐标超出世界范围 (±%d): %d", MAX_XZ, x));
        }
        
        // 检查Z坐标范围
        if (z < MIN_XZ || z > MAX_XZ) {
            return ValidationResult.failure(String.format("Z坐标超出世界范围 (±%d): %d", MAX_XZ, z));
        }
        
        return ValidationResult.success(new Coordinate(x, y, z));
    }
    
    private void updateOutputsWithActiveBlock() {
        // Coordinate connected-invalid: Valid=false, Has Selection=false, Error=message
        // Only when coordinate drive owns the source (never poison pick/idle with stale errors).
        if (resolveActiveSource() == ActiveSource.COORDINATES
                && inputValidationError != null && !inputValidationError.isEmpty()) {
            outputValues.put(OUTPUT_POSITION, BlockPos.ORIGIN);
            outputValues.put(OUTPUT_HAS_SELECTION, false);
            outputValues.put(OUTPUT_VALID, false);
            outputValues.put(OUTPUT_ERROR, inputValidationError);
            syncOutputPorts();
            return;
        }

        ActiveBlock active = resolveActiveBlock();
        if (active == null || active.position() == null) {
            // Idle / no selection: Has Selection=false, Valid=true, Error=""
            resetOutputs();
            return;
        }

        Coordinate position = active.position();
        outputValues.put(OUTPUT_POSITION, new BlockPos(position.x(), position.y(), position.z()));
        outputValues.put(OUTPUT_HAS_SELECTION, true);
        outputValues.put(OUTPUT_VALID, true);
        outputValues.put(OUTPUT_ERROR, "");
        syncOutputPorts();
    }

    private void resetOutputs() {
        outputValues.put(OUTPUT_POSITION, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_HAS_SELECTION, false);
        outputValues.put(OUTPUT_VALID, true);
        outputValues.put(OUTPUT_ERROR, "");
        syncOutputPorts();
    }
    
    // === 验证结果 ===
    
    /**
     * 验证结果类 - 使用泛型简化多种验证场景
     * @param <T> 验证成功时携带的数据类型
     */
    private static class ValidationResult<T> {
        private final boolean valid;
        private final String message;
        private final T data;
        
        private ValidationResult(boolean valid, String message, T data) {
            this.valid = valid;
            this.message = message;
            this.data = data;
        }
        
        public static <T> ValidationResult<T> success() {
            return new ValidationResult<>(true, null, null);
        }
        
        public static <T> ValidationResult<T> success(T data) {
            return new ValidationResult<>(true, null, data);
        }
        
        public static <T> ValidationResult<T> failure(String message) {
            return new ValidationResult<>(false, message, null);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 验证 Minecraft 世界状态
     * 检查客户端、玩家和世界是否可用
     * 
     * @return ValidationResult<Void> 验证结果
     */
    private ValidationResult<Void> validateWorldState() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return ValidationResult.failure("Minecraft 客户端未初始化");
            }
            
            if (client.player == null) {
                return ValidationResult.failure("玩家未加载");
            }
            
            if (client.world == null) {
                return ValidationResult.failure("世界未加载");
            }
            
            return ValidationResult.success();
        } catch (Exception e) {
            return ValidationResult.failure("世界状态检查失败: " + e.getMessage());
        }
    }
    
    // === IBlockPickerCallback 实现 ===
    
    @Override
    public void onBlockPicked(Coordinate position, String blockId, com.nodecraft.nodesystem.util.BlockStateData blockStateData) {
        ValidationResult<Void> worldValidation = validateWorldState();
        if (!worldValidation.isValid()) {
            NodeCraft.LOGGER.warn("节点 {} 无法拾取方块: {}", getId(), worldValidation.getMessage());
            onPickingCancelled();
            return;
        }

        // Position-only: ignore blockId / BlockState from picker (compose Get Block for world data).
        this.pickedBlockPosition = position;
        this.hasPickedBlock = true;
        updateOutputsWithActiveBlock();
        markDirty();
        
        try {
            SelectionVisualFeedback.getInstance().showBlockSelection(
                getId().toString(),
                position,
                SelectionVisualFeedback.SelectionState.SELECTED
            );
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 显示选择视觉反馈失败: {}", getId(), e.getMessage(), e);
        }
        
        refreshBlockPreview();
        NodeCraft.LOGGER.info("节点 {} 接收到拾取位置: {}", getId(), position);
    }
    
    @Override
    public void onPickingCancelled() {
        NodeCraft.LOGGER.debug("节点 {} 方块拾取被取消", getId());
    }
    
    @Override
    public IBlockPickerCallback.BlockPickingConfig getPickingConfig() {
        IBlockPickerCallback.BlockPickingConfig config = new IBlockPickerCallback.BlockPickingConfig();
        config.setMaxDistance(maxDistance);
        config.setIncludeFluids(includeFluids);
        return config;
    }
    
    // === 方块管理方法 ===
    
    public void clearPickedBlock() {
        hasPickedBlock = false;
        pickedBlockPosition = null;
        
        // 清除选择视觉反馈（仅当拾取不是当前活动源时，或没有坐标活动源）
        if (resolveActiveSource() != ActiveSource.COORDINATES) {
            SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
            clearBlockPreview();
        }
        updateOutputsWithActiveBlock();
        
        markDirty();
    }

    public void onNodeRemoved() {
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        clearBlockPreview();

        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isCurrentInteractionNode(getId().toString())) {
            interactionManager.cancelCurrentInteraction();
        }
    }
    
    /**
     * 统一的幽灵方块预览更新方法
     * 根据当前状态决定是否显示预览
     * 
     * 检查节点的游戏内可见性状态：
     * - 如果节点被设置为 "Hide in Game"，则不显示任何预览
     * - 只有当节点在游戏中可见时，才根据其他条件显示预览
     */
    private void refreshBlockPreview() {
        try {
            // 检查节点是否在游戏中可见
            if (!isNodeVisibleInGame()) {
                clearBlockPreview();
                return;
            }
            
            if (showBlockPreview) {
                ActiveBlock active = resolveActiveBlock();
                if (active != null && active.position() != null) {
                    createBlockPreview(active.position());
                } else {
                    clearBlockPreview();
                }
            } else {
                clearBlockPreview();
            }
        } catch (NullPointerException | IllegalArgumentException e) {
            NodeCraft.LOGGER.error("节点 {} 更新幽灵方块预览失败: {} - {}", getId(), 
                e.getClass().getSimpleName(), 
                e instanceof NullPointerException ? "检查PreviewRenderer或坐标数据" : "可能是无效的方块ID或坐标", e);
            currentGhostBlockPreviewId = null;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 更新幽灵方块预览失败: {} - {}", getId(), 
                e.getClass().getSimpleName(), e.getMessage(), e);
            currentGhostBlockPreviewId = null;
        }
    }
    
    private void createBlockPreview(Coordinate position) {
        if (position == null) {
            return;
        }
        try {
            ValidationResult<Void> worldValidation = validateWorldState();
            if (!worldValidation.isValid()) {
                NodeCraft.LOGGER.debug("节点 {} 跳过幽灵方块预览: {}", getId(), worldValidation.getMessage());
                return;
            }

            // Live world lookup for preview only — not cached as node selection state.
            String blockId = resolveLiveBlockId(position);
            if (blockId == null) {
                clearBlockPreview();
                return;
            }
            
            clearBlockPreview();
            
            PreviewBlocksPayload payload = new PreviewBlocksPayload(List.of(
                new PreviewBlock(
                    position.x(),
                    position.y(),
                    position.z(),
                    blockId
                )
            ));
            PreviewStyle style = PreviewStyle.forGhostBlocks(1.0f, 1.0f, 1.0f, 0.5f, false, "block_model", 2.0f, 0.1f, 0);
            PreviewOptions options = style.toPreviewOptions(PreviewKind.BLOCKS);
            currentGhostBlockPreviewId = PreviewRenderer.getInstance()
                    .showPreview(getId().toString(), "ghost_block", payload, options);
            
            if (currentGhostBlockPreviewId != null) {
                NodeCraft.LOGGER.debug("节点 {} 幽灵方块预览已显示: {} at {}, 预览ID: {}", 
                    getId(), blockId, position, currentGhostBlockPreviewId);
            } else {
                NodeCraft.LOGGER.warn("节点 {} 幽灵方块预览创建失败: {} at {}", 
                    getId(), blockId, position);
            }
        } catch (NullPointerException e) {
            NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: 空指针异常 - PreviewRenderer或方块数据为null", getId(), e);
            currentGhostBlockPreviewId = null;
        } catch (IllegalArgumentException e) {
            NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: 参数异常 - 无效坐标 {}", getId(), position, e);
            currentGhostBlockPreviewId = null;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: {} - {}", getId(), 
                e.getClass().getSimpleName(), e.getMessage(), e);
            currentGhostBlockPreviewId = null;
        }
    }

    private @Nullable String resolveLiveBlockId(Coordinate position) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || position == null) {
                return null;
            }
            BlockPos blockPos = new BlockPos(position.x(), position.y(), position.z());
            return Registries.BLOCK.getId(client.world.getBlockState(blockPos).getBlock()).toString();
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("节点 {} 瞬时读取预览方块 ID 失败: {}", getId(), e.getMessage());
            return null;
        }
    }
    
    private void clearBlockPreview() {
        if (currentGhostBlockPreviewId != null) {
            try {
                PreviewRenderer.getInstance().hidePreview(currentGhostBlockPreviewId);
                NodeCraft.LOGGER.debug("节点 {} 幽灵方块预览已隐藏: {}", getId(), currentGhostBlockPreviewId);
            } catch (NullPointerException e) {
                NodeCraft.LOGGER.error("节点 {} 隐藏幽灵方块预览失败: 空指针异常 - PreviewRenderer为null", getId(), e);
            } catch (IllegalArgumentException e) {
                NodeCraft.LOGGER.error("节点 {} 隐藏幽灵方块预览失败: 参数异常 - 无效的预览ID: {}", getId(), currentGhostBlockPreviewId, e);
            } catch (Exception e) {
                NodeCraft.LOGGER.error("节点 {} 隐藏幽灵方块预览失败: {} - {}", getId(), 
                    e.getClass().getSimpleName(), e.getMessage(), e);
            } finally {
                // 无论是否成功，都清理ID
                currentGhostBlockPreviewId = null;
            }
        }
    }
    
    // === BaseCustomUINode 实现 ===
    
    @Override
    protected float calculateUIHeight() {
        // 与 renderCustomUIScaled 布局保持一致；展开区高度必须计入，否则会被节点裁剪
        float buttonHeight = ImGui.getFrameHeight();
        float textLine = Math.max(ImGui.getTextLineHeightWithSpacing(), 18f);
        float headerHeight = buttonHeight;
        float smallGap = getSmallPadding();
        float baseHeight = 0f;

        baseHeight += smallGap;

        // Source Mode 标签 + combo
        baseHeight += textLine;
        baseHeight += ImGui.getFrameHeight();
        baseHeight += smallGap;
        // Active 行
        baseHeight += textLine;
        if ((sourceMode == SourceMode.PICKED && hasAnyCoordinateConnected())
                || (sourceMode == SourceMode.COORDINATES && hasPickedBlock)) {
            baseHeight += textLine;
        }
        baseHeight += smallGap;

        // 拾取按钮
        baseHeight += buttonHeight;

        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isPendingBlockPick(getId().toString())) {
            baseHeight += smallGap;
            baseHeight += textLine * 2;
        }

        if (inputValidationError != null && !inputValidationError.isEmpty()) {
            baseHeight += smallGap;
            baseHeight += textLine;
        }

        ActiveBlock activeBlock = resolveActiveBlock();
        if (resolveActiveSource() != ActiveSource.NONE && activeBlock != null) {
            baseHeight += smallGap;
            baseHeight += headerHeight; // 「已选位置」折叠头
            if (infoSectionExpanded) {
                baseHeight += smallGap;
                // 来源 / 位置
                baseHeight += textLine * 2;
                baseHeight += smallGap;
                baseHeight += 1f; // separator
                baseHeight += smallGap;
                baseHeight += buttonHeight; // 区内清除按钮
                baseHeight += smallGap;
            }
        }

        // Advanced Settings
        baseHeight += headerHeight;
        if (settingsSectionExpanded) {
            baseHeight += smallGap;
            baseHeight += textLine * 2; // 状态说明（可能换行）
            baseHeight += smallGap;
            baseHeight += textLine; // 方块预览
            baseHeight += textLine; // 包含流体
            baseHeight += textLine; // 最大距离标签
            baseHeight += 2f;
            baseHeight += ImGui.getFrameHeight(); // 滑条
            baseHeight += smallGap;
        }

        if (hasPickedBlock || hasInputBlock) {
            baseHeight += smallGap;
            baseHeight += buttonHeight; // 底部 Clear Selection
        }

        baseHeight += smallGap;
        return baseHeight;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f; // 最小宽度
    }
    
    /**
     * 覆写字体缩放计算，使其与通用节点元素保持一致
     * 
     * 通用节点元素（文字、连接点）使用直接的线性缩放：baseFontSize * canvasZoom
     * 为了确保自定义UI元素与它们同步缩放，我们也使用相同的策略
     * 
     * @param zoom 当前缩放级别
     * @return 与通用节点元素一致的字体缩放因子
     */
    @Override
    protected float calculateImGuiFontScale(float zoom) {
        // 使用与通用节点元素完全一致的缩放策略
        // 通用元素使用：baseFontSize * canvasZoom
        // 所以我们的字体缩放因子就是 zoom 本身
        
        // 添加基本的安全限制，防止极端值
        float clampedZoom = Math.max(0.1f, Math.min(zoom, 10.0f));
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Font Scale Debug] Node {}: Using direct zoom scaling for consistency with general elements - zoom={:.3f}, fontScale={:.3f}", 
                                 getId(), zoom, clampedZoom);
        }
        
        return clampedZoom;
    }
    
    /**
     * 渲染节点的自定义UI
     * 
     * 使用可折叠标题将UI分为三个逻辑区域：
     * 1. 主要操作区 - 拾取按钮和状态提示
     * 2. 状态显示区 - 已选方块信息（可折叠）
     * 3. 高级设置区 - 所有配置选项（可折叠）
     * 
     * 优化的输入事件传播控制：
     * - 使用 ImGui.getIO().getWantCaptureMouse() 检测鼠标输入捕获
     * - 使用 ImGui.getIO().getWantCaptureKeyboard() 检测键盘输入捕获
     * - 这比手动跟踪UI交互更精确，能捕获所有类型的输入事件
     * - 包括滑块的键盘输入、右键菜单、滚轮操作等
     * 
     * @param width 节点宽度
     * @param height 节点高度  
     * @param zoom 缩放级别
     * @return true 如果应该阻止事件传播到底层系统，false 否则
     */
    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        boolean changed = false;
        
        // === 使用 Minecraft UI 主题系统 ===
        // 注意：缩放变换现在由 CustomUIRenderer 统一处理，这里只需要应用主题颜色
        try (MinecraftUITheme.MinecraftStyleScope themeScope = MinecraftUITheme.apply(1.0f)) {
            // 将逻辑宽度和边距统一转换为像素后再做减法，确保缩放一致
            float edgeMargin = ZoomHelper.toScaledPixels(getSmallPadding(), zoom);
            float availableWidth = Math.max(0.0f, ZoomHelper.toScaledPixels(width, zoom) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();
            
            // 添加顶部间距（较小）
            addVerticalSpacing(getSmallPadding(), zoom);

            // Source Mode + Active Source
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            ImGui.textDisabled("Source Mode");
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            ImGui.pushItemWidth(availableWidth);
            ImInt modeIndex = new ImInt(switch (sourceMode == null ? SourceMode.AUTO : sourceMode) {
                case AUTO -> 0;
                case PICKED -> 1;
                case COORDINATES -> 2;
            });
            if (ImGui.combo("##sourceMode", modeIndex, new String[]{"Auto", "Picked Block", "Coordinates"})) {
                SourceMode next = switch (modeIndex.get()) {
                    case 1 -> SourceMode.PICKED;
                    case 2 -> SourceMode.COORDINATES;
                    default -> SourceMode.AUTO;
                };
                setSourceMode(next);
                changed = true;
            }
            ImGui.popItemWidth();
            addVerticalSpacing(getSmallPadding(), zoom);
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            ImGui.textDisabled("Active:");
            ImGui.sameLine();
            ImGui.text(getActiveSourceLabel());
            if (sourceMode == SourceMode.PICKED && hasAnyCoordinateConnected()) {
                ImGui.textColored(0.9f, 0.7f, 0.2f, 1.0f, "X/Y/Z connected but Source=Picked");
            } else if (sourceMode == SourceMode.COORDINATES && hasPickedBlock) {
                ImGui.textColored(0.9f, 0.7f, 0.2f, 1.0f, "Pick stored but Source=Coordinates");
            }
            addVerticalSpacing(getSmallPadding(), zoom);

            // === 1. 主要操作区 ===
            NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
            boolean isCurrentlyPicking = interactionManager.isPendingBlockPick(getId().toString());
            
            String pickButtonText = isCurrentlyPicking ? "取消拾取" : "拾取方块";
            float buttonHeight = ImGui.getFrameHeight();
            
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            boolean pickDisabled = sourceMode == SourceMode.COORDINATES;
            if (pickDisabled) {
                ImGui.beginDisabled();
            }
            if (ImGui.button(pickButtonText + "##pickBlock", availableWidth, buttonHeight)) {
                if (isCurrentlyPicking) {
                    // 取消当前拾取
                    interactionManager.cancelBlockPick();
                    NodeCraft.LOGGER.info("节点 {} 取消方块拾取", getId());
                } else {
                    // 确保编辑模式已激活
                    if (!interactionManager.isInEditorMode()) {
                        interactionManager.enterEditorMode();
                        NodeCraft.LOGGER.debug("节点 {} 激活编辑模式以支持方块拾取", getId());
                    }
                    
                    // 请求方块拾取（使用新的API）
                    NodeCraft.LOGGER.info("节点 {} 请求方块拾取 - 编辑模式:{} 交互模式:{}", 
                        getId(), interactionManager.isInEditorMode(), interactionManager.isInInteractionMode());
                    
                    interactionManager.requestBlockPick(getId().toString(), this);
                    NodeCraft.LOGGER.info("节点 {} 方块拾取请求已发送 - 请在游戏中左键点击一个方块", getId());
                }
                changed = true;
            }
            if (pickDisabled) {
                ImGui.endDisabled();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Source Mode is Coordinates — switch to Auto or Picked to pick.");
                }
            }

            // 拾取状态提示
            if (isCurrentlyPicking) {
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 0.0f, 1.0f); // 黄色
                ImGui.text("等待拾取中...");
                ImGui.text("在游戏中左键点击方块");
                ImGui.popStyleColor();
            }

            // 显示输入验证错误（如果有）
            renderInputValidationErrors(zoom);

            // === 2. 状态显示区 ===
            ActiveSource activeSource = resolveActiveSource();
            ActiveBlock activeBlock = resolveActiveBlock();
            if (activeSource != ActiveSource.NONE && activeBlock != null) {
                String headerText = activeSource == ActiveSource.PICKED
                    ? "已选位置##info"
                    : "输入坐标##info";
                int infoHeaderFlags = infoSectionExpanded ? ImGuiTreeNodeFlags.DefaultOpen : 0;
                boolean infoExpandedNow = ImGui.collapsingHeader(headerText, infoHeaderFlags);
                infoSectionExpanded = syncExpandableUiState(infoSectionExpanded, infoExpandedNow);
                if (infoSectionExpanded) {
                    ImGui.indent();
                    addVerticalSpacing(getSmallPadding(), zoom);
                    
                    ImGui.textDisabled("来源:");
                    ImGui.sameLine();
                    ImGui.text(getActiveSourceLabel());
                    
                    Coordinate activePosition = activeBlock.position();
                    if (activePosition != null) {
                        ImGui.textDisabled("位置:");
                        ImGui.sameLine();
                        ImGui.text(String.format("%d, %d, %d", 
                            activePosition.x(),
                            activePosition.y(),
                            activePosition.z()));
                        
                        if (ImGui.isItemHovered()) {
                            Vector3d center = new Vector3d(
                                activePosition.x() + 0.5,
                                activePosition.y() + 0.5,
                                activePosition.z() + 0.5
                            );
                            ImGui.setTooltip(String.format("中心点: %.2f, %.2f, %.2f", 
                                center.x, center.y, center.z));
                        }
                    }
                    
                    ImGui.unindent();
                    addVerticalSpacing(getSmallPadding(), zoom);
                    ImGui.separator();
                    addVerticalSpacing(getSmallPadding(), zoom);
                    
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.1f, 0.1f, 1.0f);
                    if (ImGui.button("清除选择##clearBlock", availableWidth, buttonHeight)) {
                        if (activeSource == ActiveSource.PICKED || hasPickedBlock) {
                            clearPickedBlock();
                        }
                        if (activeSource == ActiveSource.COORDINATES || hasInputBlock) {
                            clearInputBlockData();
                        }
                        updateOutputsWithActiveBlock();
                        changed = true;
                    }
                    ImGui.popStyleColor(3);
                    
                    addVerticalSpacing(getSmallPadding(), zoom);
                }
            }

            // === 3. 高级设置区 ===
            int settingsHeaderFlags = settingsSectionExpanded ? ImGuiTreeNodeFlags.DefaultOpen : 0;
            boolean settingsExpandedNow = ImGui.collapsingHeader("Advanced Settings##settings", settingsHeaderFlags);
            settingsSectionExpanded = syncExpandableUiState(settingsSectionExpanded, settingsExpandedNow);
            if (settingsSectionExpanded) {
                addVerticalSpacing(getSmallPadding(), zoom);
                
                // 输入端口 / Source Mode 状态说明
                ActiveSource activeForHint = resolveActiveSource();
                if (activeForHint == ActiveSource.PICKED && hasAnyCoordinateConnected()) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.6f, 0.2f, 1.0f); // 橙色
                    ImGui.textWrapped("X/Y/Z connected; Active Source is Picked (mode="
                        + (sourceMode == null ? SourceMode.AUTO : sourceMode).getLabel() + ")");
                    ImGui.popStyleColor();
                } else if (activeForHint == ActiveSource.COORDINATES) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f); // 绿色
                    ImGui.textWrapped("Active Source: Coordinates — X/Y/Z drive outputs");
                    ImGui.popStyleColor();
                } else if (activeForHint == ActiveSource.PICKED) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f);
                    ImGui.textWrapped("Active Source: Picked Block");
                    ImGui.popStyleColor();
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
                    ImGui.textWrapped("No active source — pick a block or connect X/Y/Z");
                    ImGui.popStyleColor();
                }
                addVerticalSpacing(getSmallPadding(), zoom);
                
                // 显示方块预览选项
                if (ImGui.checkbox("显示方块预览##blockPreview", showBlockPreview)) {
                    setShowBlockPreview(!showBlockPreview);
                    changed = true;
                }



                // 包含流体选项
                if (ImGui.checkbox("包含流体##includeFluids", includeFluids)) {
                    setIncludeFluids(!includeFluids);
                    changed = true;
                }

                // 最大距离滑块
                ImGui.text("最大距离: " + String.format("%.1f", maxDistance));
                addVerticalSpacing(2, zoom);
                
                float[] distanceArray = {maxDistance};
                ImGui.pushItemWidth(availableWidth);
                if (ImGui.sliderFloat("##maxDistance", distanceArray, 1.0f, 300.0f)) {
                    setMaxDistance(distanceArray[0]);
                    changed = true;
                }
                ImGui.popItemWidth();
                
                addVerticalSpacing(getSmallPadding(), zoom);
            }

            if (hasPickedBlock || hasInputBlock) {
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.1f, 0.1f, 1.0f);
                if (ImGui.button("Clear Selection##clearBlock", availableWidth, buttonHeight)) {
                    clearPickedBlock();
                    clearInputBlockData();
                    updateOutputsWithActiveBlock();
                    changed = true;
                }
                ImGui.popStyleColor(3);
            }

        } catch (NullPointerException e) {
            NodeCraft.LOGGER.error("节点 {} UI渲染失败: 空指针异常 - 可能的原因: 未初始化的组件或数据", getId(), e);
            // 在调试模式下提供更多上下文信息
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("节点状态: hasPickedBlock={}, pickedBlockPosition={}, showBlockPreview={}", 
                    hasPickedBlock, pickedBlockPosition, showBlockPreview);
            }
        } catch (IllegalStateException e) {
            NodeCraft.LOGGER.error("节点 {} UI渲染失败: 非法状态异常 - ImGui可能未正确初始化", getId(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} UI渲染失败: {} - {}", getId(), 
                e.getClass().getSimpleName(), e.getMessage(), e);
        } // try-with-resources 会自动调用 themeScope.close() 来恢复样式

        // 使用ImGui的输入捕获状态来精确控制事件传播
        // 这比手动跟踪交互状态更可靠，能捕获所有类型的输入（鼠标、键盘、滚轮等）
        boolean wantCaptureMouse = ImGui.getIO().getWantCaptureMouse();
        boolean wantCaptureKeyboard = ImGui.getIO().getWantCaptureKeyboard();
        boolean shouldCaptureInput = wantCaptureMouse || wantCaptureKeyboard;
        
        if (shouldCaptureInput) {
            // ImGui正在处理输入，阻止事件传播到Minecraft世界
            // 可选：添加调试日志（在开发阶段有用）
            if (System.getProperty("nodecraft.debug.ui", "false").equals("true")) {
                NodeCraft.LOGGER.debug("节点 {} 捕获输入 - 鼠标: {}, 键盘: {}", 
                    getId().toString().substring(0, 8), wantCaptureMouse, wantCaptureKeyboard);
            }
            return true;
        }

        return changed;
    }
    
    // === 辅助方法 ===
    
    /**
     * 渲染输入验证错误和警告信息
     * @param zoom 缩放级别
     */
    private void renderInputValidationErrors(float zoom) {
        if (inputValidationError != null && !inputValidationError.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
            ImGui.text("错误: " + inputValidationError);
            ImGui.popStyleColor();
            addVerticalSpacing(getSmallPadding(), zoom);
        }
    }

    // === 属性访问器 ===
    
    /*
     * markDirty() 调用规则：
     * 
     * 1. 数据变更规则：
     *    - 任何影响节点输出的数据变化都必须调用 markDirty()
     *    - 包括：拾取的方块数据、配置参数等
     * 
     * 2. 状态变更规则：
     *    - 任何影响节点状态的设置变化都应该调用 markDirty()
     *    - 包括：maxDistance、includeFluids、useHandItem、showBlockPreview
     *    - 确保状态序列化、反序列化和下游节点更新正常工作
     * 
     * 3. 一致性原则：
     *    - 所有setter方法保持一致的markDirty()调用模式
     *    - 避免部分设置调用、部分设置不调用的不一致情况
     * 
     * 4. 性能考虑：
     *    - markDirty()调用开销很小，一致性比微优化更重要
     *    - 避免因为不调用markDirty()导致的状态同步问题
     */
    
    public float getMaxDistance() {
        return maxDistance;
    }

    public SourceMode getSourceMode() {
        return sourceMode == null ? SourceMode.AUTO : sourceMode;
    }

    public void setSourceMode(SourceMode sourceMode) {
        SourceMode next = sourceMode == null ? SourceMode.AUTO : sourceMode;
        if (this.sourceMode != next) {
            this.sourceMode = next;
            invalidateCache();
            markDirty();
        }
    }
    
    public void setMaxDistance(float maxDistance) {
        if (maxDistance < 0) maxDistance = 0;
        if (maxDistance > 1000) maxDistance = 1000;
        
        if (this.maxDistance != maxDistance) {
            this.maxDistance = maxDistance;
            markDirty();
        }
    }
    
    public void setIncludeFluids(boolean includeFluids) {
        if (this.includeFluids != includeFluids) {
            this.includeFluids = includeFluids;
            markDirty();
        }
    }

    public void setShowBlockPreview(boolean showBlockPreview) {
        if (this.showBlockPreview != showBlockPreview) {
            this.showBlockPreview = showBlockPreview;
            
            // 统一调用预览更新方法
            refreshBlockPreview();
            
            // 统一调用markDirty()确保节点状态变化被正确跟踪
            // 虽然showBlockPreview主要影响UI显示，但它是节点状态的一部分
            // 保持与其他设置方法的一致性，确保状态序列化和下游更新正常工作
            markDirty();
        }
    }
    
    // === 状态序列化 ===
    
    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        
        try {
            // 直接保存设置为顶级属性，与@NodeProperty机制兼容
            state.put("sourceMode", (sourceMode == null ? SourceMode.AUTO : sourceMode).name());
            state.put("maxDistance", maxDistance);
            state.put("includeFluids", includeFluids);
            state.put("showBlockPreview", showBlockPreview);
            // Do not persist pickedBlock — pick is session/runtime only.
            
            NodeCraft.LOGGER.debug("节点 {} 状态序列化完成，包含 {} 个属性", getId(), state.size());
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 状态序列化失败", getId(), e);
            // 返回基本状态，确保不会完全失败
            Map<String, Object> fallbackState = new HashMap<>();
            fallbackState.put("sourceMode", SourceMode.AUTO.name());
            fallbackState.put("maxDistance", 100.0f);
            fallbackState.put("includeFluids", false);
            fallbackState.put("showBlockPreview", true);
            return fallbackState;
        }
        
        return state;
    }
    
    /**
     * 恢复节点状态
     * 
     * 健壮的状态反序列化实现：
     * - 严格的类型检查，使用 Map<String, Object> 类型
     * - 详细的错误日志和异常处理
     * - 对无效数据的容错处理
     * - 确保状态一致性和预览同步
     * 
     * @param state 要恢复的状态对象
     */
    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map)) {
            NodeCraft.LOGGER.warn("节点 {} 状态恢复失败: 无效状态类型 {}, 期望 Map", 
                getId(), state != null ? state.getClass().getSimpleName() : "null");
            return;
        }
        
        try {
            // 使用更严格的类型检查
            @SuppressWarnings("unchecked")
            Map<String, Object> stateMap = (Map<String, Object>) state;
            
            NodeCraft.LOGGER.debug("节点 {} 开始状态恢复，包含 {} 个属性", getId(), stateMap.size());

            if (stateMap.get("sourceMode") instanceof String modeName) {
                try {
                    setSourceMode(SourceMode.valueOf(modeName));
                } catch (IllegalArgumentException ignored) {
                    setSourceMode(SourceMode.AUTO);
                }
            }
            
            // 恢复设置属性，使用模式匹配（Java 14+）或传统方式
            restoreFloatSetting(stateMap, "maxDistance", this::setMaxDistance, 1.0f, 1000.0f);
            restoreBooleanSetting(stateMap, "includeFluids", this::setIncludeFluids);
            
            // showBlockPreview: 只读取新 key，保持状态键统一。
            if (stateMap.get("showBlockPreview") instanceof Boolean showPreview) {
                this.showBlockPreview = showPreview;
                NodeCraft.LOGGER.debug("节点 {} 恢复 showBlockPreview: {}", getId(), showPreview);
            }
            
            // Ignore legacy pickedBlock map — do not restore pick from save.
            
            // 状态恢复完成后，统一更新方块预览
            refreshBlockPreview();
            
            markDirty(); // 确保节点状态恢复后能够触发更新
            
            NodeCraft.LOGGER.debug("节点 {} 状态恢复完成", getId());
            
        } catch (ClassCastException e) {
            NodeCraft.LOGGER.error("节点 {} 状态恢复失败: 类型转换异常 - 状态格式不兼容", getId(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 状态恢复失败: 未知异常", getId(), e);
        }
    }
    
    /**
     * 恢复浮点数设置
     */
    private void restoreFloatSetting(Map<String, Object> stateMap, String key, 
                                   java.util.function.Consumer<Float> setter, float min, float max) {
        if (stateMap.get(key) instanceof Number number) {
            float value = number.floatValue();
            // 应用范围限制
            value = Math.max(min, Math.min(max, value));
            setter.accept(value);
            NodeCraft.LOGGER.debug("节点 {} 恢复 {}: {}", getId(), key, value);
        } else if (stateMap.containsKey(key)) {
            NodeCraft.LOGGER.warn("节点 {} 恢复 {} 失败: 无效类型 {}", 
                getId(), key, stateMap.get(key).getClass().getSimpleName());
        }
    }
    
    /**
     * 恢复布尔设置
     */
    private void restoreBooleanSetting(Map<String, Object> stateMap, String key, 
                                     java.util.function.Consumer<Boolean> setter) {
        if (stateMap.get(key) instanceof Boolean value) {
            setter.accept(value);
            NodeCraft.LOGGER.debug("节点 {} 恢复 {}: {}", getId(), key, value);
        } else if (stateMap.containsKey(key)) {
            NodeCraft.LOGGER.warn("节点 {} 恢复 {} 失败: 无效类型 {}", 
                getId(), key, stateMap.get(key).getClass().getSimpleName());
        }
    }

    /**
     * 检查节点是否在游戏中可见（控制幽灵预览是否显示）。
     */
    private boolean isNodeVisibleInGame() {
        try {
            com.nodecraft.gui.editor.impl.ImGuiNodeEditor editor =
                com.nodecraft.gui.editor.impl.ImGuiNodeEditor.getInstance();

            if (editor != null) {
                boolean isVisible = editor.isNodeVisible(getId());
                NodeCraft.LOGGER.debug("节点 {} 游戏内可见性检查: {}", getId(), isVisible);
                return isVisible;
            }
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("节点 {} 检查游戏内可见性时出现异常，默认为可见: {}", getId(), e.getMessage());
            return true;
        }
    }
}
