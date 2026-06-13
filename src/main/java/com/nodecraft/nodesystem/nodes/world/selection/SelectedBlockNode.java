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
import com.nodecraft.nodesystem.util.BlockStateData;
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
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

// Minecraft 相关导入，用于世界状态检查
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.world.ClientWorld;

// 新增导入，用于获取方块的详细信息
import net.minecraft.text.Text;
import org.joml.Vector3d;

/**
 * 重构后的选定方块节点
 * 职责明确：仅负责输出拾取到的方块数据，不直接管理交互模式
 */
@NodeInfo(
    id = "world.selection.selected_block",
    displayName = "Selected Block",
    description = "获取方块信息，支持交互拾取或坐标输入",
    category = "world.selection",
    order = 0
)
public class SelectedBlockNode extends BaseCustomUINode implements IBlockPickerCallback {
    
    // --- 节点设置（影响拾取行为） ---
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
    
    // --- 核心数据状态 ---
    private volatile Coordinate pickedBlockPosition = null;
    private volatile String pickedBlockId = "minecraft:air";
    private volatile BlockStateData pickedBlockStateData = null;
    private volatile boolean hasPickedBlock = false;

    @NodeProperty(
        displayName = "Has Selection",
        readOnly = true,
        category = "Selection",
        order = 10,
        description = "Whether this node currently has a selected block."
    )
    public boolean isSelectionActive() {
        return hasPickedBlock || hasInputCoordinates();
    }

    @NodeProperty(
        displayName = "Selected Block Name",
        readOnly = true,
        category = "Selection",
        order = 11,
        description = "Human-readable name of the current block."
    )
    public String getSelectedBlockDisplayName() {
        return getBlockDisplayName(pickedBlockId);
    }

    @NodeProperty(
        displayName = "Selected Block ID",
        readOnly = true,
        category = "Selection",
        order = 12,
        description = "Identifier of the current block."
    )
    public String getSelectedBlockIdForPanel() {
        return pickedBlockId;
    }

    @NodeProperty(
        displayName = "Selected Position",
        readOnly = true,
        category = "Selection",
        order = 13,
        description = "Grid-aligned position of the current block."
    )

    // --- 输入验证状态 ---
    public String getSelectedPositionForPanel() {
        if (pickedBlockPosition == null) {
            return "";
        }
        return pickedBlockPosition.getX() + ", " + pickedBlockPosition.getY() + ", " + pickedBlockPosition.getZ();
    }

    private volatile String inputValidationError = null;
    private volatile boolean hasInputValidationWarning = false;
    private volatile String inputValidationWarning = null;
    private transient boolean infoSectionExpanded = true;
    private transient boolean settingsSectionExpanded = false;
    private transient boolean blockStateTreeExpanded = false;

    // --- UI折叠状态（影响节点高度计算） ---
    
    // --- 预览管理 ---
    private volatile String currentGhostBlockPreviewId = null;
    
    // --- 输入端口 ---
    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    
    // --- 输出端口 ---
    // 核心输出
    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_BLOCK_NAME = "output_block_name";
    private static final String OUTPUT_POSITION = "output_position";
    private static final String OUTPUT_CENTER = "output_center";
    private static final String OUTPUT_BLOCK_STATE = "output_block_state";
    private static final String OUTPUT_HAS_BLOCK_ENTITY = "output_has_block_entity";
    
    // 坐标分量输出
    private static final String OUTPUT_BLOCK_X_ID = "output_block_x";
    private static final String OUTPUT_BLOCK_Y_ID = "output_block_y";
    private static final String OUTPUT_BLOCK_Z_ID = "output_block_z";
    
    public SelectedBlockNode() {
        super(UUID.randomUUID(), "world.selection.selected_block");
        
        // 创建输入端口
        addInputPort(new BasePort(INPUT_X_ID, "X", 
                "方块的X坐标（仅在未拾取方块时可用）", NodeDataType.INTEGER, this));
        
        addInputPort(new BasePort(INPUT_Y_ID, "Y", 
                "方块的Y坐标（仅在未拾取方块时可用）", NodeDataType.INTEGER, this));
        
        addInputPort(new BasePort(INPUT_Z_ID, "Z", 
                "方块的Z坐标（仅在未拾取方块时可用）", NodeDataType.INTEGER, this));
        
        // 创建输出端口
        // 核心输出端口
        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block ID", 
                "机器可读的唯一标识，如 minecraft:oak_planks", NodeDataType.STRING, this));
        
        addOutputPort(new BasePort(OUTPUT_BLOCK_NAME, "Block Name", 
                "人类可读的显示名称，如 '橡木楼梯'", NodeDataType.STRING, this));
        
        addOutputPort(new BasePort(OUTPUT_POSITION, "Position", 
                "整数坐标，用于定位和网格对齐", NodeDataType.COORDINATE, this));
        
        addOutputPort(new BasePort(OUTPUT_CENTER, "Center", 
                "方块几何中心，用于精确的非网格对齐操作", NodeDataType.VECTOR, this));
        
        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE, "Block State", 
                "方块的变体属性，如 {\"facing\": \"north\", \"waterlogged\": \"true\"}", NodeDataType.BLOCK_STATE_DATA, this));
        
        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY, "Has Block Entity", 
                "是否有额外数据（箱子、熔炉等）", NodeDataType.BOOLEAN, this));
        
        // 坐标分量输出
        addOutputPort(new BasePort(OUTPUT_BLOCK_X_ID, "Block X", 
                "方块的X坐标", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_BLOCK_Y_ID, "Block Y", 
                "方块的Y坐标", NodeDataType.INTEGER, this));
        
        addOutputPort(new BasePort(OUTPUT_BLOCK_Z_ID, "Block Z", 
                "方块的Z坐标", NodeDataType.INTEGER, this));
        
        resetOutputs();
    }
    
    @Override
    public String getDescription() {
        return "获取方块信息，支持交互拾取或坐标输入两种方式。";
    }
    
    @Override
    public String getDisplayName() {
        return "Selected Block";
    }
    
    // === 核心节点逻辑 ===
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 空检查 - 如果context为null，重置输出
        if (context == null) {
            resetOutputs();
            return;
        }
        
        // 更新输入端口的可用性
        // Keep existing X/Y/Z graph connections intact; picked blocks simply take priority.
        
        // 如果没有拾取方块，尝试从输入端口获取坐标
        if (!hasPickedBlock) {
            tryProcessInputCoordinates(context);
        }
        
        // 输出当前方块数据（无论是拾取的还是从输入获取的）
        updateOutputsWithPickedBlock();
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
     * 尝试从输入端口处理坐标
     * 仅在未拾取方块时执行
     */
    private void tryProcessInputCoordinates(@Nullable ExecutionContext context) {
        if (context == null) {
            return;
        }
        
        // 清除之前的验证状态
        clearInputValidationState();
        
        try {
            // 获取输入坐标
            Object xValue = getInputValue(INPUT_X_ID, 0);
            Object yValue = getInputValue(INPUT_Y_ID, 0);
            Object zValue = getInputValue(INPUT_Z_ID, 0);
            
            // 检查是否所有坐标都有值
            if (xValue instanceof Integer x && yValue instanceof Integer y && zValue instanceof Integer z) {
                // 验证坐标范围
                ValidationResult<Coordinate> rangeValidation = validateCoordinateRange(x, y, z);
                if (!rangeValidation.isValid()) {
                    inputValidationError = rangeValidation.getMessage();
                    clearInputBlockData();
                    return;
                }
                
                // 创建坐标对象
                Coordinate inputPosition = new Coordinate(x, y, z);
                
                // 验证坐标是否与当前位置不同，避免重复处理
                if (!inputPosition.equals(pickedBlockPosition)) {
                    // 从世界中获取该位置的方块信息
                    processBlockAtPosition(inputPosition);
                }
            } else {
                // 如果坐标不完整，清除当前的输入方块数据（但保留拾取的方块数据）
                if (pickedBlockPosition != null && !hasPickedBlock) {
                    clearInputBlockData();
                }
            }
        } catch (Exception e) {
            inputValidationError = "处理输入坐标时发生异常: " + e.getMessage();
            NodeCraft.LOGGER.debug("节点 {} 处理输入坐标失败: {}", getId(), e.getMessage());
        }
    }
    
    /**
     * 处理指定位置的方块
     * 从世界中获取方块信息
     */
    private void processBlockAtPosition(Coordinate position) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                inputValidationError = "世界未加载，无法获取方块信息";
                NodeCraft.LOGGER.debug("节点 {} 无法处理位置 {}: 世界未加载", getId(), position);
                return;
            }
            
            BlockPos blockPos = new BlockPos(position.getX(), position.getY(), position.getZ());
            
            // 检查区块是否已加载
            try {
                if (client.world.getChunk(blockPos) == null) {
                    inputValidationError = "目标区块未加载，请等待区块加载完成";
                    NodeCraft.LOGGER.debug("节点 {} 无法处理位置 {}: 区块未加载", getId(), position);
                    return;
                }
            } catch (Exception e) {
                inputValidationError = "目标区块未加载，请等待区块加载完成";
                NodeCraft.LOGGER.debug("节点 {} 无法处理位置 {}: 区块未加载", getId(), position);
                return;
            }
            
            // 获取方块状态
            BlockState blockState = client.world.getBlockState(blockPos);
            Block block = blockState.getBlock();
            
            if (block == null) {
                NodeCraft.LOGGER.debug("节点 {} 位置 {} 的方块为null", getId(), position);
                return;
            }
            
            // 获取方块ID
            String blockId = Registries.BLOCK.getId(block).toString();
            
            // 创建方块状态数据
            BlockStateData blockStateData = new BlockStateData();
            try {
                blockState.getProperties().forEach(property -> {
                    try {
                        String key = property.getName();
                        String value = blockState.get(property).toString();
                        blockStateData.put(key, value);
                    } catch (Exception e) {
                        // 忽略无法获取的属性
                    }
                });
            } catch (Exception e) {
                NodeCraft.LOGGER.debug("节点 {} 获取方块状态失败", getId(), e);
            }
            
            // 更新方块数据（标记为输入数据，不是拾取数据）
            this.pickedBlockPosition = position;
            this.pickedBlockId = blockId;
            this.pickedBlockStateData = blockStateData;
            // 注意：不设置 hasPickedBlock = true，因为这是输入数据
            
            // 实时验证方块状态一致性
            ValidationResult<BlockValidationData> blockValidation = validatePickedBlock(position, blockId, blockStateData);
            if (!blockValidation.isValid()) {
                hasInputValidationWarning = true;
                inputValidationWarning = blockValidation.getMessage();
                NodeCraft.LOGGER.debug("节点 {} 输入坐标方块验证警告: {}", getId(), blockValidation.getMessage());
            }
            
            // 更新方块预览
            refreshBlockPreview();

            // 输入坐标驱动的选中也显示方块高亮（与交互拾取保持一致）。
            SelectionVisualFeedback.getInstance().showBlockSelection(
                getId().toString(),
                position,
                SelectionVisualFeedback.SelectionState.SELECTED
            );
            updateOutputsWithPickedBlock();
            
            NodeCraft.LOGGER.debug("节点 {} 从输入坐标获取方块: {} at {}", getId(), blockId, position);
            
        } catch (Exception e) {
            inputValidationError = "获取方块信息时发生异常: " + e.getMessage();
            NodeCraft.LOGGER.error("节点 {} 处理位置 {} 的方块失败: {}", getId(), position, e.getMessage(), e);
        }
    }
    
    /**
     * 清除输入方块数据
     * 仅清除通过输入坐标获取的数据，不影响拾取的数据
     */
    private void clearInputBlockData() {
        if (!hasPickedBlock) {
            this.pickedBlockPosition = null;
            this.pickedBlockId = "minecraft:air";
            this.pickedBlockStateData = null;
            
            // 隐藏方块预览
            clearBlockPreview();

            // 同步清理输入坐标驱动的方块高亮。
            SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
            
            NodeCraft.LOGGER.debug("节点 {} 清除输入方块数据", getId());
        }
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
     * 获取输入值的辅助方法
     * @param portId 端口ID
     * @param defaultValue 默认值
     * @return 输入值或默认值
     */
    @SuppressWarnings("unchecked")
    private <T> T getInputValue(String portId, T defaultValue) {
        Object value = inputValues.get(portId);
        if (value != null) {
            try {
                return (T) value;
            } catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 检查是否有输入坐标
     * @return 如果有完整的输入坐标返回true
     */
    private boolean hasInputCoordinates() {
        if (hasPickedBlock) {
            return false; // 如果已拾取方块，输入坐标无效
        }
        
        Object xValue = getInputValue(INPUT_X_ID, null);
        Object yValue = getInputValue(INPUT_Y_ID, null);
        Object zValue = getInputValue(INPUT_Z_ID, null);
        
        return xValue instanceof Integer && yValue instanceof Integer && zValue instanceof Integer;
    }
    
    /**
     * 清除输入验证状态
     */
    private void clearInputValidationState() {
        inputValidationError = null;
        hasInputValidationWarning = false;
        inputValidationWarning = null;
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
    
    private void updateOutputsWithPickedBlock() {
        // 基础输出
        outputValues.put(OUTPUT_BLOCK_ID, pickedBlockId);
        
        // 获取方块名称
        String blockName = getBlockDisplayName(pickedBlockId);
        outputValues.put(OUTPUT_BLOCK_NAME, blockName);
        
        // 位置相关输出
        if (pickedBlockPosition != null) {
            // 主要位置输出
            outputValues.put(OUTPUT_POSITION, pickedBlockPosition);
            
            // 方块中心坐标（浮点数）
            Vector3d center = new Vector3d(
                pickedBlockPosition.getX() + 0.5,
                pickedBlockPosition.getY() + 0.5,
                pickedBlockPosition.getZ() + 0.5
            );
            outputValues.put(OUTPUT_CENTER, center);
            
            // 坐标分量
            outputValues.put(OUTPUT_BLOCK_X_ID, pickedBlockPosition.getX());
            outputValues.put(OUTPUT_BLOCK_Y_ID, pickedBlockPosition.getY());
            outputValues.put(OUTPUT_BLOCK_Z_ID, pickedBlockPosition.getZ());
        } else {
            Coordinate defaultCoord = new Coordinate(0, 0, 0);
            Vector3d defaultCenter = new Vector3d(0.5, 0.5, 0.5);
            
            outputValues.put(OUTPUT_POSITION, defaultCoord);
            outputValues.put(OUTPUT_CENTER, defaultCenter);
            outputValues.put(OUTPUT_BLOCK_X_ID, 0);
            outputValues.put(OUTPUT_BLOCK_Y_ID, 0);
            outputValues.put(OUTPUT_BLOCK_Z_ID, 0);
        }
        
        // 方块状态数据
        outputValues.put(OUTPUT_BLOCK_STATE, pickedBlockStateData);
        
        // 方块实体检查
        boolean hasBlockEntity = checkHasBlockEntity(pickedBlockId, pickedBlockPosition);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY, hasBlockEntity);
        syncOutputPorts();
    }
    
    private void resetOutputs() {
        outputValues.put(OUTPUT_BLOCK_ID, "minecraft:air");
        outputValues.put(OUTPUT_BLOCK_NAME, "空气");
        outputValues.put(OUTPUT_POSITION, new Coordinate(0, 0, 0));
        outputValues.put(OUTPUT_CENTER, new Vector3d(0.5, 0.5, 0.5));
        outputValues.put(OUTPUT_BLOCK_STATE, null);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY, false);
        outputValues.put(OUTPUT_BLOCK_X_ID, 0);
        outputValues.put(OUTPUT_BLOCK_Y_ID, 0);
        outputValues.put(OUTPUT_BLOCK_Z_ID, 0);
        syncOutputPorts();
    }
    
    // === 数据记录和验证结果 ===
    

    
    /**
     * 方块验证数据记录
     * 包含实际方块ID和验证状态
     */
    private record BlockValidationData(String actualBlockId, boolean stateMatches) {}
    
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
        
        public static <T> ValidationResult<T> warning(String message, T data) {
            return new ValidationResult<>(false, message, data);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Optional<T> getData() {
            return Optional.ofNullable(data);
        }
        
        public boolean hasData() {
            return data != null;
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
            
            ClientWorld world = client.world;
            if (world == null) {
                return ValidationResult.failure("世界未加载");
            }
            
                    // 可选：检查玩家所在区块是否已加载
        BlockPos playerPos = client.player.getBlockPos();
        if (client.isInSingleplayer()) {
            try {
                // 使用 getChunk 替代已弃用的 isChunkLoaded
                // 如果区块未加载，getChunk 会返回 null 或抛出异常
                if (world.getChunk(playerPos) == null) {
                    return ValidationResult.failure("玩家所在区块未加载");
                }
            } catch (Exception e) {
                return ValidationResult.failure("玩家所在区块未加载");
            }
        }
            
            return ValidationResult.success();
            
        } catch (Exception e) {
            return ValidationResult.failure("世界状态检查异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证拾取的方块是否与客户端世界状态一致
     * 这对于多人游戏中的网络延迟和状态同步问题很重要
     * 
     * @param position 方块位置
     * @param expectedBlockId 期望的方块ID
     * @param expectedBlockStateData 期望的方块状态数据
     * @return ValidationResult<BlockValidationData> 验证结果
     */
    private ValidationResult<BlockValidationData> validatePickedBlock(Coordinate position, String expectedBlockId, 
                                                                    BlockStateData expectedBlockStateData) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client.world;
            
            // 如果世界状态无效，跳过验证
            if (world == null) {
                return ValidationResult.failure("世界未加载，跳过方块验证");
            }
            
            BlockPos pos = new BlockPos(position.getX(), position.getY(), position.getZ());
            
                    // 检查区块是否已加载
        try {
            // 使用 getChunk 替代已弃用的 isChunkLoaded
            if (world.getChunk(pos) == null) {
                return ValidationResult.failure("目标区块未加载，跳过方块验证");
            }
        } catch (Exception e) {
            return ValidationResult.failure("目标区块未加载，跳过方块验证");
        }
            
            // 获取世界中的实际方块状态
            BlockState actualBlockState = world.getBlockState(pos);
            String actualBlockId = Registries.BLOCK.getId(actualBlockState.getBlock()).toString();
            
            // 比较方块ID
            if (!actualBlockId.equals(expectedBlockId)) {
                String warningMessage = String.format(
                    "拾取方块 %s 与世界状态 %s 不一致 at (%d, %d, %d) - 可能由网络延迟引起", 
                    expectedBlockId, actualBlockId, position.getX(), position.getY(), position.getZ());
                return ValidationResult.warning(warningMessage, new BlockValidationData(actualBlockId, false));
            }
            
            // 可选：比较方块状态属性（更严格的验证）
            boolean stateMatches;
            if (expectedBlockStateData != null && !expectedBlockStateData.isEmpty()) {
                stateMatches = validateBlockStateProperties(actualBlockState, expectedBlockStateData);
                if (!stateMatches) {
                    String warningMessage = String.format(
                        "方块 %s 的状态属性不一致 at (%d, %d, %d) - 可能由网络延迟引起", 
                        expectedBlockId, position.getX(), position.getY(), position.getZ());
                    return ValidationResult.warning(warningMessage, new BlockValidationData(actualBlockId, false));
                }
            }
            
            return ValidationResult.success(new BlockValidationData(actualBlockId, true));
            
        } catch (Exception e) {
            String warningMessage = "方块验证异常: " + e.getMessage();
            return ValidationResult.failure(warningMessage);
        }
    }
    
    /**
     * 验证方块状态属性是否匹配
     * 
     * @param actualBlockState 世界中的实际方块状态
     * @param expectedBlockStateData 期望的方块状态数据
     * @return true 如果状态匹配，false 否则
     */
    private boolean validateBlockStateProperties(BlockState actualBlockState, BlockStateData expectedBlockStateData) {
        try {
            // 检查每个期望的属性是否与实际状态匹配
            for (Map.Entry<String, String> entry : expectedBlockStateData.entrySet()) {
                String propertyName = entry.getKey();
                String expectedValue = entry.getValue();
                
                // 查找对应的属性
                boolean propertyFound = false;
                for (var property : actualBlockState.getProperties()) {
                    if (property.getName().equals(propertyName)) {
                        String actualValue = actualBlockState.get(property).toString();
                        if (!actualValue.equals(expectedValue)) {
                            NodeCraft.LOGGER.debug("方块属性 {} 不匹配: 期望 {}, 实际 {}", 
                                propertyName, expectedValue, actualValue);
                            return false;
                        }
                        propertyFound = true;
                        break;
                    }
                }
                
                if (!propertyFound) {
                    NodeCraft.LOGGER.debug("方块属性 {} 在实际状态中未找到", propertyName);
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("验证方块状态属性时发生异常", e);
            return false;
        }
    }
    
        // === IBlockPickerCallback 实现 ===
    
    @Override
    public void onBlockPicked(Coordinate position, String blockId, BlockStateData blockStateData) {
        // 首先检查 Minecraft 世界状态
        ValidationResult<Void> worldValidation = validateWorldState();
        if (!worldValidation.isValid()) {
            NodeCraft.LOGGER.warn("节点 {} 无法拾取方块: {}", getId(), worldValidation.getMessage());
            onPickingCancelled();
            return;
        }
        
        // 验证拾取的方块是否与客户端世界状态一致（可选但推荐）
        ValidationResult<BlockValidationData> blockValidation = validatePickedBlock(position, blockId, blockStateData);
        if (!blockValidation.isValid()) {
            NodeCraft.LOGGER.warn("节点 {} 方块验证失败: {}", getId(), blockValidation.getMessage());
            // 注意：这里不返回，而是继续使用传入的方块信息，但会记录警告
            // 这样可以处理网络延迟导致的状态不一致问题
        }
        

        
        // 外部交互管理器通知方块被拾取
        this.pickedBlockPosition = position;
        this.pickedBlockId = blockId;
        this.pickedBlockStateData = blockStateData;
        this.hasPickedBlock = true;
        updateOutputsWithPickedBlock();
        
        // 标记节点为脏，触发重新计算
        markDirty();
        
        // 显示选择视觉反馈（不受节点可见性开关影响，确保拾取时总有反馈）。
        try {
            SelectionVisualFeedback.getInstance().showBlockSelection(
                getId().toString(),
                position,
                SelectionVisualFeedback.SelectionState.SELECTED
            );
            NodeCraft.LOGGER.info("节点 {} 选择视觉反馈已请求显示，位置: {}", getId(), position);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 显示选择视觉反馈失败: {}", getId(), e.getMessage(), e);
        }
        
        // 统一更新方块预览
        refreshBlockPreview();
        
        NodeCraft.LOGGER.info("节点 {} 接收到拾取的方块: {} at {}", getId(), blockId, position);
        if (NodeCraft.LOGGER.isDebugEnabled()) {
            NodeCraft.LOGGER.debug("节点 {} 方块状态数据: {}", getId(), 
                (blockStateData != null ? blockStateData.toString() : "无"));
            blockValidation.getData().ifPresent(validationData -> NodeCraft.LOGGER.debug("节点 {} 世界中实际方块: {}, 状态匹配: {}",
                getId(), validationData.actualBlockId(), validationData.stateMatches()));
        }
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
        pickedBlockId = "minecraft:air";
        pickedBlockStateData = null;
        
        // 清除选择视觉反馈
        SelectionVisualFeedback.getInstance().clearFeedback(getId().toString());
        
        // 隐藏方块预览
        clearBlockPreview();
        resetOutputs();
        
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
            
            if (showBlockPreview && hasPickedBlock && pickedBlockPosition != null && pickedBlockId != null) {
                createBlockPreview();
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
    
    private void createBlockPreview() {
        if (pickedBlockPosition != null && pickedBlockId != null) {
            try {
                // 检查世界状态，确保预览可以正常显示
                ValidationResult<Void> worldValidation = validateWorldState();
                if (!worldValidation.isValid()) {
                    NodeCraft.LOGGER.debug("节点 {} 跳过幽灵方块预览: {}", getId(), worldValidation.getMessage());
                    return;
                }
                
                // 先隐藏之前的预览
                clearBlockPreview();
                
                PreviewBlocksPayload payload = new PreviewBlocksPayload(List.of(
                    new PreviewBlock(
                        pickedBlockPosition.getX(),
                        pickedBlockPosition.getY(),
                        pickedBlockPosition.getZ(),
                        pickedBlockId
                    )
                ));
                PreviewStyle style = PreviewStyle.forGhostBlocks(1.0f, 1.0f, 1.0f, 0.5f, false, "original", 2.0f, 0.1f, 0);
                PreviewOptions options = style.toPreviewOptions(PreviewKind.BLOCKS);
                currentGhostBlockPreviewId = PreviewRenderer.getInstance()
                        .showPreview(getId().toString(), "ghost_block", payload, options);
                
                if (currentGhostBlockPreviewId != null) {
                    NodeCraft.LOGGER.debug("节点 {} 幽灵方块预览已显示: {} at {}, 预览ID: {}", 
                        getId(), pickedBlockId, pickedBlockPosition, currentGhostBlockPreviewId);
                } else {
                    NodeCraft.LOGGER.warn("节点 {} 幽灵方块预览创建失败: {} at {}", 
                        getId(), pickedBlockId, pickedBlockPosition);
                }
            } catch (NullPointerException e) {
                NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: 空指针异常 - PreviewRenderer或方块数据为null", getId(), e);
                currentGhostBlockPreviewId = null;
            } catch (IllegalArgumentException e) {
                NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: 参数异常 - 无效的方块ID: {}", getId(), pickedBlockId, e);
                currentGhostBlockPreviewId = null;
            } catch (Exception e) {
                NodeCraft.LOGGER.error("节点 {} 显示方块预览失败: {} - {}", getId(), 
                    e.getClass().getSimpleName(), e.getMessage(), e);
                currentGhostBlockPreviewId = null;
            }
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
        float buttonHeight = 22f;
        float smallGap = 4f;
        float baseHeight = 0f;

        baseHeight += smallGap;
        baseHeight += buttonHeight;

        NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
        if (interactionManager.isPendingBlockPick(getId().toString())) {
            baseHeight += smallGap;
            baseHeight += 30f;
        }

        if (inputValidationError != null && !inputValidationError.isEmpty()) {
            baseHeight += smallGap;
            baseHeight += 18f;
        }
        if (hasInputValidationWarning && inputValidationWarning != null && !inputValidationWarning.isEmpty()) {
            baseHeight += smallGap;
            baseHeight += 18f;
        }

        if (hasPickedBlock) {
            baseHeight += smallGap;
            baseHeight += buttonHeight;
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

            // === 1. 主要操作区 ===
            NodeEditorInteractionManager interactionManager = NodeEditorInteractionManager.getInstance();
            boolean isCurrentlyPicking = interactionManager.isPendingBlockPick(getId().toString());
            
            String pickButtonText = isCurrentlyPicking ? "取消拾取" : "拾取方块";
            float buttonHeight = ImGui.getFrameHeight();
            
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
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
            if (hasPickedBlock || hasInputCoordinates()) {
                // 默认展开状态显示区
                String headerText = hasPickedBlock ? "已选方块信息##info" : "输入坐标方块##info";
                boolean infoExpandedNow = ImGui.collapsingHeader(headerText);
                infoSectionExpanded = infoExpandedNow;
                if (infoExpandedNow) {
                    ImGui.indent(); // 缩进内容
                    addVerticalSpacing(getSmallPadding(), zoom);
                    
                    // 显示数据来源
                    if (hasPickedBlock) {
                        ImGui.textDisabled("来源:");
                        ImGui.sameLine();
                        ImGui.text("交互拾取");
                    } else {
                        ImGui.textDisabled("来源:");
                        ImGui.sameLine();
                        ImGui.text("输入坐标");
                    }
                    
                    // 方块名称和ID（紧凑布局）
                    String blockName = getBlockDisplayName(pickedBlockId);
                    ImGui.textDisabled("名称:");
                    ImGui.sameLine();
                    ImGui.text(blockName);
                    
                    ImGui.textDisabled("ID:");
                    ImGui.sameLine();
                    ImGui.text(pickedBlockId);
                    
                    // 位置信息（带悬停提示显示中心点）
                    if (pickedBlockPosition != null) {
                        ImGui.textDisabled("位置:");
                        ImGui.sameLine();
                        ImGui.text(String.format("%d, %d, %d", 
                            pickedBlockPosition.getX(), 
                            pickedBlockPosition.getY(), 
                            pickedBlockPosition.getZ()));
                        
                        // 悬停时显示中心点坐标
                        if (ImGui.isItemHovered()) {
                            Vector3d center = new Vector3d(
                                pickedBlockPosition.getX() + 0.5,
                                pickedBlockPosition.getY() + 0.5,
                                pickedBlockPosition.getZ() + 0.5
                            );
                            ImGui.setTooltip(String.format("中心点: %.2f, %.2f, %.2f", 
                                center.x, center.y, center.z));
                        }
                    }
                    
                    // 方块状态属性（可展开的树形结构）
                    if (pickedBlockStateData != null && !pickedBlockStateData.isEmpty()) {
                        ImGui.textDisabled("状态:");
                        ImGui.sameLine();
                        
                        // 使用树形节点展示属性列表
                        String stateLabel = "属性 (" + pickedBlockStateData.size() + ")";
                        boolean treeExpandedNow = ImGui.treeNode(stateLabel + "##blockState");
                        blockStateTreeExpanded = syncExpandableUiState(blockStateTreeExpanded, treeExpandedNow);
                        if (treeExpandedNow) {
                            for (Map.Entry<String, String> entry : pickedBlockStateData.entrySet()) {
                                ImGui.bulletText(entry.getKey() + ": " + entry.getValue());
                            }
                            ImGui.treePop();
                        }
                    }
                    
                    // 方块实体信息
                    boolean hasBlockEntity = checkHasBlockEntity(pickedBlockId, pickedBlockPosition);
                    if (hasBlockEntity) {
                        ImGui.bulletText("包含方块实体");
                    }
                    
                    ImGui.unindent();
                    addVerticalSpacing(getSmallPadding(), zoom);
                    ImGui.separator(); // 分隔线
                    addVerticalSpacing(getSmallPadding(), zoom);
                    
                    // 清除按钮（红色警告色）
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f); // 红色按钮
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f); // 悬停时稍亮
                    ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.1f, 0.1f, 1.0f); // 按下时稍暗
                    if (ImGui.button("清除选择##clearBlock", availableWidth, buttonHeight)) {
                        clearPickedBlock();
                        changed = true;
                    }
                    ImGui.popStyleColor(3); // 弹出三个颜色样式
                    
                    addVerticalSpacing(getSmallPadding(), zoom);
                }
            }

            // === 3. 高级设置区 ===
            boolean settingsExpandedNow = ImGui.collapsingHeader("Advanced Settings##settings");
            settingsSectionExpanded = settingsExpandedNow;
            if (settingsExpandedNow) {
                addVerticalSpacing(getSmallPadding(), zoom);
                
                // 输入端口状态说明
                if (hasPickedBlock) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.6f, 0.2f, 1.0f); // 橙色
                    ImGui.text("输入端口已禁用（已拾取方块）");
                    ImGui.popStyleColor();
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f); // 绿色
                    ImGui.text("输入端口可用（X、Y、Z坐标）");
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

            if (hasPickedBlock) {
                addVerticalSpacing(getSmallPadding(), zoom);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.2f, 0.2f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.1f, 0.1f, 1.0f);
                if (ImGui.button("Clear Selection##clearBlock", availableWidth, buttonHeight)) {
                    clearPickedBlock();
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
        // 显示输入验证错误
        if (inputValidationError != null && !inputValidationError.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f); // 红色
            ImGui.text("错误: " + inputValidationError);
            ImGui.popStyleColor();
            addVerticalSpacing(getSmallPadding(), zoom);
        }
        
        // 显示输入验证警告
        if (hasInputValidationWarning && inputValidationWarning != null && !inputValidationWarning.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f); // 橙色
            ImGui.text("警告: " + inputValidationWarning);
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
            state.put("maxDistance", maxDistance);
            state.put("includeFluids", includeFluids);
            state.put("showBlockPreview", showBlockPreview);
            
            // 保存拾取的方块信息
            if (hasPickedBlock && pickedBlockPosition != null) {
                Map<String, Object> pickedBlock = new HashMap<>();
                
                // 确保方块ID不为null
                pickedBlock.put("blockId", pickedBlockId != null ? pickedBlockId : "minecraft:air");
                pickedBlock.put("x", pickedBlockPosition.getX());
                pickedBlock.put("y", pickedBlockPosition.getY());
                pickedBlock.put("z", pickedBlockPosition.getZ());
                
                // 显式处理BlockStateData的序列化
                if (pickedBlockStateData != null && !pickedBlockStateData.isEmpty()) {
                    // 创建一个新的HashMap来确保类型安全
                    Map<String, String> stateDataCopy = new HashMap<>();
                    for (Map.Entry<String, String> entry : pickedBlockStateData.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            stateDataCopy.put(entry.getKey(), entry.getValue());
                        }
                    }
                    pickedBlock.put("blockStateData", stateDataCopy);
                } else {
                    // 显式保存空映射，确保序列化一致性
                    pickedBlock.put("blockStateData", new HashMap<String, String>());
                }
                
                state.put("pickedBlock", pickedBlock);
            }
            
            NodeCraft.LOGGER.debug("节点 {} 状态序列化完成，包含 {} 个属性", getId(), state.size());
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 状态序列化失败", getId(), e);
            // 返回基本状态，确保不会完全失败
            Map<String, Object> fallbackState = new HashMap<>();
            fallbackState.put("maxDistance", 100.0f);
            fallbackState.put("includeFluids", false);
            fallbackState.put("useHandItem", false);
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
            
            // 恢复设置属性，使用模式匹配（Java 14+）或传统方式
            restoreFloatSetting(stateMap, "maxDistance", this::setMaxDistance, 1.0f, 1000.0f);
            restoreBooleanSetting(stateMap, "includeFluids", this::setIncludeFluids);
            
            // showBlockPreview: 只读取新 key，保持状态键统一。
            if (stateMap.get("showBlockPreview") instanceof Boolean showPreview) {
                this.showBlockPreview = showPreview;
                NodeCraft.LOGGER.debug("节点 {} 恢复 showBlockPreview: {}", getId(), showPreview);
            }
            
            // 恢复拾取的方块信息
            restorePickedBlockData(stateMap);
            
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
     * 恢复拾取的方块数据
     */
    private void restorePickedBlockData(Map<String, Object> stateMap) {
        if (!(stateMap.get("pickedBlock") instanceof Map pickedBlockObj)) {
            if (stateMap.containsKey("pickedBlock")) {
                NodeCraft.LOGGER.warn("节点 {} 恢复拾取方块数据失败: 无效类型 {}", 
                    getId(), stateMap.get("pickedBlock").getClass().getSimpleName());
            }
            return;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> pickedBlockMap = (Map<String, Object>) pickedBlockObj;
            
            // 严格验证必需字段
            String blockId = validateAndGetString(pickedBlockMap, "blockId");
            Integer x = validateAndGetInteger(pickedBlockMap, "x");
            Integer y = validateAndGetInteger(pickedBlockMap, "y");
            Integer z = validateAndGetInteger(pickedBlockMap, "z");
            
            if (blockId == null || x == null || y == null || z == null) {
                NodeCraft.LOGGER.warn("节点 {} 恢复拾取方块数据失败: 缺少必需字段 - blockId={}, x={}, y={}, z={}", 
                    getId(), blockId, x, y, z);
                return;
            }
            
            // 恢复基本方块信息
            this.pickedBlockId = blockId;
            this.pickedBlockPosition = new Coordinate(x, y, z);
            this.hasPickedBlock = true;
            
            // 恢复方块状态数据
            restoreBlockStateData(pickedBlockMap);
            
            NodeCraft.LOGGER.debug("节点 {} 恢复拾取方块数据: {} at ({}, {}, {})", 
                getId(), blockId, x, y, z);
                
        } catch (ClassCastException e) {
            NodeCraft.LOGGER.error("节点 {} 恢复拾取方块数据失败: 类型转换异常", getId(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 恢复拾取方块数据失败: 未知异常", getId(), e);
        }
    }
    
    /**
     * 恢复方块状态数据
     */
    private void restoreBlockStateData(Map<String, Object> pickedBlockMap) {
        Object stateDataObj = pickedBlockMap.get("blockStateData");
        
        if (stateDataObj == null) {
            this.pickedBlockStateData = null;
            return;
        }
        
        if (!(stateDataObj instanceof Map stateDataMap)) {
            NodeCraft.LOGGER.warn("节点 {} 恢复方块状态数据失败: 无效类型 {}", 
                getId(), stateDataObj.getClass().getSimpleName());
            this.pickedBlockStateData = null;
            return;
        }
        
            	 try {
                BlockStateData restoredStateData = new BlockStateData();
            int validEntries = 0;
            int totalEntries = stateDataMap.size();
            
            for (Object entryObj : stateDataMap.entrySet()) {
                if (entryObj instanceof Map.Entry<?, ?> entry) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                            restoredStateData.put(key, value);
                        validEntries++;
                    } else {
                        NodeCraft.LOGGER.debug("节点 {} 跳过无效的状态数据条目: {} -> {}", 
                            getId(), entry.getKey(), entry.getValue());
                    }
                }
            }

                this.pickedBlockStateData = restoredStateData;
            
            NodeCraft.LOGGER.debug("节点 {} 恢复方块状态数据: {}/{} 个有效条目", 
                getId(), validEntries, totalEntries);
                
        } catch (Exception e) {
            NodeCraft.LOGGER.error("节点 {} 恢复方块状态数据失败", getId(), e);
            this.pickedBlockStateData = null;
        }
    }
    
    /**
     * 验证并获取字符串值
     */
    private String validateAndGetString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String str && !str.trim().isEmpty()) {
            return str;
        }
        return null;
    }
    
    /**
     * 验证并获取整数值
     */
    private Integer validateAndGetInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer integer) {
            return integer;
        } else if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
    
    // === 预览状态管理 ===
    
    /**
     * 检查节点是否在游戏中可见
     * 通过编辑器的可见性设置来控制预览显示
     *
     * @return true 如果节点在游戏中可见，false 如果被设置为 "Hide in Game"
     */
    private boolean isNodeVisibleInGame() {
        try {
            // 尝试获取当前的节点编辑器实例
            com.nodecraft.gui.editor.impl.ImGuiNodeEditor editor =
                com.nodecraft.gui.editor.impl.ImGuiNodeEditor.getInstance();

            if (editor != null) {
                // 检查节点是否在编辑器中被标记为可见
                boolean isVisible = editor.isNodeVisible(getId());
                NodeCraft.LOGGER.debug("节点 {} 游戏内可见性检查: {}", getId(), isVisible);
                return isVisible;
            } else {
                // 如果无法获取编辑器实例，默认为可见
                NodeCraft.LOGGER.debug("节点 {} 无法获取编辑器实例，默认为可见", getId());
                return true;
            }
        } catch (Exception e) {
            // 如果检查过程中出现异常，默认为可见，避免影响正常功能
            NodeCraft.LOGGER.warn("节点 {} 检查游戏内可见性时出现异常，默认为可见: {}", getId(), e.getMessage());
            return true;
        }
    }

    // === 新增的辅助方法 ===
    
    /**
     * 获取方块的显示名称
     * 
     * @param blockId 方块ID
     * @return 人类可读的方块名称
     */
    private String getBlockDisplayName(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return "未知方块";
        }
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return getDefaultBlockName(blockId);
            }
            
                         // 尝试从注册表获取方块
             Identifier identifier = Identifier.tryParse(blockId);
             if (identifier == null) {
                 return getDefaultBlockName(blockId);
             }
             Block block = Registries.BLOCK.get(identifier);
            
            if (block != Blocks.AIR) {
                // 获取方块的翻译文本
                Text displayName = block.getName();
                if (displayName != null) {
                    return displayName.getString();
                }
            }
            
            return getDefaultBlockName(blockId);
            
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("节点 {} 获取方块显示名称失败: {}", getId(), e.getMessage());
            return getDefaultBlockName(blockId);
        }
    }
    
    /**
     * 获取默认的方块名称（当无法从游戏获取时）
     */
    private String getDefaultBlockName(String blockId) {
        if (blockId == null) return "未知方块";
        
        // 简单的名称转换：去掉命名空间，将下划线替换为空格，首字母大写
        String name = blockId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }
        
        name = name.replace("_", " ");
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        
        return name;
    }
    

    
    /**
     * 检查方块是否有方块实体
     * 使用 Minecraft 的 BlockState.hasBlockEntity() 方法，100% 准确且无需手动维护列表
     * 
     * @param blockId 方块ID
     * @param position 方块位置
     * @return 是否有方块实体
     */
    private boolean checkHasBlockEntity(String blockId, Coordinate position) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return false;
            }
            
            BlockState blockState;
            
            // 优先从世界中获取实际的方块状态（如果有位置信息）
            if (position != null && client.world != null) {
                BlockPos blockPos = new BlockPos(position.getX(), position.getY(), position.getZ());
                blockState = client.world.getBlockState(blockPos);
            } else {
                // 否则从方块ID获取默认状态
                Identifier identifier = Identifier.tryParse(blockId);
                if (identifier == null) {
                    return false;
                }
                
                Block block = Registries.BLOCK.get(identifier);
                if (block == Blocks.AIR) {
                    return false;
                }
                
                blockState = block.getDefaultState();
            }
            
            // 使用 Minecraft 内置的方法检查是否有方块实体
            return blockState.hasBlockEntity();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("节点 {} 检查方块实体失败: {}", getId(), e.getMessage());
            return false;
        }
    }
} 
