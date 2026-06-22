package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.nodesystem.preview.gizmo.GizmoInteractionService;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.client.input.NodecraftInputSystem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.HashMap;

/**
 * 节点编辑器交互编排：交互状态机、悬停高亮、每帧 update 与拾取请求 API。
 * <p>
 * 职责已拆分：{@link EditorModeCameraService}（编辑视图 / 中键视角）、
 * {@link WorldPickingService}（鼠标射线、方块 raycast、射线缓存）、
 * {@link AreaPreviewStyleSettings}（区域框选预览样式）。
 * 本类仍负责交互模式 handler 与 ImGui/世界输入门控。
 * <p>
 * 单例，保证全局唯一交互状态。
 */
public class NodeEditorInteractionManager {

    // ================= 常量定义 =================
    private static final String OWNER_ID = "editor_interaction_manager"; // 本类作为预览拥有者的标识符

    private final WorldPickingService worldPicking = new WorldPickingService();
    private final EditorModeCameraService editorModeCamera = new EditorModeCameraService();
    private final AreaPreviewStyleSettings areaPreviewStyle = new AreaPreviewStyleSettings();

    private final HoveredBlockHighlightService hoveredBlockHighlight = new HoveredBlockHighlightService(OWNER_ID);
    private boolean previousGizmoLeftMouseDown = false;

    // ================= 统一的交互状态管理 =================
    private final InteractionSessionState interactionState;
    
    // ================= 交互模式处理器管理 =================
    private final Map<EditorInteractionMode, InteractionModeHandler> modeHandlers = new HashMap<>();
    private final InteractionModeRouter interactionRouter;
    
    /**
     * 编辑器交互模式枚举
     */
    public enum EditorInteractionMode {
        NONE,           // 默认状态，自由浏览
        BLOCK_PICKING,  // 正在等待方块拾取
        AREA_SELECTION, // 区域选择模式
        ENTITY_PICKING  // 实体拾取模式
        // 未来可扩展更多模式
    }
    
    /**
     * 通用交互回调接口
     * 所有交互模式的回调都应该实现此接口
     */
    public interface IInteractionCallback {
        /**
         * 当交互被取消时调用
         */
        default void onInteractionCancelled() {
            // 默认空实现
        }
    }
    
    /**
     * 区域选择回调接口
     */
    public interface IAreaSelectionCallback extends IInteractionCallback {
        /**
         * 当区域选择完成时调用
         * @param startPos 起始位置
         * @param endPos 结束位置
         */
        void onAreaSelected(Coordinate startPos, Coordinate endPos);
        
        /**
         * 当第一个点被选择时调用
         * @param position 选择的位置
         */
        default void onFirstPointSelected(Coordinate position) {
            // 默认空实现
        }
    }
    
    /**
     * 实体拾取回调接口
     */
    public interface IEntityPickerCallback extends IInteractionCallback {
        /**
         * 当实体被拾取时调用
         * @param entityId 实体ID
         * @param entityType 实体类型
         * @param position 实体位置
         */
        void onEntityPicked(String entityId, String entityType, Coordinate position);
    }
    
    /**
     * 交互模式处理器接口
     * 定义了每种交互模式的标准处理流程
     */
    public interface InteractionModeHandler {
        /**
         * 进入交互模式时调用
         * @param nodeId 节点ID
         * @param callback 回调接口
         */
        void onEnter(String nodeId, IInteractionCallback callback);
        
        /**
         * 更新交互状态（每帧调用）
         * @param hoveredBlock 当前悬停的方块
         * @param hitResult 射线检测结果
         * @param isLeftMouseClicked 是否左键点击
         * @param isRightMouseClicked 是否右键点击
         */
        void onUpdate(Coordinate hoveredBlock, BlockHitResult hitResult, 
                     boolean isLeftMouseClicked, boolean isRightMouseClicked);
        
        /**
         * 取消交互模式时调用
         */
        void onCancel();

        /**
         * 交互成功完成时调用（不应触发取消回调）
         */
        void onComplete();
        
        /**
         * 获取交互模式的显示名称
         */
        String getDisplayName();
        
        /**
         * 获取交互提示信息
         */
        String getHintMessage();
    }
    
    private NodeEditorInteractionManager() {
        // 私有构造函数，单例模式
        interactionState = new InteractionSessionState(modeHandlers);
        interactionRouter = new InteractionModeRouter(modeHandlers, interactionState, this::handleBlockPicking);
        initializeModeHandlers();
    }
    
    /**
     * 初始化交互模式处理器
     */
    private void initializeModeHandlers() {
        modeHandlers.put(
            EditorInteractionMode.BLOCK_PICKING,
            new BlockPickingInteractionHandler(
                    interactionState::clear,
                    interactionState::complete
            )
        );
        modeHandlers.put(
            EditorInteractionMode.AREA_SELECTION,
            new AreaSelectionInteractionHandler(
                areaPreviewStyle,
                OWNER_ID,
                    interactionState::clear,
                    interactionState::complete
            )
        );
        modeHandlers.put(
            EditorInteractionMode.ENTITY_PICKING,
            new EntityPickingInteractionHandler(
                    interactionState::clear,
                    interactionState::complete
            )
        );
    }
    
    /**
     * 单例持有者，利用JVM类加载机制保证线程安全
     */
    private static class SingletonHolder {
        private static final NodeEditorInteractionManager INSTANCE = new NodeEditorInteractionManager();
    }
    
    /**
     * 获取单例实例
     */
    public static NodeEditorInteractionManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    // ================= 新增：编辑模式管理 =================
    
    /**
     * 进入编辑模式
     * 当打开 NodeCraft UI 时调用
     */
    public void enterEditorMode() {
        editorModeCamera.enterEditorMode();
    }
    
    /**
     * 退出编辑模式
     * 当关闭 NodeCraft UI 时调用
     */
    public void exitEditorMode() {
        if (!editorModeCamera.isInEditorMode()) {
            NodeCraft.LOGGER.debug("不在编辑模式中，跳过退出");
            return;
        }

        try {
            hoveredBlockHighlight.clear();
            interactionState.clear();
            worldPicking.invalidateRayCache();
            editorModeCamera.exitEditorMode();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("退出编辑模式时出错", e);
        }
    }
    
    /**
     * 检查是否在编辑模式中
     */
    public boolean isInEditorMode() {
        return editorModeCamera.isInEditorMode();
    }
    
    /**
     * 检查是否在交互模式中
     * @return true 如果当前在任何交互模式中（方块拾取、区域选择等）
     */
    public boolean isInInteractionMode() {
        return interactionState.isInInteractionMode();
    }

    /**
     * 区域框选预览样式（视图菜单与节点渲染共用）。
     */
    public AreaPreviewStyleSettings getAreaPreviewStyle() {
        return areaPreviewStyle;
    }

    // ================= 鼠标射线与世界拾取（委托 {@link WorldPickingService}）=================

    /**
     * 将屏幕上的 2D 鼠标坐标转换为 3D 世界中的射线。
     */
    public WorldPickingService.Ray getRayFromMouse(float mouseX, float mouseY) {
        return worldPicking.getRayFromMouse(mouseX, mouseY);
    }

    public void invalidateRayCache() {
        worldPicking.invalidateRayCache();
    }

    // ================= 新增：实时更新和高亮 =================
    
    /**
     * 在每一帧的渲染或逻辑更新中调用（性能优化版本）
     * 负责实时高亮鼠标悬停的方块，使用射线缓存减少重复计算
     * 
     * @param mouseX 当前鼠标X坐标
     * @param mouseY 当前鼠标Y坐标
     * @param isMiddleMouseDown 中键是否按下
     * @param isLeftMouseClicked 左键是否刚点击
     * @param isMouseOverImGui 鼠标是否在ImGui界面上
     */
    public void update(float mouseX, float mouseY, boolean isMiddleMouseDown, boolean isLeftMouseClicked, boolean isRightMouseClicked, boolean isMouseOverImGui) {
        if (!editorModeCamera.isInEditorMode()) {
            return;
        }
        
        // 调试信息：只在实际点击或状态变化时记录
        if (isLeftMouseClicked || isRightMouseClicked) {
            NodeCraft.LOGGER.debug("NodeEditorInteractionManager.update() - 鼠标点击({}, {}) 左键:{} 中键:{} ImGui:{} 交互模式:{}", 
                mouseX, mouseY, true, isMiddleMouseDown, isMouseOverImGui, interactionState.getMode());
        }
        
        try {
            // 交互模式下需要允许 ImGui 光标与世界交互（参考 chronoblocks 的 UI+世界交互行为）
            boolean allowWorldInteraction = !isMouseOverImGui || interactionState.isInInteractionMode();

            if (allowWorldInteraction) {
                editorModeCamera.updateMiddleMouseCamera(mouseX, mouseY, isMiddleMouseDown, !isMouseOverImGui);
                
                // 使用输入系统中已验证的射线换算，避免屏幕边缘拾取偏移
                BlockHitResult hitResult = NodecraftInputSystem.raycastFromMouse();
                Coordinate newHoveredBlock = null;
                
                if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                    BlockPos blockPos = hitResult.getBlockPos();
                    newHoveredBlock = new Coordinate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                }
                
                // 更新高亮预览
                hoveredBlockHighlight.updateHoveredBlockHighlight(newHoveredBlock);
                
                // 处理鼠标点击事件
                handleMouseClickEvents(newHoveredBlock, hitResult, isLeftMouseClicked, isRightMouseClicked);
                updateGizmoInteraction(mouseX, mouseY, true);
            } else {
                boolean wasMiddleDragging = editorModeCamera.isMiddleMouseDragging();
                editorModeCamera.updateMiddleMouseCamera(mouseX, mouseY, isMiddleMouseDown, false);
                if (wasMiddleDragging) {
                    NodeCraft.LOGGER.debug("鼠标移到ImGui界面上，停止视角控制");
                }
                
                // 清除方块高亮（鼠标在UI上时不应该高亮游戏中的方块）
                hoveredBlockHighlight.clear();
                
                // 清除射线缓存（鼠标在UI上时不需要缓存）
                invalidateRayCache();
                updateGizmoInteraction(mouseX, mouseY, false);
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新编辑模式状态时出错", e);
        }
    }
    
    /**
     * 处理鼠标点击事件
     */
    private void updateGizmoInteraction(float mouseX, float mouseY, boolean allowWorldInteraction) {
        boolean draggingGizmo = GizmoInteractionService.getInstance().isDraggingGizmo();
        if (interactionState.isInInteractionMode() && !draggingGizmo) {
            previousGizmoLeftMouseDown = false;
            return;
        }
        if (editorModeCamera.isMiddleMouseDragging() && !draggingGizmo) {
            previousGizmoLeftMouseDown = false;
            return;
        }
        if (!allowWorldInteraction && !draggingGizmo) {
            previousGizmoLeftMouseDown = false;
            return;
        }

        WorldPickingService.Ray ray = worldPicking.getCachedOrComputeRay(mouseX, mouseY);
        long windowHandle = ImGuiRenderer.getInstance().getActiveInputWindowHandle();
        if (windowHandle == 0L) {
            previousGizmoLeftMouseDown = false;
            return;
        }

        boolean leftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean leftClicked = leftDown && !previousGizmoLeftMouseDown;
        boolean leftReleased = !leftDown && previousGizmoLeftMouseDown;
        previousGizmoLeftMouseDown = leftDown;

        GizmoInteractionService.getInstance().update(ray, leftClicked, leftDown, leftReleased);
    }

    private void handleMouseClickEvents(Coordinate hoveredBlock, BlockHitResult hitResult, boolean isLeftMouseClicked, boolean isRightMouseClicked) {
        try {
            // 如果中键正在被按下（用于视角控制），跳过其他鼠标事件处理
            if (editorModeCamera.isMiddleMouseDragging()) {
                return;
            }
            interactionRouter.route(hoveredBlock, hitResult, isLeftMouseClicked, isRightMouseClicked);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理鼠标点击事件时出错", e);
        }
    }
    
    /**
     * 处理方块拾取
     */
    private void handleBlockPicking(Coordinate coordinate, BlockHitResult hitResult) {
        try {
            BlockSnapshot snapshot = BlockSnapshot.fromHitResult(hitResult);
            
            // 使用统一的拾取处理逻辑
            processBlockPicking(coordinate, snapshot.blockId(), snapshot.stateData());
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理方块拾取时出错", e);
            interactionState.clear();
        }
    }
    
    // ================= 新增：通用交互请求管理 =================
    
    /**
     * 请求交互模式（通用API）
     * @param mode 交互模式
     * @param nodeId 请求交互的节点ID
     * @param callback 交互完成后的回调
     */
    public void requestInteraction(EditorInteractionMode mode, String nodeId, IInteractionCallback callback) {
        if (!editorModeCamera.isInEditorMode()) {
            NodeCraft.LOGGER.warn("不在编辑模式中，无法请求交互");
            callback.onInteractionCancelled();
            return;
        }
        
        if (mode == EditorInteractionMode.NONE) {
            NodeCraft.LOGGER.warn("无效的交互模式: NONE");
            callback.onInteractionCancelled();
            return;
        }
        
        // 获取对应的处理器
        InteractionModeHandler handler = modeHandlers.get(mode);
        if (handler == null) {
            NodeCraft.LOGGER.error("未找到交互模式处理器: {}", mode);
            callback.onInteractionCancelled();
            return;
        }
        
        // 清除当前交互状态
        interactionState.clear();
        
        // 设置新的交互状态
        interactionState.setInteraction(mode, nodeId, callback);
        
        // 启动处理器
        handler.onEnter(nodeId, callback);
        
        NodeCraft.LOGGER.info("节点 {} 请求交互模式: {}", nodeId, handler.getDisplayName());
    }

    /**
     * 请求实体拾取
     * @param nodeId 请求拾取的节点ID
     * @param callback 拾取完成后的回调
     */
    public void requestEntityPicking(String nodeId, IEntityPickerCallback callback) {
        requestInteraction(EditorInteractionMode.ENTITY_PICKING, nodeId, callback);
    }

    /**
     * 请求区域选择（两点框选）
     * @param nodeId 请求选择的节点ID
     * @param callback 区域选择回调
     */
    public void requestAreaSelection(String nodeId, IAreaSelectionCallback callback) {
        requestInteraction(EditorInteractionMode.AREA_SELECTION, nodeId, callback);
    }
    
    // ================= 拾取请求管理（向后兼容） =================
    
    /**
     * 请求方块拾取（新的API）
     * @param nodeId 请求拾取的节点ID
     * @param callback 拾取完成后的回调
     */
    public void requestBlockPick(String nodeId, IBlockPickerCallback callback) {
        // 使用统一的交互请求API
        requestInteraction(EditorInteractionMode.BLOCK_PICKING, nodeId, callback);
    }
    
    /**
     * 取消当前交互
     */
    public void cancelCurrentInteraction() {
        if (interactionState.isInInteractionMode()) {
            interactionState.clear();
        }
    }
    
    /**
     * 取消方块拾取请求（向后兼容）
     */
    public void cancelBlockPick() {
        if (interactionState.isPendingBlockPick()) {
            cancelCurrentInteraction();
        }
    }

    /**
     * 取消区域选择（仅当当前处于区域选择模式时生效）
     */
    public void cancelAreaSelection() {
        if (interactionState.getMode() == EditorInteractionMode.AREA_SELECTION) {
            cancelCurrentInteraction();
        }
    }

    /**
     * 检查指定节点是否在等待拾取
     */
    public boolean isPendingBlockPick(String nodeId) {
        return interactionState.isPendingBlockPick(nodeId);
    }

    /**
     * 检查指定节点是否正在等待区域选择
     */
    public boolean isPendingAreaSelection(String nodeId) {
        return interactionState.getMode() == EditorInteractionMode.AREA_SELECTION
            && interactionState.isCurrentInteractionNode(nodeId);
    }

    /**
     * 检查指定节点是否是当前交互的节点
     */
    public boolean isCurrentInteractionNode(String nodeId) {
        return interactionState.isCurrentInteractionNode(nodeId);
    }

    /**
     * 处理玩家在游戏世界中的左键点击事件
     * 这是推荐的拾取交互方式，与编辑器内的左键点击逻辑保持一致
     * 
     * @param position 点击的方块位置
     * @param blockId 方块ID
     * @param blockStateData 方块状态数据
     * @return 是否处理了此事件
     */
    public boolean handleBlockLeftClick(Coordinate position, String blockId, BlockStateData blockStateData) {
        NodeCraft.LOGGER.debug("收到方块左键点击事件: {} at {}", blockId, position);
        
        // 使用统一的拾取处理逻辑
        return processBlockPicking(position, blockId, blockStateData);
    }

    /**
     * 强制取消所有交互（用于紧急情况或编辑器关闭时）
     */
    public void forceExitAllInteractions() {
        if (interactionState.isInInteractionMode()) {
            NodeCraft.LOGGER.info("强制退出所有交互模式");
            
            // 清除高亮预览
            hoveredBlockHighlight.clear();
            
            // 取消当前交互
            cancelCurrentInteraction();
        }
    }
    
    // ================= 辅助工具方法 =================
    
    /**
     * 处理方块拾取的通用逻辑
     * 统一的拾取处理方法，被新旧API共同使用
     * 
     * @param coordinate 方块坐标
     * @param blockId 方块ID
     * @param blockStateData 方块状态数据
     * @return 是否成功处理了拾取
     */
    private boolean processBlockPicking(Coordinate coordinate, String blockId, BlockStateData blockStateData) {
        if (!interactionState.isPendingBlockPick()) {
            return false;
        }
        
        try {
            NodeCraft.LOGGER.info("处理方块拾取: {} at {}", blockId, coordinate);
            
            // 通知回调方块被拾取
            IBlockPickerCallback callback = interactionState.getBlockPickerCallback();
            if (callback != null) {
                callback.onBlockPicked(coordinate, blockId, blockStateData);
            }
            
            // 成功完成交互，不触发取消回调
            interactionState.complete();
            
            return true; // 事件已被处理
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理方块拾取时出错", e);
            interactionState.clear();
            return false;
        }
    }
} 
