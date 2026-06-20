package com.nodecraft.gui.editor.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.NodeEditorFactory;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.SubgraphExtractionService;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * ImGui节点编辑器实现
 */
public class ImGuiNodeEditor implements INodeEditor, ICanvasEditor {

    private static ImGuiNodeEditor instance;

    // 子组件
    private final ImGuiNodeRenderer renderer;
    private final ImGuiNodeInteraction interaction;
    private final ImGuiNodeIO io;
    private final ImGuiNodeMenus menus;
    private final ImGuiNodeHistory history;
    private final ImGuiNodeClipboard clipboard;

    // 编辑器状态
    private boolean isOpen = false;
    private NodeGraph currentGraph;
    private Map<UUID, NodePosition> nodePositions = new HashMap<>();
    // portScreenPositions 存储的是端口的屏幕坐标 (已缩放)，每帧更新
    private Map<UUID, Map<String, ImVec2>> portScreenPositions = new HashMap<>();

    // 节点选中状态
    private UUID selectedNodeId = null;
    private final java.util.Set<UUID> selectedNodeIds = new HashSet<>();

    // 画布视图状态变量
    private float canvasZoom = 1.0f;
    private float canvasOffsetX = 0;
    private float canvasOffsetY = 0;
    private boolean showGrid = true;

    // 节点显示模式
    private int nodeDisplayMode = 0; // 0=完整, 1=紧凑, 2=仅图标, 3=仅文本
    private boolean showNodePreviews = true;

    // 节点自定义颜色存储
    private final Map<UUID, Integer> nodeCustomColors = new HashMap<>();

    // 节点状态存储
    private final java.util.Set<UUID> disabledNodes = new HashSet<>();
    private final java.util.Set<UUID> hiddenNodes = new HashSet<>();

    private static final long AUTO_PREVIEW_DEBOUNCE_MS = 250L;
    private static final long AUTO_PREVIEW_POLL_INTERVAL_MS = 750L;
    private long lastObservedDirtyVersion = -1L;
    private long pendingAutoPreviewVersion = -1L;
    private long lastAutoPreviewDirtyChangeAt = 0L;
    private long lastAutoPreviewExecutionAt = 0L;
    private NodeExecutor autoPreviewExecutor = null;
    private long graphDirtyEpoch = 0L;
    private final java.util.Set<UUID> invalidatedNodeIds = new HashSet<>();

    /**
     * 获取单例实例
     * @return 节点编辑器实例
     */
    public static ImGuiNodeEditor getInstance() {
        if (instance == null) {
            instance = new ImGuiNodeEditor();
        }
        return instance;
    }

    /**
     * 私有构造函数，用于实现单例模式。
     * 初始化所有编辑器子组件。
     */
    private ImGuiNodeEditor() {
        this.renderer = new ImGuiNodeRenderer(this);
        this.io = new ImGuiNodeIO(this);
        this.interaction = new ImGuiNodeInteraction(this);
        this.menus = new ImGuiNodeMenus(this, this.io);
        this.history = new ImGuiNodeHistory(this);
        this.clipboard = new ImGuiNodeClipboard(this);
        BaseNode.addDirtyListener(this::handleNodeDirty);
    }

    private void handleNodeDirty(BaseNode node, long dirtyVersion) {
        if (node == null || io == null || currentGraph == null) {
            return;
        }
        if (currentGraph.getNode(node.getId()) == null) {
            return;
        }
        invalidatedNodeIds.clear();
        invalidatedNodeIds.addAll(currentGraph.getDirtyImpactNodeIds(node.getId()));
        graphDirtyEpoch++;
        io.markDirty();
        NodeCraft.LOGGER.debug(
                "Graph dirty version bumped from node {} dirty version {}. Impacted nodes: {}, graphDirtyEpoch={}",
                node.getId(),
                dirtyVersion,
                invalidatedNodeIds.size(),
                graphDirtyEpoch
        );
    }

    private void markGraphStructureDirty() {
        if (io == null) {
            return;
        }
        invalidatedNodeIds.clear();
        graphDirtyEpoch++;
        io.markDirty();
        NodeCraft.LOGGER.debug("Graph structure dirty. graphDirtyEpoch={}", graphDirtyEpoch);
    }

    public void notifyGraphStructureChanged() {
        markGraphStructureDirty();
    }

    /**
     * 初始化节点编辑器，如果当前图为空，则创建一个默认图并添加一些示例节点。
     */
    @Override
    public void init() {
        if (currentGraph == null) {
            currentGraph = new NodeGraph("默认节点图");

            // 添加一些示例节点用于测试和演示
            try {
                NodeCraft.LOGGER.info("正在添加示例节点用于测试...");

                String[] testNodeTypes = {
                        // 基础输入节点（如果存在）
                        "input.numeric.integer", "input.numeric.float_slider",
                    "input.basic.boolean_toggle", "input.basic.text_input",
                        // 数学运算节点
                        "math.scalar_math.addition", "math.scalar_math.multiplication", "math.scalar_math.division",
                        "math.scalar_math.subtraction", "math.scalar_math.power", "math.scalar_math.clamp",
                        // 数学逻辑节点（如果存在）
                        "math.logic.and", "math.logic.or", "math.logic.not"
                };

                int addedCount = 0;
                float startX = 50f;
                float startY = 50f;
                float spacingX = 200f; // 节点水平间距
                float spacingY = 150f; // 节点垂直间距

                // 暂停历史记录，防止这些初始节点被记录
                history.pauseRecording();
                try {
                    for (int i = 0; i < testNodeTypes.length && addedCount < 10; i++) { // 添加最多10个节点
                        String nodeType = testNodeTypes[i];
                        try {
                            float x = startX + (addedCount % 4) * spacingX; // 每行4个节点
                            float y = startY + (addedCount / 4) * spacingY;

                            INode node = addNode(nodeType, x, y);
                            if (node != null) {
                                NodeCraft.LOGGER.info("成功添加示例节点: {} 在位置 ({}, {})", nodeType, x, y);
                                addedCount++;
                            }
                        } catch (Exception e) {
                            NodeCraft.LOGGER.debug("无法创建节点类型 {}: {}", nodeType, e.getMessage());
                        }
                    }
                } finally {
                    history.resumeRecording(); // 确保恢复记录
                }

                if (addedCount > 0) {
                    NodeCraft.LOGGER.info("成功添加了 {} 个示例节点", addedCount);
                } else {
                    NodeCraft.LOGGER.warn("未能添加任何示例节点，请使用右键菜单手动添加节点");
                }

            } catch (Exception e) {
                NodeCraft.LOGGER.error("添加示例节点时出错: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 打开编辑器界面。
     */
    @Override
    public void open() {
        NodeCraft.LOGGER.info("ImGuiNodeEditor打开");
        isOpen = true;
    }

    /**
     * 关闭编辑器界面。
     */
    @Override
    public void close() {
        NodeCraft.LOGGER.info("ImGuiNodeEditor关闭");
        isOpen = false;
    }

    /**
     * 检查编辑器是否处于打开状态。
     * @return true 如果编辑器已打开，否则返回false。
     */
    @Override
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Minecraft DrawContext 渲染方法（通常留空，实际ImGui渲染在 renderImGui 中）。
     * @param context Minecraft 绘制上下文。
     * @param mouseX 鼠标X坐标。
     * @param mouseY 鼠标Y坐标。
     * @param delta 帧时间差。
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 留空，渲染由 NodeCraftClient 中的 HudRenderCallback 处理
    }

    /**
     * 主ImGui渲染方法。
     * 此方法由外部调用（例如，CanvasComponent），负责整个节点编辑器的ImGui渲染循环。
     */
    public void renderImGui() {
        if (!isOpen) {
            return;
        }

        try {
            // 获取当前画布子窗口的信息
            ImVec2 canvasPos = ImGui.getWindowPos();
            float canvasWidth = ImGui.getWindowWidth();
            float canvasHeight = ImGui.getWindowHeight();

            // 获取帧时间差，用于动画和动态效果
            float deltaTime = ImGui.getIO().getDeltaTime();
            interaction.updatePortHighlightAnimation(deltaTime);

            ImDrawList drawList = ImGui.getWindowDrawList();

            // 清除端口屏幕位置缓存，每帧重新计算
            portScreenPositions.clear();

            // 获取当前鼠标位置
            ImVec2 mousePos = ImGui.getIO().getMousePos();

            // 2.0 在绘制前先处理节点拖动位移，确保本帧节点背景与自定义UI同步移动
            applyNodeDragMovementBeforeRender();

            // 2.05 清理动态端口变化后产生的悬挂连线（端口已不存在）
            cleanupDanglingConnections();

            // 2. 先计算所有节点的尺寸和端口位置
            // 这一步会更新 NodePosition 中的 width/height 字段，并填充 portScreenPositions
            renderer.calculatePortPositions(canvasPos, currentGraph, nodePositions, portScreenPositions);

            // 3. 渲染背景连接线（未选中节点之间的连接）
            renderer.renderConnectionsDirect(drawList, currentGraph, portScreenPositions, selectedNodeIds);

            // 4. 渲染节点（包含节点主体、标题和自定义UI）。
            // 节点渲染会设置 ImGui.invisibleButton，并更新 ImGui.isItemActive() 状态。
            // 在渲染前，在主窗口上下文里预先计算本帧点击目标节点（坐标在此处是正确的）。
            if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)) {
                UUID clickTargetNodeId = getNodeIdUnderMouse(mousePos.x, mousePos.y);
                if (clickTargetNodeId == null) {
                    Map.Entry<UUID, String> clickedPort = interaction.getClickedPort(mousePos, portScreenPositions);
                    if (clickedPort != null) {
                        clickTargetNodeId = clickedPort.getKey();
                    }
                }
                interaction.setPendingClickTargetNodeId(clickTargetNodeId);
            } else {
                interaction.setPendingClickTargetNodeId(null);
            }
            // 【关键修改点】：节点选择和拖拽的启动和持续移动逻辑现在都移到 ImGuiNodeRenderer 内部处理。
            renderer.renderNodesDirect(drawList, canvasPos, currentGraph, nodePositions, portScreenPositions, selectedNodeIds);

            // 5. 渲染前景连接线（与选中节点相关的连接，显示在节点上方）
            renderer.renderForegroundConnections(drawList, currentGraph, portScreenPositions, selectedNodeIds);

            // 6. 更新端口和连接的悬停状态
            // 这两个方法会更新 interaction.hoveredNodeId, hoveredPortId, isHoveredPortOutput, isHoveringConnection 等
            interaction.updateHoveredPort(mousePos, portScreenPositions, currentGraph);
            interaction.updateHoveredConnection(mousePos, portScreenPositions, currentGraph);
            renderHoveredPortTooltip(currentGraph, interaction);

            // 6.5 双击连接线自动插入中继节点（Reroute）
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)
                    && interaction.isHoveringConnection()
                    && !interaction.isCreatingConnection()
                    && !interaction.isDraggingNode()
                    && !interaction.isBoxSelecting()
                    && currentGraph != null) {
                // 捕获鼠标，避免被画布层当作空白处双击处理
                ImGui.getIO().setWantCaptureMouse(true);
                insertRerouteNodeOnHoveredConnection(mousePos, canvasPos);
            }

            // 7. 处理进行中的连接创建（绘制预览线，鼠标释放时完成连接）
            // 此方法内部会检查 interaction.isCreatingConnection()
            interaction.handleActiveConnectionCreation(currentGraph, portScreenPositions);

            // 8. 处理进行中的框选的更新和完成
            // 此方法内部会检查 interaction.isBoxSelecting()，并在鼠标释放时处理框选结果
            interaction.handleBoxSelection(mousePos, canvasPos, nodePositions, currentGraph);

            // 9. 处理画布平移（如果正在平移）
            interaction.handleCanvasPanning(canvasPos);

            // 10. 处理鼠标左键点击：节点选择、选择清除、框选启动、画布平移
            // 注意：当 ImGui 已捕获鼠标（如弹出菜单打开时），不处理画布级别的左键事件，
            // 否则点击弹出菜单内的按钮会被误判为"画布空白点击"而清除节点选择。
                // 注意：不能用 WantCaptureMouse（鼠标在画布窗口本身上时也为 true，会破坏框选）。
                // 只在特定 popup 打开时跳过，避免菜单点击误清除选择集。
                boolean anyEditorPopupOpen = ImGui.isPopupOpen("NodeContextMenu") || ImGui.isPopupOpen("Node Search");
                if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left) && !anyEditorPopupOpen) {
                NodeCraft.LOGGER.debug("左键点击检测 - 鼠标位置: ({}, {})", mousePos.x, mousePos.y);

                // 检查点击是否在画布区域内
                if (mousePos.x >= canvasPos.x && mousePos.x <= canvasPos.x + canvasWidth &&
                        mousePos.y >= canvasPos.y && mousePos.y <= canvasPos.y + canvasHeight) {
                    NodeCraft.LOGGER.debug("鼠标点击在画布区域内");

                    // 获取当前鼠标下方的节点信息
                    UUID nodeUnderMouse = this.getNodeIdUnderMouse(mousePos.x, mousePos.y);
                    // 重新检测鼠标是否在端口上，确保准确性
                    boolean isMouseOnPort = interaction.updateHoveredPort(mousePos, portScreenPositions, currentGraph);
                    NodeCraft.LOGGER.debug("节点检测 - nodeUnderMouse: {}, isMouseOnPort: {}", nodeUnderMouse, isMouseOnPort);

                    // 只有当鼠标不在节点也不在端口上时，才启动画布级别的交互 (框选或平移)
                    if (nodeUnderMouse == null && !isMouseOnPort) {
                        NodeCraft.LOGGER.debug("鼠标点击在画布空白区域 (清除选择/框选/平移画布) - 鼠标位置: ({}, {})", mousePos.x, mousePos.y);
                        // 如果鼠标在画布空白区域，并且当前没有按住Ctrl键，清除所有选择
                        if (!ImGui.getIO().getKeyCtrl()) {
                            NodeCraft.LOGGER.debug("清除选择 - 当前选中节点数: {}", selectedNodeIds.size());
                            this.clearSelectedNodes();
                            NodeCraft.LOGGER.debug("选择已清除 - 当前选中节点数: {}", selectedNodeIds.size());
                        }
                        // 启动框选 (使用新的专门的启动方法)
                        NodeCraft.LOGGER.debug("尝试启动框选");
                        interaction.startBoxSelection(mousePos, canvasPos);
                        NodeCraft.LOGGER.debug("框选启动结果 - isBoxSelecting: {}", interaction.isBoxSelecting());

                        // 启动画布平移 (仅当没有启动框选时)
                        if (!interaction.isBoxSelecting()) {
                            NodeCraft.LOGGER.debug("框选未启动，尝试启动画布平移");
                            interaction.tryStartCanvasPanning(mousePos);
                        } else {
                            NodeCraft.LOGGER.debug("框选已启动，跳过画布平移");
                        }
                    } else {
                        if (nodeUnderMouse != null) {
                            NodeCraft.LOGGER.debug("鼠标点击在节点上，不清除选择 - 节点ID: {}", nodeUnderMouse);
                        }
                        if (isMouseOnPort) {
                            NodeCraft.LOGGER.debug("鼠标点击在端口上，不清除选择");
                        }
                    }
                } else {
                    NodeCraft.LOGGER.debug("鼠标点击在画布区域外，不处理 - 鼠标位置: ({}, {}), 画布区域: ({}, {}) - ({}, {})", 
                        mousePos.x, mousePos.y, canvasPos.x, canvasPos.y, canvasPos.x + canvasWidth, canvasPos.y + canvasHeight);
                }
            }

            // 11. 渲染连接预览线 (如果正在创建连接)，类型不匹配时显示红色
            if (interaction.isCreatingConnection()) {
                boolean previewTypeMismatch = computeConnectionPreviewTypeMismatch(currentGraph, interaction);
                renderer.drawConnectionPreview(drawList, interaction.getDragPreviewLineStartPos(), canvasZoom, interaction.isFromOutputPort(), previewTypeMismatch);
                String previewInvalidReason = getConnectionPreviewInvalidReason(currentGraph, interaction);
                if (previewInvalidReason != null) {
                    ImGui.setTooltip(previewInvalidReason);
                }
            }

            // 12. 绘制框选框 (如果正在框选)
            if (interaction.isBoxSelecting()) {
                renderer.drawSelectionBox(drawList, canvasPos, interaction.getBoxSelectStart(), interaction.getBoxSelectEnd());
            }

            // 13. 处理连接线断开 (右键点击连接线)
            // 此方法不依赖 ImGui.isAnyItemActive()，因为它处理的是右键事件，且鼠标已在连接线上。
            interaction.handleConnectionDisconnection(currentGraph, portScreenPositions);

            // 13.5 悬停在类型不匹配的连线上时显示提示
            if (interaction.isHoveringConnection() && currentGraph != null) {
                UUID srcId = interaction.getHoveredConnectionSourceNodeId();
                String srcPortId = interaction.getHoveredConnectionSourcePortId();
                UUID tgtId = interaction.getHoveredConnectionTargetNodeId();
                String tgtPortId = interaction.getHoveredConnectionTargetPortId();
                for (NodeGraph.Connection c : currentGraph.getConnections()) {
                    if (c.sourceNode.getId().equals(srcId) && c.sourcePort.getId().equals(srcPortId)
                            && c.targetNode.getId().equals(tgtId) && c.targetPort.getId().equals(tgtPortId)) {
                        if (!NodeDataType.isConnectableTo(c.sourcePort.getDataType(), c.targetPort.getDataType())) {
                            String msg = String.format("类型不匹配: 输出 %s 无法连接到输入 %s",
                                    c.sourcePort.getDataType().getDisplayName(),
                                    c.targetPort.getDataType().getDisplayName());
                            ImGui.setTooltip(msg);
                        }
                        break;
                    }
                }
            }

            // 14. 处理节点右键菜单 (仅当 ImGui 没有被其他 Item 捕获时)
            if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Right)) {
                // 如果 ImGui 已经捕获了鼠标，则不显示右键菜单（防止与内部控件的右键事件冲突）
                if (!ImGui.getIO().getWantCaptureMouse()) {
                    handleNodeRightClick(mousePos.x, mousePos.y);
                } else {
                    NodeCraft.LOGGER.debug("ImGui已捕获右键，不显示节点右键菜单。");
                }
            }
            menus.renderNodeContextMenu(); // 渲染菜单（如果菜单已打开）

            // 15. 处理节点搜索弹窗
            menus.renderNodeSearchPopup(); // 渲染搜索弹窗（如果弹窗已打开）
            maybeAutoExecutePreviewGraph();

        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染ImGui编辑器时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 在渲染前应用节点拖动位移。
     * 这样可以避免“本帧先绘制后位移”导致的节点背景与自定义UI视觉不同步。
     */
    private void applyNodeDragMovementBeforeRender() {
        if (!interaction.isDraggingNode() || !ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }

        float deltaX = ImGui.getIO().getMouseDelta().x / canvasZoom;
        float deltaY = ImGui.getIO().getMouseDelta().y / canvasZoom;

        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        UUID draggingNodeId = interaction.getDraggingNodeId();
        if (draggingNodeId == null) {
            return;
        }

        // 如果拖动节点属于当前选中集，则整体移动选中集；否则只移动拖动源节点
        java.util.Set<UUID> moveTargets = new java.util.HashSet<>();
        if (!selectedNodeIds.isEmpty() && selectedNodeIds.contains(draggingNodeId)) {
            moveTargets.addAll(selectedNodeIds);
        } else {
            moveTargets.add(draggingNodeId);
        }

        boolean moved = false;
        for (UUID nodeId : moveTargets) {
            NodePosition nodePos = nodePositions.get(nodeId);
            if (nodePos != null) {
                nodePos.x += deltaX;
                nodePos.y += deltaY;
                moved = true;
            }
        }

        if (moved) {
            markGraphStructureDirty();
        }
    }

    private void cleanupDanglingConnections() {
        if (currentGraph == null) {
            return;
        }

        int removedCount = 0;
        for (NodeGraph.Connection connection : currentGraph.getConnections()) {
            INode sourceNode = connection.sourceNode;
            INode targetNode = connection.targetNode;
            if (sourceNode == null || targetNode == null) {
                currentGraph.removeConnection(connection);
                removedCount++;
                continue;
            }

            if (!hasPort(sourceNode.getOutputPorts(), connection.sourcePort.getId())
                    || !hasPort(targetNode.getInputPorts(), connection.targetPort.getId())) {
                currentGraph.removeConnection(connection);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            NodeCraft.LOGGER.info("已清理 {} 条悬挂连线（端口已被动态移除）", removedCount);
        }
    }

    private static boolean hasPort(List<IPort> ports, String portId) {
        if (ports == null || portId == null) {
            return false;
        }
        for (IPort port : ports) {
            if (port != null && portId.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算当前拖拽连接预览线是否应显示为红色。
     */
    private boolean computeConnectionPreviewTypeMismatch(NodeGraph graph, ImGuiNodeInteraction interaction) {
        return getConnectionPreviewInvalidReason(graph, interaction) != null;
    }

    /**
     * 在当前悬停连接线上插入中继节点（utilities.assist.reroute）。
     */
    private void insertRerouteNodeOnHoveredConnection(ImVec2 mousePos, ImVec2 canvasPos) {
        UUID sourceNodeId = interaction.getHoveredConnectionSourceNodeId();
        String sourcePortId = interaction.getHoveredConnectionSourcePortId();
        UUID targetNodeId = interaction.getHoveredConnectionTargetNodeId();
        String targetPortId = interaction.getHoveredConnectionTargetPortId();

        if (sourceNodeId == null || sourcePortId == null || targetNodeId == null || targetPortId == null) {
            return;
        }

        if (currentGraph == null || !currentGraph.isConnected(sourceNodeId, sourcePortId, targetNodeId, targetPortId)) {
            return;
        }

        float worldX = (mousePos.x - canvasPos.x - canvasOffsetX) / canvasZoom;
        float worldY = (mousePos.y - canvasPos.y - canvasOffsetY) / canvasZoom;

        INode rerouteNode = addNode("utilities.assist.reroute", worldX, worldY);
        if (rerouteNode == null) {
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：无法创建中继节点");
            return;
        }

        UUID rerouteNodeId = rerouteNode.getId();
        boolean oldDisconnected = disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        if (!oldDisconnected) {
            currentGraph.removeNode(rerouteNodeId);
            nodePositions.remove(rerouteNodeId);
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：无法断开原连接");
            return;
        }

        boolean firstConnected = connectPorts(sourceNodeId, sourcePortId, rerouteNodeId, "input_signal");
        boolean secondConnected = connectPorts(rerouteNodeId, "output_signal", targetNodeId, targetPortId);

        if (!firstConnected || !secondConnected) {
            // 回滚：尽最大努力恢复原连接
            disconnectPorts(sourceNodeId, sourcePortId, rerouteNodeId, "input_signal");
            disconnectPorts(rerouteNodeId, "output_signal", targetNodeId, targetPortId);
            currentGraph.removeNode(rerouteNodeId);
            nodePositions.remove(rerouteNodeId);
            connectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：连接重建失败，已回滚");
            return;
        }

        clearSelectedNodes();
        setSelectedNodeId(rerouteNodeId);
        markGraphStructureDirty();

        NodeCraft.LOGGER.info("已在连接线上插入中继节点: {}({}) -> {} -> {}({})",
                sourceNodeId, sourcePortId, rerouteNodeId, targetNodeId, targetPortId);
    }

    private void renderHoveredPortTooltip(NodeGraph graph, ImGuiNodeInteraction interaction) {
        if (graph == null || interaction == null || interaction.isCreatingConnection()) {
            return;
        }

        UUID hoveredNodeId = interaction.getHoveredNodeId();
        String hoveredPortId = interaction.getHoveredPortId();
        if (hoveredNodeId == null || hoveredPortId == null) {
            return;
        }

        INode node = graph.getNode(hoveredNodeId);
        if (node == null) {
            return;
        }

        IPort hoveredPort = findPort(node, hoveredPortId, interaction.isHoveredPortOutput());
        if (hoveredPort == null) {
            return;
        }

        StringBuilder tooltip = new StringBuilder();
        tooltip.append(hoveredPort.getDisplayName()).append("\n");
        tooltip.append("类型: ").append(hoveredPort.getDataType().getDisplayName()).append("\n");
        if (hoveredPort.isInput()) {
            tooltip.append("连接规则: ")
                    .append(hoveredPort.allowsMultipleIncomingConnections() ? "允许多个上游输入连接" : "只允许一个上游输入连接")
                    .append("\n");

            UUID connectedNodeId = graph.getConnectedOutputNodeId(node.getId(), hoveredPort.getId());
            if (connectedNodeId != null) {
                tooltip.append("状态: 已连接");
            } else {
                tooltip.append("状态: 未连接");
            }
        } else {
            tooltip.append("连接规则: 允许一对多输出");
        }

        if (hoveredPort.getDescription() != null && !hoveredPort.getDescription().isEmpty()) {
            tooltip.append("\n").append(hoveredPort.getDescription());
        }

        ImGui.setTooltip(tooltip.toString());
    }

    private IPort findPort(INode node, String portId, boolean isOutputPort) {
        List<IPort> ports = isOutputPort ? node.getOutputPorts() : node.getInputPorts();
        for (IPort port : ports) {
            if (port.getId().equals(portId)) {
                return port;
            }
        }
        return null;
    }

    private String getConnectionPreviewInvalidReason(NodeGraph graph, ImGuiNodeInteraction interaction) {
        if (graph == null || !interaction.isCreatingConnection()) return null;
        UUID sourceNodeId = interaction.getSourceNodeId();
        String sourcePortId = interaction.getSourcePortId();
        UUID hoveredNodeId = interaction.getHoveredNodeId();
        String hoveredPortId = interaction.getHoveredPortId();
        boolean isFromOutput = interaction.isFromOutputPort();
        boolean hoveredIsOutput = interaction.isHoveredPortOutput();
        if (sourceNodeId == null || sourcePortId == null || hoveredNodeId == null || hoveredPortId == null) return null;
        INode sourceNode = graph.getNode(sourceNodeId);
        INode hoveredNode = graph.getNode(hoveredNodeId);
        if (sourceNode == null || hoveredNode == null) return null;
        IPort sourcePort = null;
        IPort targetPort = null;
        if (isFromOutput && !hoveredIsOutput) {
            for (IPort p : sourceNode.getOutputPorts()) if (p.getId().equals(sourcePortId)) { sourcePort = p; break; }
            for (IPort p : hoveredNode.getInputPorts()) if (p.getId().equals(hoveredPortId)) { targetPort = p; break; }
        } else if (!isFromOutput && hoveredIsOutput) {
            for (IPort p : sourceNode.getInputPorts()) if (p.getId().equals(sourcePortId)) { targetPort = p; break; }
            for (IPort p : hoveredNode.getOutputPorts()) if (p.getId().equals(hoveredPortId)) { sourcePort = p; break; }
        } else {
            return isFromOutput ? "只能连接到输入端" : "只能连接到输出端";
        }
        if (sourcePort == null || targetPort == null) return "目标端口不可连接";
        if (sourcePort.getNode().getId().equals(targetPort.getNode().getId())) {
            return "不能连接到同一节点";
        }
        if (!NodeDataType.isConnectableTo(sourcePort.getDataType(), targetPort.getDataType())) {
            return String.format("类型不匹配: 输出 %s 无法连接到输入 %s",
                    sourcePort.getDataType().getDisplayName(),
                    targetPort.getDataType().getDisplayName());
        }
        UUID connectedNodeId = graph.getConnectedOutputNodeId(targetPort.getNode().getId(), targetPort.getId());
        String connectedPortId = graph.getConnectedOutputPortId(targetPort.getNode().getId(), targetPort.getId());
        boolean isSameExistingConnection = connectedNodeId != null &&
                connectedNodeId.equals(sourcePort.getNode().getId()) &&
                connectedPortId != null &&
                connectedPortId.equals(sourcePort.getId());
        if (connectedNodeId != null && !isSameExistingConnection && !targetPort.allowsMultipleIncomingConnections()) {
            return "该输入端只允许一个输入连接";
        }
        if (!graph.canConnect(sourcePort.getNode().getId(), sourcePort.getId(), targetPort.getNode().getId(), targetPort.getId())) {
            return "该连接无效";
        }
        return null;
    }

    /**
     * 处理鼠标右键点击节点或画布背景，打开上下文菜单。
     * @param mouseX 鼠标X屏幕坐标。
     * @param mouseY 鼠标Y屏幕坐标。
     */
    public void handleNodeRightClick(float mouseX, float mouseY) {
        // ImGui.isAnyItemActive() 检查已在 renderImGui() 调用此方法之前进行，确保优先级。
        menus.handleNodeRightClick(mouseX, mouseY);
    }

    /**
     * 添加新节点到画布。
     * 此方法现在是统一入口 `addNodeWithState` 的简化包装。
     * @param nodeTypeId 节点的类型ID。
     * @param x 节点的初始世界X坐标。
     * @param y 节点的初始世界Y坐标。
     * @return 新创建的节点实例，如果失败则返回null。
     */
    @Override
    public INode addNode(String nodeTypeId, float x, float y) {
        // 调用统一的添加方法，不指定UUID和状态，由其生成新UUID并记录历史
        return addNodeWithState(nodeTypeId, null, x, y, null);
    }

    /**
     * 根据节点类型ID、指定UUID和初始状态创建并添加一个节点到图中。
     * 此方法主要用于历史记录的撤销/重做功能，以恢复特定ID和状态的节点。
     *
     * @param nodeTypeId 节点的类型ID。
     * @param oldNodeId 节点的旧UUID（仅用于历史记录内部映射，新创建的节点会有新UUID），如果为null，将自动生成新的UUID。
     * @param x 节点的初始世界X坐标。
     * @param y 节点的初始世界Y坐标。
     * @param nodeState 节点的初始状态数据，将通过 setNodeState 应用。如果为null，则使用节点默认状态。
     * @return 新创建或恢复的节点实例，如果失败则返回null。
     */
    @Override
    public INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState) {
        if (currentGraph == null) {
            NodeCraft.LOGGER.error("无法添加节点: 当前没有节点图");
            return null;
        }

        try {
            NodeRegistry registry = NodeRegistry.getInstance();
            INode node = registry.createNodeInstance(nodeTypeId); // 创建节点实例，它会生成自己的 UUID

            if (node == null) {
                NodeCraft.LOGGER.error("无法创建节点: 未找到类型 {}", nodeTypeId);
                return null;
            }
            
            // 应用节点状态
            if (nodeState != null) {
                try {
                    node.setNodeState(nodeState);
                } catch (Exception e) {
                    NodeCraft.LOGGER.warn("添加节点 {} 时恢复状态失败: {}", nodeTypeId, e.getMessage());
                }
            }

            currentGraph.addNode(node); // 将新创建的节点添加到图中
            nodePositions.put(node.getId(), new NodePosition(x, y));

              markGraphStructureDirty();

            // 只有在历史记录启用时才记录历史，避免在撤销/重做操作中重复记录
            if (history != null && history.isRecording()) {
                history.recordAddNode(node, x, y);
            }

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("nodeId", node.getId());
            eventData.put("nodeType", nodeTypeId);
            eventData.put("x", x);
            eventData.put("y", y);
            notifyEditorComponents("node_added", eventData);

            return node;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("添加节点时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 连接两个节点的指定端口。
     * @param sourceNodeId 源节点ID。
     * @param sourcePortId 源端口ID。
     * @param targetNodeId 目标节点ID。
     * @param targetPortId 目标端口ID。
     * @return true 如果连接成功，否则返回false。
     */
    @Override
    public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (currentGraph == null) {
            NodeCraft.LOGGER.error("无法连接端口: 当前没有节点图");
            return false;
        }

        try {
            INode sourceNode = currentGraph.getNode(sourceNodeId);
            INode targetNode = currentGraph.getNode(targetNodeId);

            if (sourceNode == null || targetNode == null) {
                NodeCraft.LOGGER.error("无法连接端口: 未找在节点");
                return false;
            }

            boolean success = currentGraph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);

            if (success) {
                  markGraphStructureDirty();
                // 只有在历史记录启用时才记录历史
                if (history != null && history.isRecording()) {
                    history.recordAddConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                }
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("sourceNodeId", sourceNodeId);
                eventData.put("sourcePortId", sourcePortId);
                eventData.put("targetNodeId", targetNodeId);
                eventData.put("targetPortId", targetPortId);
                notifyEditorComponents("connection_added", eventData);
            }

            return success;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("连接端口时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取鼠标下方的节点ID。
     * 此方法仅用于判断鼠标是否"在"节点上，不包含任何交互优先级逻辑。
     * @param mouseX 鼠标屏幕X坐标。
     * @param mouseY 鼠标屏幕Y坐标。
     * @return 鼠标下方的节点ID，如果没有则返回null。
     */
    @Override
    public UUID getNodeIdUnderMouse(float mouseX, float mouseY) {
        if (currentGraph == null) {
            // NodeCraft.LOGGER.debug("getNodeIdUnderMouse: currentGraph 为空"); // Too verbose
            return null;
        }

        ImVec2 canvasWindowPos = ImGui.getWindowPos(); // Get ImGui window position
        List<INode> nodes = currentGraph.getNodes();

        // Keep hit-testing in the same layering order as rendering:
        // 1) unselected nodes, 2) selected nodes.
        // Then iterate backwards to hit-test the top-most drawn node first.
        List<INode> renderOrder = new java.util.ArrayList<>(nodes.size());
        for (INode node : nodes) {
            if (!selectedNodeIds.contains(node.getId())) {
                renderOrder.add(node);
            }
        }
        for (INode node : nodes) {
            if (selectedNodeIds.contains(node.getId())) {
                renderOrder.add(node);
            }
        }

        // NodeCraft.LOGGER.debug("getNodeIdUnderMouse: Checking mouse ({}, {}) vs canvas ({}, {})", mouseX, mouseY, canvasWindowPos.x, canvasWindowPos.y);

        for (int i = renderOrder.size() - 1; i >= 0; i--) { // Iterate backwards to pick top-most node
            INode node = renderOrder.get(i);
            UUID nodeId = node.getId();
            NodePosition pos = nodePositions.get(nodeId);

            if (pos != null) {
                // Calculate node screen coordinates
                float nodeScreenX = canvasWindowPos.x + pos.x * canvasZoom + canvasOffsetX;
                float nodeScreenY = canvasWindowPos.y + pos.y * canvasZoom + canvasOffsetY;

                // Use NodePosition's stored width/height (unscaled), then apply zoom
                // Fallback to default if not yet calculated (width/height <= 0)
                float nodeWidthScaled = pos.width > 0 ? pos.width * canvasZoom : 150 * canvasZoom;
                float nodeHeightScaled = pos.height > 0 ? pos.height * canvasZoom : 80 * canvasZoom;

                // Check if mouse is within node's bounding box
                if (mouseX >= nodeScreenX && mouseX <= nodeScreenX + nodeWidthScaled &&
                        mouseY >= nodeScreenY && mouseY <= nodeScreenY + nodeHeightScaled) {
                    // NodeCraft.LOGGER.debug("Found node under mouse: {} at ({}, {}) size ({}, {})", nodeId, nodeScreenX, nodeScreenY, nodeWidthScaled, nodeHeightScaled);
                    return nodeId;
                }
            }
        }
        // NodeCraft.LOGGER.debug("No node found under mouse."); // Too verbose
        return null;
    }

    /**
     * 检查鼠标是否悬停在画布的任何节点上。
     * @param mouseX 鼠标X屏幕坐标。
     * @param mouseY 鼠标Y屏幕坐标。
     * @param canvasScreenPos 画布屏幕位置（此参数在此实现中可能冗余）。
     * @return 如果鼠标在任何节点上则返回true。
     */
    public boolean isMouseOverAnyNode(float mouseX, float mouseY, ImVec2 canvasScreenPos) {
        return getNodeIdUnderMouse(mouseX, mouseY) != null;
    }

    /**
     * 在指定的世界坐标位置粘贴节点。
     * @param x 粘贴的世界X坐标。
     * @param y 粘贴的世界Y坐标。
     */
    @Override
    public void pasteNodesAtPosition(float x, float y) {
        NodeCraft.LOGGER.info("尝试在位置 ({}, {}) 粘贴节点", x, y);
        if (clipboard != null) {
            boolean result = clipboard.pasteNodes(x, y);
            if (result) {
                NodeCraft.LOGGER.info("粘贴节点成功");
                markGraphStructureDirty();
            } else {
                NodeCraft.LOGGER.error("粘贴节点失败");
            }
        } else {
            NodeCraft.LOGGER.error("剪贴板组件为空，无法粘贴节点");
        }
    }

    /**
     * 在指定的世界坐标位置请求节点搜索。
     * @param x 搜索弹窗的世界X坐标。
     * @param y 搜索弹窗的世界Y坐标。
     */
    public void requestNodeSearch(float x, float y) {
        menus.requestNodeSearch(x, y);
    }

    /**
     * 获取编辑器的唯一标识符。
     * @return 编辑器的字符串标识符。
     */
    @Override
    public String getIdentifier() {
        return "imgui";
    }

    /**
     * 获取编辑器的优先级（如果存在多个编辑器）。
     * @return 优先级数值。
     */
    @Override
    public int getPriority() {
        return 10; // 较高优先级
    }

    /**
     * 检查当前平台是否支持此编辑器实现。
     * @return 如果支持则返回true。
     */
    @Override
    public boolean isPlatformSupported() {
        return NodeEditorFactory.isImGuiSupported();
    }

    // --- Getter/Setter方法 (实现 ICanvasEditor 接口和内部访问) ---

    @Override
    public NodeGraph getCurrentGraph() {
        return currentGraph;
    }

    public void setCurrentGraph(NodeGraph graph) {
        this.currentGraph = graph;
    }

    @Override
    public Map<UUID, NodePosition> getNodePositions() {
        return nodePositions;
    }

    @Override
    public NodePosition getNodePosition(UUID nodeId) {
        return nodePositions.get(nodeId);
    }

    public void setNodePositions(Map<UUID, NodePosition> positions) {
        this.nodePositions = positions;
    }

    @Override
    public void clearNodePositions() {
        this.nodePositions.clear();
    }

    @Override
    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    @Override
    public void setSelectedNodeId(UUID nodeId) {
        this.selectedNodeId = nodeId;
        if (nodeId != null) {
            this.selectedNodeIds.add(nodeId);
            NodeCraft.LOGGER.debug("设置选中节点ID: {}, 当前选中节点数: {}", nodeId, selectedNodeIds.size());
        } else {
            // 如果传入null，表示清空主选中节点，但不清空selectedNodeIds集合
            // clearSelectedNodes() 方法负责清空整个集合
        }
        notifyEditorComponents("nodeSelected", nodeId); // 通知选择事件
    }

    /**
     * 通知所有编辑器组件发生了事件。
     * @param eventType 事件类型（字符串）。
     * @param eventData 事件相关数据。
     */
    private void notifyEditorComponents(String eventType, Object eventData) {
        NodeCraft.LOGGER.debug("发送编辑器事件: {}, 数据: {}", eventType, eventData);

        try {
            if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen screen) {

                if (screen.getComponentManager() != null) {
                    screen.getComponentManager().broadcastEvent(eventType, eventData);
                    NodeCraft.LOGGER.debug("事件已通过ComponentManager广播: {} {}", eventType, eventData);
                } else {
                    NodeCraft.LOGGER.warn("ComponentManager为空，无法广播事件");
                }
            } else {
                NodeCraft.LOGGER.debug("当前不在NodecraftScreen中，事件未广播");
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("广播事件时出错", e);
        }
    }

    @Override
    public java.util.Set<UUID> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    @Override
    public void clearSelectedNodes() {
        this.selectedNodeIds.clear();
        this.selectedNodeId = null;
        NodeCraft.LOGGER.debug("已清除所有选中节点");
        notifyEditorComponents("nodeSelectionCleared", null); // 通知清除选择事件
    }

    @Override
    public void removeSelectedNode(UUID nodeId) {
        this.selectedNodeIds.remove(nodeId);
        if (nodeId != null && nodeId.equals(selectedNodeId)) {
            selectedNodeId = null;
            // 如果移除了主选节点，尝试将集合中的第一个节点设置为主选
            if (!selectedNodeIds.isEmpty()) {
                setSelectedNodeId(selectedNodeIds.iterator().next());
            } else {
                notifyEditorComponents("nodeSelectionCleared", null);
            }
        }
    }

    @Override
    public void removeNodePosition(UUID nodeId) {
        this.nodePositions.remove(nodeId);
    }

    @Override
    public float getCanvasZoom() {
        return canvasZoom;
    }

    @Override
    public void setCanvasZoom(float zoom) {
        this.canvasZoom = zoom;
    }

    @Override
    public float getCanvasOffsetX() {
        return canvasOffsetX;
    }

    @Override
    public float getCanvasOffsetY() {
        return canvasOffsetY;
    }

    @Override
    public void setCanvasOffset(float x, float y) {
        this.canvasOffsetX = x;
        this.canvasOffsetY = y;
    }

    @Override
    public void setCanvasView(float zoom, float offsetX, float offsetY) {
        this.canvasZoom = zoom;
        this.canvasOffsetX = offsetX;
        this.canvasOffsetY = offsetY;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    @Override
    public ImGuiNodeInteraction getInteraction() {
        return interaction;
    }

    @Override
    public ImGuiNodeIO getNodeIO() {
        return io;
    }

    @Override
    public Map<UUID, Map<String, ImVec2>> getPortScreenPositions() {
        return portScreenPositions;
    }

    @Override
    public ImGuiNodeHistory getHistory() {
        return history;
    }

    @Override
    public ImGuiNodeClipboard getClipboard() {
        return clipboard;
    }

    @Override
    public boolean undo() {
        NodeCraft.LOGGER.info("编辑器撤销操作被调用 - 历史记录状态: {}", history.getHistoryStats());
        boolean result = history.undo();
        NodeCraft.LOGGER.info("编辑器撤销操作完成 - 结果: {}, 新状态: {}", result, history.getHistoryStats());
        return result;
    }

    @Override
    public boolean redo() {
        NodeCraft.LOGGER.info("编辑器重做操作被调用 - 历史记录状态: {}", history.getHistoryStats());
        boolean result = history.redo();
        NodeCraft.LOGGER.info("编辑器重做操作完成 - 结果: {}, 新状态: {}", result, history.getHistoryStats());
        return result;
    }

    @Override
    public boolean copySelectedNodes() {
        return clipboard.copySelectedNodes();
    }

    @Override
    public boolean cutSelectedNodes() {
        boolean result = clipboard.cutSelectedNodes();
        if (result) {
            markGraphStructureDirty();
        }
        return result;
    }

    @Override
    public boolean pasteNodesAt(float x, float y) {
        boolean result = clipboard.pasteNodes(x, y);
        if (result) {
            markGraphStructureDirty();
        }
        return result;
    }

    @Override
    public boolean deleteSelectedNodes() {
        boolean result = clipboard.deleteSelectedNodes();
        if (result) {
            markGraphStructureDirty();
        }
        return result;
    }

    public boolean createSubgraphFromSelection() {
        if (currentGraph == null || selectedNodeIds.isEmpty()) {
            return false;
        }

        java.util.Set<UUID> selection = new java.util.LinkedHashSet<>(selectedNodeIds);
        String subgraphName = currentGraph.getName() != null && !currentGraph.getName().isBlank()
            ? currentGraph.getName() + " Selection"
            : "Extracted Subgraph";

        try {
            SubgraphExtractionService.ExtractionResult extraction = SubgraphExtractionService.extract(currentGraph, selection, subgraphName);
            String embeddedGraphJson = GraphSerializer.toJson(extraction.savedGraph());
            Map<String, Object> subgraphState = buildSubgraphNodeState(extraction, subgraphName, embeddedGraphJson);
            NodePosition wrapperPosition = selectionCenter(selection);

            boolean wasRecording = history != null && history.isRecording();
            if (wasRecording) {
                history.pauseRecording();
            }
            try {
                INode wrapper = addNodeWithState(
                    "utilities.organization.subgraph",
                    null,
                    wrapperPosition.x,
                    wrapperPosition.y,
                    subgraphState
                );
                if (wrapper == null) {
                    return false;
                }

                for (UUID nodeId : new ArrayList<>(selection)) {
                    if (currentGraph.removeNode(nodeId)) {
                        removeNodePosition(nodeId);
                        removeSelectedNode(nodeId);
                    }
                }

                reconnectSubgraphBoundaries(wrapper.getId(), extraction);
                clearSelectedNodes();
                setSelectedNodeId(wrapper.getId());
                markGraphStructureDirty();
                NodeCraft.LOGGER.info(
                    "Created subgraph '{}' from {} nodes. inputs={}, outputs={}",
                    subgraphName,
                    selection.size(),
                    extraction.inputBindings().size(),
                    extraction.outputBindings().size()
                );
                return true;
            } finally {
                if (wasRecording) {
                    history.resumeRecording();
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to create subgraph from selection: {}", e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Object> buildSubgraphNodeState(
        SubgraphExtractionService.ExtractionResult extraction,
        String subgraphName,
        String embeddedGraphJson
    ) {
        List<String> inputKeys = extraction.inputKeys();
        List<String> outputKeys = extraction.outputKeys();
        String primaryInputKey = inputKeys.isEmpty() ? "in" : inputKeys.get(0);
        String primaryOutputKey = outputKeys.isEmpty() ? "out" : outputKeys.get(0);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("subgraphRef", keyToken(subgraphName));
        state.put("inputKey", primaryInputKey);
        state.put("outputKey", primaryOutputKey);
        state.put("strictMode", true);
        state.put("maxCallDepth", 8);
        state.put("additionalInputKeys", joinAdditionalKeys(inputKeys));
        state.put("additionalOutputKeys", joinAdditionalKeys(outputKeys));
        state.put("emitDebugTrace", true);
        state.put("embeddedGraphJson", embeddedGraphJson);
        return state;
    }

    private void reconnectSubgraphBoundaries(UUID wrapperNodeId, SubgraphExtractionService.ExtractionResult extraction) {
        for (SubgraphExtractionService.InputBinding binding : extraction.inputBindings()) {
            currentGraph.connect(
                binding.externalSourceNodeId(),
                binding.externalSourcePortId(),
                wrapperNodeId,
                dynamicInputPortId(binding.inputKey())
            );
        }

        for (SubgraphExtractionService.OutputBinding binding : extraction.outputBindings()) {
            currentGraph.connect(
                wrapperNodeId,
                dynamicOutputPortId(binding.outputKey()),
                binding.externalTargetNodeId(),
                binding.externalTargetPortId()
            );
        }
    }

    private NodePosition selectionCenter(java.util.Set<UUID> selection) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;

        for (UUID nodeId : selection) {
            NodePosition position = nodePositions.get(nodeId);
            if (position == null) {
                continue;
            }
            float width = position.width > 0 ? position.width : 180.0f;
            float height = position.height > 0 ? position.height : 90.0f;
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x + width);
            maxY = Math.max(maxY, position.y + height);
            found = true;
        }

        if (!found) {
            return new NodePosition(0.0f, 0.0f);
        }
        return new NodePosition((minX + maxX) / 2.0f - 100.0f, (minY + maxY) / 2.0f - 45.0f);
    }

    private static String joinAdditionalKeys(List<String> keys) {
        if (keys == null || keys.size() <= 1) {
            return "";
        }
        return String.join(",", keys.subList(1, keys.size()));
    }

    private static String dynamicInputPortId(String key) {
        return "dynamic_input_key_" + keyToken(key);
    }

    private static String dynamicOutputPortId(String key) {
        return "dynamic_output_key_" + keyToken(key);
    }

    private static String keyToken(String key) {
        if (key == null || key.isBlank()) {
            return "empty";
        }
        String normalized = key.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        return normalized.isEmpty() ? "empty" : normalized;
    }

    @Override
    public boolean hasUnsavedChanges() {
        if (io != null && io.isDirty()) {
            return true;
        }
        if (currentGraph == null) {
            return false;
        }
        return !currentGraph.getNodes().isEmpty();
    }

    @Override
    public boolean alignNodes(java.util.Set<UUID> nodeIds, NodeAlignmentAction action) {
        if (nodeIds == null || nodeIds.size() < 2 || action == null) {
            return false;
        }

        List<NodePosition> positions = new java.util.ArrayList<>();
        for (UUID nodeId : nodeIds) {
            NodePosition position = nodePositions.get(nodeId);
            if (position != null) {
                positions.add(position);
            }
        }
        if (positions.size() < 2) {
            return false;
        }

        boolean changed = switch (action) {
            case ALIGN_LEFT -> alignLeft(positions);
            case ALIGN_CENTER -> alignCenter(positions);
            case DISTRIBUTE_HORIZONTAL -> distributeHorizontal(positions);
        };

        if (changed) {
            markGraphStructureDirty();
            NodeCraft.LOGGER.info("Applied node alignment {} to {} nodes", action, positions.size());
        }
        return changed;
    }

    private static boolean alignLeft(List<NodePosition> positions) {
        float left = Float.MAX_VALUE;
        for (NodePosition position : positions) {
            left = Math.min(left, position.x);
        }

        boolean changed = false;
        for (NodePosition position : positions) {
            if (Float.compare(position.x, left) != 0) {
                position.x = left;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean alignCenter(List<NodePosition> positions) {
        float minCenter = Float.MAX_VALUE;
        float maxCenter = -Float.MAX_VALUE;
        for (NodePosition position : positions) {
            float center = position.x + getSafeWidth(position) / 2.0f;
            minCenter = Math.min(minCenter, center);
            maxCenter = Math.max(maxCenter, center);
        }
        float targetCenter = (minCenter + maxCenter) / 2.0f;

        boolean changed = false;
        for (NodePosition position : positions) {
            float nextX = targetCenter - getSafeWidth(position) / 2.0f;
            if (Float.compare(position.x, nextX) != 0) {
                position.x = nextX;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean distributeHorizontal(List<NodePosition> positions) {
        if (positions.size() < 3) {
            return false;
        }
        positions.sort(java.util.Comparator.comparingDouble(position -> position.x + getSafeWidth(position) / 2.0f));

        NodePosition first = positions.get(0);
        NodePosition last = positions.get(positions.size() - 1);
        float firstCenter = first.x + getSafeWidth(first) / 2.0f;
        float lastCenter = last.x + getSafeWidth(last) / 2.0f;
        float step = (lastCenter - firstCenter) / (positions.size() - 1);

        boolean changed = false;
        for (int i = 1; i < positions.size() - 1; i++) {
            NodePosition position = positions.get(i);
            float targetCenter = firstCenter + step * i;
            float nextX = targetCenter - getSafeWidth(position) / 2.0f;
            if (Float.compare(position.x, nextX) != 0) {
                position.x = nextX;
                changed = true;
            }
        }
        return changed;
    }

    private static float getSafeWidth(NodePosition position) {
        return position.width > 0.0f ? position.width : 150.0f;
    }

    private void maybeAutoExecutePreviewGraph() {
        if (currentGraph == null || io == null) {
            return;
        }

        long currentDirtyVersion = io.getDirtyVersion();
        long now = System.currentTimeMillis();
        if (currentDirtyVersion != lastObservedDirtyVersion) {
            lastObservedDirtyVersion = currentDirtyVersion;
            pendingAutoPreviewVersion = currentDirtyVersion;
            lastAutoPreviewDirtyChangeAt = now;
        }

        boolean hasPendingDirtyExecution = pendingAutoPreviewVersion >= 0;
        boolean shouldPollPreview = now - lastAutoPreviewExecutionAt >= AUTO_PREVIEW_POLL_INTERVAL_MS;
        if (!hasPendingDirtyExecution && !shouldPollPreview) {
            return;
        }

        if (autoPreviewExecutor != null && autoPreviewExecutor.isExecuting()) {
            return;
        }

        if (hasPendingDirtyExecution && now - lastAutoPreviewDirtyChangeAt < AUTO_PREVIEW_DEBOUNCE_MS) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        World world = client.world;
        ServerPlayerEntity serverPlayer = null;
        IntegratedServer integratedServer = client.getServer();
        if (integratedServer != null && client.player != null) {
            serverPlayer = integratedServer.getPlayerManager().getPlayer(client.player.getUuid());
            if (serverPlayer != null) {
                world = integratedServer.getOverworld();
            }
        }

        final long executingVersion = hasPendingDirtyExecution ? pendingAutoPreviewVersion : currentDirtyVersion;
        final String triggerReason = hasPendingDirtyExecution ? "dirty" : "poll";
        pendingAutoPreviewVersion = -1L;
        lastAutoPreviewExecutionAt = now;

        java.util.Set<UUID> executionScope = hasPendingDirtyExecution && !invalidatedNodeIds.isEmpty()
                ? new HashSet<>(invalidatedNodeIds)
                : null;
        autoPreviewExecutor = new NodeExecutor(currentGraph, new ExecutionContext(world, serverPlayer), executionScope);
        NodeCraft.LOGGER.debug(
                "自动执行预览图: reason={}, dirtyVersion={}, nodes={}, mode={}, scopeSize={}",
                triggerReason,
                executingVersion,
                currentGraph.getNodes().size(),
                executionScope == null ? "full" : "partial",
                executionScope == null ? 0 : executionScope.size()
        );
        autoPreviewExecutor.executeAsync().thenAccept(result -> {
            if (result) {
                NodeCraft.LOGGER.debug("自动执行预览图完成: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            } else {
                NodeCraft.LOGGER.debug("自动执行预览图失败: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            }
            autoPreviewExecutor = null;
        }).exceptionally(throwable -> {
            NodeCraft.LOGGER.error("自动执行预览图异常: reason={}, dirtyVersion={}", triggerReason, executingVersion, throwable);
            autoPreviewExecutor = null;
            return null;
        });
    }

    @Override
    public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (currentGraph == null) return false;
        try {
            INode sourceNode = currentGraph.getNode(sourceNodeId);
            INode targetNode = currentGraph.getNode(targetNodeId);
            if (sourceNode == null || targetNode == null) {
                NodeCraft.LOGGER.warn("断开连接失败：未找到源节点或目标节点");
                return false;
            }
            if (currentGraph.isConnected(sourceNodeId, sourcePortId, targetNodeId, targetPortId)) {
                // 只有在历史记录启用时才记录历史
                if (history != null && history.isRecording()) {
                    history.recordRemoveConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                }
                currentGraph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                markGraphStructureDirty();
                NodeCraft.LOGGER.info("成功断开连接: {}({}) -> {}({})",
                        sourceNode.getDisplayName(), sourcePortId,
                        targetNode.getDisplayName(), targetPortId);
                return true;
            } else {
                NodeCraft.LOGGER.warn("断开连接失败：端口未连接");
                return false;
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("断开端口连接时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean duplicateSelectedNode() {
        if (currentGraph == null) {
            NodeCraft.LOGGER.warn("无法复制节点：当前没有节点图");
            return false;
        }
        if (selectedNodeIds.isEmpty()) {
            NodeCraft.LOGGER.warn("没有选中的节点可复制");
            return false;
        }
        UUID nodeId = selectedNodeIds.iterator().next();
        INode sourceNode = currentGraph.getNode(nodeId);
        if (sourceNode == null) {
            NodeCraft.LOGGER.error("复制失败：找不到选中的节点 {}", nodeId);
            return false;
        }
        NodeCraft.LOGGER.info("开始复制节点: {} (ID: {}, 类型: {})",
                sourceNode.getDisplayName(), nodeId, sourceNode.getTypeId());
        NodePosition sourcePos = getNodePosition(nodeId);
        if (sourcePos == null) {
            NodeCraft.LOGGER.error("复制失败：节点 {} 没有位置信息", nodeId);
            return false;
        }
        NodeCraft.LOGGER.info("源节点位置: ({}, {})", sourcePos.x, sourcePos.y);
        float offsetX = 30;
        float offsetY = 0;
        INode newNode = null;
        try {
            // 使用新的 addNodeWithState 方法来创建副本，不指定 UUID，让它生成新的
            // 复制节点时，将源节点的状态传递给新节点
            newNode = addNodeWithState(sourceNode.getTypeId(), null, sourcePos.x + offsetX, sourcePos.y + offsetY, sourceNode.getNodeState());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("复制节点失败: {}", e.getMessage());
        }

        if (newNode != null) {
            clearSelectedNodes();
            setSelectedNodeId(newNode.getId());
            NodeCraft.LOGGER.info("节点复制成功: {} -> {} (新ID: {})",
                    sourceNode.getDisplayName(), newNode.getDisplayName(), newNode.getId());
            markGraphStructureDirty();
            // 历史记录会在 addNodeWithState 内部统一记录，这里不再手动记录
            return true;
        } else {
            NodeCraft.LOGGER.error("复制节点失败: 无法创建新节点");
            return false;
        }
    }

    public void setNodeDisplayMode(int mode) {
        this.nodeDisplayMode = mode;
        NodeCraft.LOGGER.debug("设置节点显示模式: {}", mode);
    }


    public void setShowNodePreviews(boolean show) {
        this.showNodePreviews = show;
        NodeCraft.LOGGER.debug("设置节点预览显示: {}", show);
    }

    // === 节点颜色管理方法 ===

    /**
     * 设置节点的自定义颜色
     * @param nodeId 节点ID
     * @param color 颜色值（ImGui格式的整数颜色）
     */
    public void setNodeCustomColor(UUID nodeId, int color) {
        if (nodeId != null) {
            nodeCustomColors.put(nodeId, color);
            NodeCraft.LOGGER.debug("设置节点 {} 的自定义颜色: {}", nodeId, String.format("0x%08X", color));
        }
    }

    /**
     * 获取节点的自定义颜色
     * @param nodeId 节点ID
     * @return 自定义颜色，如果没有设置则返回null
     */
    public Integer getNodeCustomColor(UUID nodeId) {
        return nodeCustomColors.get(nodeId);
    }

    /**
     * 移除节点的自定义颜色
     * @param nodeId 节点ID
     */
    public void removeNodeCustomColor(UUID nodeId) {
        if (nodeId != null) {
            nodeCustomColors.remove(nodeId);
            NodeCraft.LOGGER.debug("移除节点 {} 的自定义颜色", nodeId);
        }
    }

    /**
     * 检查节点是否有自定义颜色
     * @param nodeId 节点ID
     * @return 是否有自定义颜色
     */
    public boolean hasNodeCustomColor(UUID nodeId) {
        return nodeCustomColors.containsKey(nodeId);
    }

    // === 节点状态管理方法实现 ===

    @Override
    public boolean toggleNodeDisabled(UUID nodeId) {
        if (nodeId == null) return false;
        
        boolean wasDisabled = disabledNodes.contains(nodeId);
        if (wasDisabled) {
            disabledNodes.remove(nodeId);
            NodeCraft.LOGGER.info("启用节点: {}", nodeId);
        } else {
            disabledNodes.add(nodeId);
            clearNodePreviewArtifacts(nodeId);
            NodeCraft.LOGGER.info("禁用节点: {}", nodeId);
        }
        
        // 标记编辑器为脏状态
        markGraphStructureDirty();
        
        return !wasDisabled; // 返回新状态
    }

    @Override
    public void setNodeDisabled(UUID nodeId, boolean disabled) {
        if (nodeId == null) return;
        
        if (disabled) {
            disabledNodes.add(nodeId);
            clearNodePreviewArtifacts(nodeId);
        } else {
            disabledNodes.remove(nodeId);
        }
        
        NodeCraft.LOGGER.debug("设置节点 {} 禁用状态: {}", nodeId, disabled);
        
        markGraphStructureDirty();
    }

    @Override
    public boolean isNodeDisabled(UUID nodeId) {
        return nodeId != null && disabledNodes.contains(nodeId);
    }

    @Override
    public boolean toggleNodeVisible(UUID nodeId) {
        if (nodeId == null) return true; // 默认可见
        
        boolean wasHidden = hiddenNodes.contains(nodeId);
        if (wasHidden) {
            hiddenNodes.remove(nodeId);
            NodeCraft.LOGGER.info("显示节点: {}", nodeId);
        } else {
            hiddenNodes.add(nodeId);
            NodeCraft.LOGGER.info("隐藏节点: {}", nodeId);
        }
        
        // 标记编辑器为脏状态
        markGraphStructureDirty();
        
        return !wasHidden; // 返回新状态（true=可见）
    }

    @Override
    public void setNodeVisible(UUID nodeId, boolean visible) {
        if (nodeId == null) return;
        
        if (visible) {
            hiddenNodes.remove(nodeId);
        } else {
            hiddenNodes.add(nodeId);
        }
        
        NodeCraft.LOGGER.debug("设置节点 {} 可见性: {}", nodeId, visible);
        
        markGraphStructureDirty();
    }

    @Override
    public boolean isNodeVisible(UUID nodeId) {
        return nodeId == null || !hiddenNodes.contains(nodeId); // 默认可见
    }


    private void clearNodePreviewArtifacts(UUID nodeId) {
        if (nodeId == null) {
            return;
        }
        String ownerNodeId = nodeId.toString();
        com.nodecraft.nodesystem.preview.PreviewManager.hideNodePreviews(ownerNodeId);
        com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService.getInstance()
            .clearTrackedPreviewAcrossWorlds(ownerNodeId);
    }

}
