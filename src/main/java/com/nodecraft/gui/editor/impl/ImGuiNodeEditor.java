package com.nodecraft.gui.editor.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.dialogs.MessageDialog;
import com.nodecraft.gui.editor.NodeEditorFactory;
import com.nodecraft.gui.editor.base.GraphApplyHistoryView;
import com.nodecraft.gui.editor.base.GraphApplyTarget;
import com.nodecraft.gui.editor.base.GraphNodeAnchor;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.editor.document.EditorDocumentFactory;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;
import com.nodecraft.gui.editor.preview.AutoPreviewController;
import com.nodecraft.gui.editor.command.NodeCommandService;
import com.nodecraft.gui.editor.connection.ConnectionEditService;
import com.nodecraft.gui.editor.session.EditorSession;
import com.nodecraft.gui.editor.subgraph.SubgraphEditService;
import com.nodecraft.gui.editor.viewport.EditorViewportState;
import com.nodecraft.gui.recommendation.NodeRecommendationApplyResult;
import com.nodecraft.gui.recommendation.NodeRecommendationContext;
import com.nodecraft.gui.recommendation.NodeRecommendationPopupRenderer;
import com.nodecraft.gui.recommendation.NodeRecommendations;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphCallStackBridge;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.nodes.variable.VariableScopeBridge;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * ImGui节点编辑器实现
 */
public class ImGuiNodeEditor implements INodeEditor, ICanvasEditor, GraphApplyTarget {

    private static ImGuiNodeEditor instance;

    // 子组件
    private final ImGuiNodeRenderer renderer;
    private final ImGuiNodeInteraction interaction;
    private final ImGuiNodeIO io;
    private final ImGuiNodeMenus menus;
    private final ImGuiNodeHistory history;
    private final ImGuiNodeClipboard clipboard;
    private final NodeRecommendationPopupRenderer recommendationPopup;

    // 编辑器状态
    private final EditorSession session;
    private final EditorDocumentState document = new EditorDocumentState();
    private final EditorViewportState viewport = new EditorViewportState();
    private final SubgraphEditService subgraphEdits;
    private final ConnectionEditService connectionEdits;
    private final NodeCommandService nodeCommands;
    private UUID subgraphRenameNodeId;
    private final ImString subgraphRenameBuffer = new ImString(128);
    private boolean openSubgraphRenamePopup;
    // portScreenPositions 存储的是端口的屏幕坐标 (已缩放)，每帧更新
    private Map<UUID, Map<String, ImVec2>> portScreenPositions = new HashMap<>();

    private final EditorInteractionState interactionState = new EditorInteractionState();

    private final AutoPreviewController autoPreviewController;

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
        this.interaction = new ImGuiNodeInteraction(this, interactionState);
        this.menus = new ImGuiNodeMenus(this, this.io);
        this.history = new ImGuiNodeHistory(this);
        this.clipboard = new ImGuiNodeClipboard(this);
        this.subgraphEdits = new SubgraphEditService(new SubgraphHost());
        this.connectionEdits = new ConnectionEditService(new ConnectionHost());
        this.nodeCommands = new NodeCommandService(new NodeCommandHost());
        this.session = new EditorSession(new SessionHost());
        this.recommendationPopup = new NodeRecommendationPopupRenderer(this, NodeRecommendations.get());
        this.autoPreviewController = new AutoPreviewController(
                document::getGraph,
                document,
                this::createAutoPreviewExecutionContext
        );
        BaseNode.addDirtyListener(this::handleNodeDirty);
    }

    private final class SessionHost implements EditorSession.Host {
        @Override
        public void notifyStructureDirty() {
            markGraphStructureDirty();
        }

        @Override
        public void clearNodePreviewArtifacts(UUID nodeId) {
            if (nodeId == null) {
                return;
            }
            com.nodecraft.nodesystem.preview.PreviewManager.hideNodePreviews(nodeId.toString());
        }
    }

    private final class SubgraphHost implements SubgraphEditService.Host {
        @Override public EditorDocumentState document() { return document; }
        @Override public EditorInteractionState interaction() { return interactionState; }
        @Override public ImGuiNodeHistory history() { return history; }
        @Override
        public INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState) {
            return ImGuiNodeEditor.this.addNodeWithState(nodeTypeId, oldNodeId, x, y, nodeState);
        }
        @Override public void removeNodePosition(UUID nodeId) { ImGuiNodeEditor.this.removeNodePosition(nodeId); }
        @Override public void removeSelectedNode(UUID nodeId) { ImGuiNodeEditor.this.removeSelectedNode(nodeId); }
        @Override public void clearSelectedNodes() { ImGuiNodeEditor.this.clearSelectedNodes(); }
        @Override public void setSelectedNodeId(UUID nodeId) { ImGuiNodeEditor.this.setSelectedNodeId(nodeId); }
        @Override public void notifyStructureDirty() { markGraphStructureDirty(); }
        @Override public void showSubgraphLoadNotice(String message) { new MessageDialog("打开子图", message).show(); }
    }

    private final class ConnectionHost implements ConnectionEditService.Host {
        @Override public EditorDocumentState document() { return document; }
        @Override public ImGuiNodeHistory history() { return history; }
        @Override public void notifyStructureDirty() { markGraphStructureDirty(); }
        @Override public void notifyConnectionAdded(Map<String, Object> eventData) { notifyEditorComponents("connection_added", eventData); }
        @Override public INode addNode(String nodeTypeId, float x, float y) { return ImGuiNodeEditor.this.addNode(nodeTypeId, x, y); }
        @Override public void removeNodePosition(UUID nodeId) { ImGuiNodeEditor.this.removeNodePosition(nodeId); }
        @Override public void clearSelectedNodes() { ImGuiNodeEditor.this.clearSelectedNodes(); }
        @Override public void setSelectedNodeId(UUID nodeId) { ImGuiNodeEditor.this.setSelectedNodeId(nodeId); }
    }

    private final class NodeCommandHost implements NodeCommandService.Host {
        @Override public EditorDocumentState document() { return document; }
        @Override public EditorInteractionState interaction() { return interactionState; }
        @Override public ImGuiNodeHistory history() { return history; }
        @Override public void notifyStructureDirty() { markGraphStructureDirty(); }
        @Override public void notifyNodeAdded(Map<String, Object> eventData) { notifyEditorComponents("node_added", eventData); }
        @Override public void clearSelectedNodes() { ImGuiNodeEditor.this.clearSelectedNodes(); }
        @Override public void setSelectedNodeId(UUID nodeId) { ImGuiNodeEditor.this.setSelectedNodeId(nodeId); }
        @Override public void removeNodePosition(UUID nodeId) { ImGuiNodeEditor.this.removeNodePosition(nodeId); }
        @Override public void removeSelectedNode(UUID nodeId) { ImGuiNodeEditor.this.removeSelectedNode(nodeId); }
    }

    private void handleNodeDirty(BaseNode node, long dirtyVersion) {
        autoPreviewController.notifyNodeDirty(node, dirtyVersion);
    }

    private void markGraphStructureDirty() {
        autoPreviewController.notifyStructureDirty();
    }

    public void notifyGraphStructureChanged() {
        markGraphStructureDirty();
    }

    @Nullable
    private ExecutionContext createAutoPreviewExecutionContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return null;
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
        return new ExecutionContext(world, serverPlayer);
    }

    /**
     * Initializes the editor with a blank document when no graph is loaded yet.
     */
    @Override
    public void init() {
        if (document.getGraph() == null) {
            document.resetForNewGraph(EditorDocumentFactory.createEmpty());
        }
    }

    /**
     * 打开编辑器界面。
     */
    @Override
    public void open() {
        NodeCraft.LOGGER.info("ImGuiNodeEditor打开");
        session.setOpen(true);
    }

    /**
     * 关闭编辑器界面。
     */
    @Override
    public void close() {
        NodeCraft.LOGGER.info("ImGuiNodeEditor关闭");
        session.setOpen(false);
    }

    /**
     * 检查编辑器是否处于打开状态。
     * @return true 如果编辑器已打开，否则返回false。
     */
    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public EditorSession getEditorSession() {
        return session;
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
        if (!session.isOpen()) {
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
            boolean mouseOverCanvas = isMouseOverCanvas(mousePos, canvasPos, canvasWidth, canvasHeight);
            if (handleSubgraphNavigationClick(canvasPos, mousePos)) {
                return;
            }

            // 2.0 在绘制前先处理节点拖动位移，确保本帧节点背景与自定义UI同步移动
            applyNodeDragMovementBeforeRender();

            // 2.05 清理动态端口变化后产生的悬挂连线（端口已不存在）
            connectionEdits.cleanupDangling();

            // 2. 先计算所有节点的尺寸和端口位置
            // 这一步会更新 NodePosition 中的 width/height 字段，并填充 portScreenPositions
            renderer.calculatePortPositions(canvasPos, document.getGraph(), document.getNodePositions(), portScreenPositions);

            boolean subgraphDoubleClickConsumed = false;
            if (mouseOverCanvas
                    && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)
                    && !interaction.isCreatingConnection()
                    && !interaction.isBoxSelecting()
                    && document.getGraph() != null) {
                UUID doubleClickedNodeId = getNodeIdUnderMouse(mousePos.x, mousePos.y);
                INode doubleClickedNode = doubleClickedNodeId != null
                    ? document.getGraph().getNode(doubleClickedNodeId)
                    : null;
                if (doubleClickedNode instanceof SubgraphNode) {
                    subgraphDoubleClickConsumed = true;
                    if (isMouseOverNodeHeader(doubleClickedNodeId, mousePos, canvasPos)) {
                        requestSubgraphRename(doubleClickedNodeId);
                        ImGui.getIO().setWantCaptureMouse(true);
                    } else if (subgraphEdits.openNode(doubleClickedNodeId)) {
                        ImGui.getIO().setWantCaptureMouse(true);
                        return;
                    }
                }
            }

            // 3. 渲染背景连接线（未选中节点之间的连接）
            renderer.renderConnectionsDirect(drawList, document.getGraph(), portScreenPositions, interactionState.getSelectedNodeIds());

            // 4. 渲染节点（包含节点主体、标题和自定义UI）。
            // 节点渲染会设置 ImGui.invisibleButton，并更新 ImGui.isItemActive() 状态。
            // 在渲染前，在主窗口上下文里预先计算本帧点击目标节点（坐标在此处是正确的）。
            if (mouseOverCanvas
                    && ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)
                    && !subgraphDoubleClickConsumed) {
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
            renderer.renderNodesDirect(drawList, canvasPos, document.getGraph(), document.getNodePositions(), portScreenPositions, interactionState.getSelectedNodeIds());

            // 5. 渲染前景连接线（与选中节点相关的连接，显示在节点上方）
            renderer.renderForegroundConnections(drawList, document.getGraph(), portScreenPositions, interactionState.getSelectedNodeIds());

            // 6. 更新端口和连接的悬停状态
            // 这两个方法会更新 interaction.hoveredNodeId, hoveredPortId, isHoveredPortOutput, isHoveringConnection 等
            interaction.updateHoveredPort(mousePos, portScreenPositions, document.getGraph());
            interaction.updateHoveredConnection(mousePos, portScreenPositions, document.getGraph());
            renderHoveredPortTooltip(document.getGraph(), interaction);

            // 6.5 双击连接线自动插入中继节点（Reroute）
            if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)
                    && interaction.isHoveringConnection()
                    && !interaction.isCreatingConnection()
                    && !interaction.isDraggingNode()
                    && !interaction.isBoxSelecting()
                    && document.getGraph() != null) {
                // 捕获鼠标，避免被画布层当作空白处双击处理
                ImGui.getIO().setWantCaptureMouse(true);
                connectionEdits.insertReroute(
                        viewport.screenToWorldX(mousePos.x, canvasPos.x),
                        viewport.screenToWorldY(mousePos.y, canvasPos.y),
                        interaction.getHoveredConnectionSourceNodeId(),
                        interaction.getHoveredConnectionSourcePortId(),
                        interaction.getHoveredConnectionTargetNodeId(),
                        interaction.getHoveredConnectionTargetPortId()
                );
            }

            // 7. 处理进行中的连接创建（绘制预览线，鼠标释放时完成连接）
            // 此方法内部会检查 interaction.isCreatingConnection()
            interaction.handleActiveConnectionCreation(document.getGraph(), portScreenPositions);

            // 8. 处理进行中的框选的更新和完成
            // 此方法内部会检查 interaction.isBoxSelecting()，并在鼠标释放时处理框选结果
            interaction.handleBoxSelection(mousePos, canvasPos, document.getNodePositions(), document.getGraph());

            // 9. 处理画布平移（如果正在平移）
            interaction.handleCanvasPanning(canvasPos);

            // 10. 处理鼠标左键点击：节点选择、选择清除、框选启动、画布平移
            // 注意：当 ImGui 已捕获鼠标（如弹出菜单打开时），不处理画布级别的左键事件，
            // 否则点击弹出菜单内的按钮会被误判为"画布空白点击"而清除节点选择。
                // 注意：不能用 WantCaptureMouse（鼠标在画布窗口本身上时也为 true，会破坏框选）。
                // 只在特定 popup 打开时跳过，避免菜单点击误清除选择集。
                boolean anyEditorPopupOpen = ImGui.isPopupOpen("NodeContextMenu") || ImGui.isPopupOpen("Node Search");
                if (mouseOverCanvas
                        && ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)
                        && !anyEditorPopupOpen) {
                NodeCraft.LOGGER.debug("左键点击检测 - 鼠标位置: ({}, {})", mousePos.x, mousePos.y);

                // 检查点击是否在画布区域内
                if (mousePos.x >= canvasPos.x && mousePos.x <= canvasPos.x + canvasWidth &&
                        mousePos.y >= canvasPos.y && mousePos.y <= canvasPos.y + canvasHeight) {
                    NodeCraft.LOGGER.debug("鼠标点击在画布区域内");

                    // 获取当前鼠标下方的节点信息
                    UUID nodeUnderMouse = this.getNodeIdUnderMouse(mousePos.x, mousePos.y);
                    // 重新检测鼠标是否在端口上，确保准确性
                    boolean isMouseOnPort = interaction.updateHoveredPort(mousePos, portScreenPositions, document.getGraph());
                    NodeCraft.LOGGER.debug("节点检测 - nodeUnderMouse: {}, isMouseOnPort: {}", nodeUnderMouse, isMouseOnPort);

                    // 只有当鼠标不在节点也不在端口上时，才启动画布级别的交互 (框选或平移)
                    if (nodeUnderMouse == null && !isMouseOnPort) {
                        NodeCraft.LOGGER.debug("鼠标点击在画布空白区域 (清除选择/框选/平移画布) - 鼠标位置: ({}, {})", mousePos.x, mousePos.y);
                        // 如果鼠标在画布空白区域，并且当前没有按住Ctrl键，清除所有选择
                        if (!ImGui.getIO().getKeyCtrl()) {
                            NodeCraft.LOGGER.debug("清除选择 - 当前选中节点数: {}", interactionState.getSelectedNodeIds().size());
                            this.clearSelectedNodes();
                            NodeCraft.LOGGER.debug("选择已清除 - 当前选中节点数: {}", interactionState.getSelectedNodeIds().size());
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
                    } else if (nodeUnderMouse != null && !isMouseOnPort) {
                        // 单击选中放在与框选相同的检测路径上（getNodeIdUnderMouse），
                        // 不依赖节点渲染中的 isWindowHovered / invisibleButton 激活状态。
                        interaction.handleClickOnNodeBody(nodeUnderMouse, ImGui.getIO().getKeyCtrl());
                        if (!renderer.isCustomUIWidgetBlockingNodeDrag(nodeUnderMouse)) {
                            interaction.tryStartNodeDraggingFromNodeBody(nodeUnderMouse);
                        }
                        ImGui.getIO().setWantCaptureMouse(true);
                        NodeCraft.LOGGER.debug("鼠标点击选中节点: {}", nodeUnderMouse);
                    } else {
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
                ConnectionEditService.DragPreview dragPreview = toConnectionDragPreview(interaction);
                boolean previewTypeMismatch = connectionEdits.isPreviewTypeMismatch(document.getGraph(), dragPreview);
                renderer.drawConnectionPreview(drawList, interaction.getDragPreviewLineStartPos(), viewport.getZoom(), interaction.isFromOutputPort(), previewTypeMismatch);
                String previewInvalidReason = connectionEdits.previewInvalidReason(document.getGraph(), dragPreview);
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
            interaction.handleConnectionDisconnection(document.getGraph(), portScreenPositions);

            // 13.5 悬停在类型不匹配的连线上时显示提示
            if (interaction.isHoveringConnection() && document.getGraph() != null) {
                UUID srcId = interaction.getHoveredConnectionSourceNodeId();
                String srcPortId = interaction.getHoveredConnectionSourcePortId();
                UUID tgtId = interaction.getHoveredConnectionTargetNodeId();
                String tgtPortId = interaction.getHoveredConnectionTargetPortId();
                for (NodeGraph.Connection c : document.getGraph().getConnections()) {
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
            menus.renderSavePresetDialog();
            recommendationPopup.render();
            autoPreviewController.tick();
            renderSubgraphRenamePopup();
            renderSubgraphNavigationOverlay(canvasPos);

        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染ImGui编辑器时出错: {}", e.getMessage(), e);
        }
    }

    /** Keeps nested-graph navigation above canvas content without creating a separate top-level window. */
    private void renderSubgraphNavigationOverlay(ImVec2 canvasPos) {
        if (!subgraphEdits.isEditing() || canvasPos == null) {
            return;
        }

        int depth = subgraphEdits.editDepth();
        String graphName = shortSubgraphName(document.getGraph() != null ? document.getGraph().getName() : null);
        String contextLabel = "SUBGRAPH " + depth + "  /  " + graphName;
        float buttonX = canvasPos.x + 12.0f;
        float buttonY = canvasPos.y + 12.0f;
        float buttonWidth = 116.0f;
        float buttonHeight = 30.0f;
        ImVec2 mousePos = ImGui.getIO().getMousePos();
        boolean hovered = isInside(mousePos, buttonX, buttonY, buttonWidth, buttonHeight);

        ImDrawList overlay = ImGui.getWindowDrawList();
        int buttonColor = ImGui.colorConvertFloat4ToU32(
            hovered ? 0.28f : 0.20f,
            hovered ? 0.50f : 0.38f,
            hovered ? 0.66f : 0.52f,
            1.0f
        );
        int borderColor = ImGui.colorConvertFloat4ToU32(0.42f, 0.78f, 0.98f, 1.0f);
        int textColor = ImGui.colorConvertFloat4ToU32(0.95f, 0.97f, 1.0f, 1.0f);
        overlay.addRectFilled(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor, 4.0f);
        overlay.addRect(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, borderColor, 4.0f);
        String buttonText = "< Parent";
        float buttonTextX = buttonX + (buttonWidth - ImGui.calcTextSize(buttonText).x) * 0.5f;
        float buttonTextY = buttonY + (buttonHeight - ImGui.getTextLineHeight()) * 0.5f;
        overlay.addText(buttonTextX, buttonTextY, textColor, buttonText);
        overlay.addText(buttonX + buttonWidth + 10.0f, buttonTextY, borderColor, contextLabel);
    }

    private boolean handleSubgraphNavigationClick(ImVec2 canvasPos, ImVec2 mousePos) {
        if (!subgraphEdits.isEditing() || canvasPos == null || mousePos == null) {
            return false;
        }
        float buttonX = canvasPos.x + 12.0f;
        float buttonY = canvasPos.y + 12.0f;
        if (!isInside(mousePos, buttonX, buttonY, 116.0f, 30.0f)) {
            return false;
        }
        ImGui.getIO().setWantCaptureMouse(true);
        if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)) {
            closeCurrentSubgraph();
            return true;
        }
        return false;
    }

    private static boolean isInside(ImVec2 point, float x, float y, float width, float height) {
        return point != null
            && point.x >= x && point.x <= x + width
            && point.y >= y && point.y <= y + height;
    }

    private void requestSubgraphRename(UUID nodeId) {
        INode node = document.getGraph() != null ? document.getGraph().getNode(nodeId) : null;
        if (!(node instanceof SubgraphNode)) {
            return;
        }
        subgraphRenameNodeId = nodeId;
        subgraphRenameBuffer.set(node.getDisplayName());
        openSubgraphRenamePopup = true;
    }

    private void renderSubgraphRenamePopup() {
        if (openSubgraphRenamePopup) {
            ImGui.openPopup("Rename Subgraph");
            openSubgraphRenamePopup = false;
        }
        if (!ImGui.beginPopupModal("Rename Subgraph", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.text("Name");
        ImGui.setNextItemWidth(280.0f);
        boolean submit = ImGui.inputText(
            "##subgraph_name",
            subgraphRenameBuffer,
            ImGuiInputTextFlags.EnterReturnsTrue
        );
        ImGui.spacing();
        submit |= ImGui.button("Rename", 120.0f, 0.0f);
        ImGui.sameLine();
        boolean cancel = ImGui.button("Cancel", 120.0f, 0.0f);
        ImGui.spacing();
        if (submit && subgraphEdits.rename(subgraphRenameNodeId, subgraphRenameBuffer.get())) {
            subgraphRenameNodeId = null;
            ImGui.closeCurrentPopup();
        } else if (cancel) {
            subgraphRenameNodeId = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private static boolean isMouseOverCanvas(ImVec2 mousePos, ImVec2 canvasPos, float canvasWidth, float canvasHeight) {
        // 只用几何范围判断，不依赖 isWindowHovered。
        // 节点/自定义 UI 提交大量 Item 后，HoveredWindow 判定会抖动，导致单击选中被误判为未悬停画布。
        if (mousePos == null || canvasPos == null) {
            return false;
        }
        return mousePos.x >= canvasPos.x && mousePos.x <= canvasPos.x + canvasWidth
                && mousePos.y >= canvasPos.y && mousePos.y <= canvasPos.y + canvasHeight;
    }

    private boolean isMouseOverNodeHeader(UUID nodeId, ImVec2 mousePos, ImVec2 canvasPos) {
        NodePosition position = document.getNodePositions().get(nodeId);
        if (position == null || mousePos == null || canvasPos == null) {
            return false;
        }
        float nodeX = canvasPos.x + position.x * viewport.getZoom() + viewport.getOffsetX();
        float nodeY = canvasPos.y + position.y * viewport.getZoom() + viewport.getOffsetY();
        float nodeWidth = (position.width > 0.0f ? position.width : 150.0f) * viewport.getZoom();
        float headerHeight = (ImGui.getTextLineHeight() + 2.0f * NodeRenderConstants.NODE_VERTICAL_PADDING) * viewport.getZoom();
        return mousePos.x >= nodeX && mousePos.x <= nodeX + nodeWidth
            && mousePos.y >= nodeY && mousePos.y <= nodeY + headerHeight;
    }

    private static String shortSubgraphName(String name) {
        String resolved = name == null || name.isBlank() ? "Embedded Graph" : name.trim();
        return resolved.length() <= 42 ? resolved : resolved.substring(0, 39) + "...";
    }

    /**
     * Applies node drag movement before rendering so the node body and custom UI move in the same frame.
     */

    private void applyNodeDragMovementBeforeRender() {
        if (!interaction.isDraggingNode() || !ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }

        float deltaX = ImGui.getIO().getMouseDelta().x / viewport.getZoom();
        float deltaY = ImGui.getIO().getMouseDelta().y / viewport.getZoom();

        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        UUID draggingNodeId = interaction.getDraggingNodeId();
        if (draggingNodeId == null) {
            return;
        }

        // 如果拖动节点属于当前选中集，则整体移动选中集；否则只移动拖动源节点
        java.util.Set<UUID> moveTargets = new java.util.HashSet<>();
        if (!interactionState.getSelectedNodeIds().isEmpty() && interactionState.getSelectedNodeIds().contains(draggingNodeId)) {
            moveTargets.addAll(interactionState.getSelectedNodeIds());
        } else {
            moveTargets.add(draggingNodeId);
        }

        boolean moved = false;
        for (UUID nodeId : moveTargets) {
            NodePosition nodePos = document.getNodePositions().get(nodeId);
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

    private static ConnectionEditService.DragPreview toConnectionDragPreview(ImGuiNodeInteraction interaction) {
        if (interaction == null || !interaction.isCreatingConnection()) {
            return null;
        }
        return new ConnectionEditService.DragPreview(
                interaction.getSourceNodeId(),
                interaction.getSourcePortId(),
                interaction.isFromOutputPort(),
                interaction.getHoveredNodeId(),
                interaction.getHoveredPortId(),
                interaction.isHoveredPortOutput()
        );
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

        IPort hoveredPort = ConnectionEditService.findPort(node, hoveredPortId, interaction.isHoveredPortOutput());
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

    /**
     * 处理鼠标右键点击节点或画布背景，打开上下文菜单。
     * @param mouseX 鼠标X屏幕坐标。
     * @param mouseY 鼠标Y屏幕坐标。
     */
    public void handleNodeRightClick(float mouseX, float mouseY) {
        // ImGui.isAnyItemActive() 检查已在 renderImGui() 调用此方法之前进行，确保优先级。
        menus.handleNodeRightClick(mouseX, mouseY);
    }

    @Override
    public INode addNode(String nodeTypeId, float x, float y) {
        return nodeCommands.addNode(nodeTypeId, x, y);
    }

    @Override
    public INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState) {
        return nodeCommands.addNodeWithState(nodeTypeId, oldNodeId, x, y, nodeState);
    }

    @Override
    public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        return connectionEdits.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
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
        if (document.getGraph() == null) {
            // NodeCraft.LOGGER.debug("getNodeIdUnderMouse: document.getGraph() 为空"); // Too verbose
            return null;
        }

        ImVec2 canvasWindowPos = ImGui.getWindowPos();
        float canvasWindowWidth = ImGui.getWindowWidth();
        float canvasWindowHeight = ImGui.getWindowHeight();
        if (mouseX < canvasWindowPos.x || mouseX > canvasWindowPos.x + canvasWindowWidth
                || mouseY < canvasWindowPos.y || mouseY > canvasWindowPos.y + canvasWindowHeight) {
            return null;
        }

        List<INode> nodes = document.getGraph().getNodes();

        // Keep hit-testing in the same layering order as rendering:
        // 1) unselected nodes, 2) selected nodes.
        // Then iterate backwards to hit-test the top-most drawn node first.
        List<INode> renderOrder = new java.util.ArrayList<>(nodes.size());
        for (INode node : nodes) {
            if (!interactionState.getSelectedNodeIds().contains(node.getId())) {
                renderOrder.add(node);
            }
        }
        for (INode node : nodes) {
            if (interactionState.getSelectedNodeIds().contains(node.getId())) {
                renderOrder.add(node);
            }
        }

        // NodeCraft.LOGGER.debug("getNodeIdUnderMouse: Checking mouse ({}, {}) vs canvas ({}, {})", mouseX, mouseY, canvasWindowPos.x, canvasWindowPos.y);

        for (int i = renderOrder.size() - 1; i >= 0; i--) { // Iterate backwards to pick top-most node
            INode node = renderOrder.get(i);
            UUID nodeId = node.getId();
            NodePosition pos = document.getNodePositions().get(nodeId);

            if (pos != null) {
                // Calculate node screen coordinates
                float nodeScreenX = canvasWindowPos.x + pos.x * viewport.getZoom() + viewport.getOffsetX();
                float nodeScreenY = canvasWindowPos.y + pos.y * viewport.getZoom() + viewport.getOffsetY();

                // Use NodePosition's stored width/height (unscaled), then apply zoom
                // Fallback to default if not yet calculated (width/height <= 0)
                float nodeWidthScaled = pos.width > 0 ? pos.width * viewport.getZoom() : 150 * viewport.getZoom();
                float nodeHeightScaled = pos.height > 0 ? pos.height * viewport.getZoom() : 80 * viewport.getZoom();

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
     * Opens the context-aware node recommendation popup at the given screen position.
     */
    public void requestNodeRecommendation(NodeRecommendationContext context, float screenX, float screenY) {
        NodeRecommendations.get().initialize();
        recommendationPopup.open(context, screenX, screenY);
    }

    /**
     * Applies a recommendation and records graph/history side effects through the editor API.
     */
    public NodeRecommendationApplyResult applyRecommendation(
            NodeRecommendationContext context,
            com.nodecraft.gui.recommendation.NodeRecommendation recommendation) {
        if (document.getGraph() == null || context == null || recommendation == null) {
            return NodeRecommendationApplyResult.failure("Editor graph or recommendation missing");
        }
        return NodeRecommendations.get().apply(this, document.getGraph(), context, recommendation);
    }

    public float screenToWorldX(float screenX) {
        ImVec2 canvasPos = ImGui.getWindowPos();
        return viewport.screenToWorldX(screenX, canvasPos.x);
    }

    public float screenToWorldY(float screenY) {
        ImVec2 canvasPos = ImGui.getWindowPos();
        return viewport.screenToWorldY(screenY, canvasPos.y);
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
        return document.getGraph();
    }

    public void setCurrentGraph(NodeGraph graph) {
        if (document.getGraph() != null) {
            String graphId = document.getGraph().getId().toString();
            VariableScopeBridge.clearFallbackScope(graphId);
            SubgraphCallStackBridge.clearFallbackScope(graphId);
        }
        document.resetForNewGraph(graph);
        subgraphEdits.clearStack();
        clearSelectedNodes();
        if (history != null) {
            history.clear();
        }
    }

    @Override
    public Map<UUID, NodePosition> getNodePositions() {
        return document.getNodePositions();
    }

    @Override
    public NodePosition getNodePosition(UUID nodeId) {
        return document.getNodePosition(nodeId);
    }

    public void setNodePositions(Map<UUID, NodePosition> positions) {
        document.replaceNodePositions(positions);
    }

    @Override
    public void clearNodePositions() {
        document.getNodePositions().clear();
    }

    @Override
    public UUID getSelectedNodeId() {
        return interactionState.getPrimarySelectedNodeId();
    }

    @Override
    public void setSelectedNodeId(UUID nodeId) {
        interactionState.setPrimarySelectedNodeId(nodeId);
        if (nodeId != null) {
            NodeCraft.LOGGER.debug("设置选中节点ID: {}, 当前选中节点数: {}", nodeId, interactionState.getSelectedNodeIds().size());
        }
        notifyEditorComponents("nodeSelected", nodeId); // 通知选择事件
    }

    public EditorInteractionState getInteractionState() {
        return interactionState;
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
        return interactionState.getSelectedNodeIds();
    }

    @Override
    public void clearSelectedNodes() {
        interactionState.clearSelection();
        NodeCraft.LOGGER.debug("已清除所有选中节点");
        notifyEditorComponents("nodeSelectionCleared", null); // 通知清除选择事件
    }

    @Override
    public void removeSelectedNode(UUID nodeId) {
        boolean wasPrimary = nodeId != null && nodeId.equals(interactionState.getPrimarySelectedNodeId());
        interactionState.removeFromSelection(nodeId);
        if (wasPrimary && !interactionState.hasSelection()) {
            notifyEditorComponents("nodeSelectionCleared", null);
        }
    }

    @Override
    public void removeNodePosition(UUID nodeId) {
        document.getNodePositions().remove(nodeId);
    }

    @Override
    public float getCanvasZoom() {
        return viewport.getZoom();
    }

    @Override
    public ExecFrontierSnapshot getActiveExecFrontierSnapshot() {
        return autoPreviewController.activeExecFrontierSnapshot();
    }

    @Override
    public void setCanvasZoom(float zoom) {
        viewport.setZoom(zoom);
    }

    @Override
    public float getCanvasOffsetX() {
        return viewport.getOffsetX();
    }

    @Override
    public float getCanvasOffsetY() {
        return viewport.getOffsetY();
    }

    @Override
    public void setCanvasOffset(float x, float y) {
        viewport.setOffset(x, y);
    }

    @Override
    public void setCanvasView(float zoom, float offsetX, float offsetY) {
        viewport.setView(zoom, offsetX, offsetY);
    }

    public boolean isShowGrid() {
        return viewport.isShowGrid();
    }

    public void setShowGrid(boolean showGrid) {
        viewport.setShowGrid(showGrid);
    }

    public EditorViewportState getViewportState() {
        return viewport;
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
    public void recordAiPatchApply(String summary, Map<UUID, Object> previousStates, int undoStepsTaken) {
        if (history != null) {
            history.recordAiPatch(summary, previousStates, undoStepsTaken);
        }
    }

    @Override
    public GraphApplyHistoryView getApplyHistoryView() {
        return history != null ? history : GraphApplyHistoryView.EMPTY;
    }

    @Override
    public GraphNodeAnchor getNodeAnchor(UUID nodeId) {
        NodePosition position = getNodePosition(nodeId);
        return position == null ? null : new GraphNodeAnchor(position.x, position.y);
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
        return nodeCommands.deleteSelected();
    }

    public boolean createSubgraphFromSelection() {
        return subgraphEdits.createFromSelection();
    }

    public boolean openSelectedSubgraph() {
        return subgraphEdits.openSelected();
    }

    public boolean closeCurrentSubgraph() {
        return subgraphEdits.closeCurrent();
    }

    public boolean isEditingSubgraph() {
        return subgraphEdits.isEditing();
    }

    @Override
    public boolean restoreGraphSnapshot(SavedGraph snapshot) {
        if (snapshot == null) {
            return false;
        }
        try {
            GraphLoadResult loadResult = GraphSerializer.loadFromSavedGraph(snapshot);
            if (!loadResult.hasLoadedNodes()) {
                return false;
            }
            SubgraphEditService.LoadedGraph loadedGraph = SubgraphEditService.toLoadedGraph(snapshot, loadResult);
            document.setGraph(loadedGraph.graph());
            document.replaceNodePositions(loadedGraph.positions());
            clearSelectedNodes();
            markGraphStructureDirty();
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to restore graph history snapshot: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean dissolveSelectedSubgraph() {
        return subgraphEdits.dissolveSelected();
    }

    boolean renameSubgraphNode(UUID nodeId, String requestedName) {
        return subgraphEdits.rename(nodeId, requestedName);
    }

    @Override
    public boolean hasUnsavedChanges() {
        return document.isDirty();
    }

    /**
     * Active document state (graph, layout, dirty generation).
     */
    public EditorDocumentState getDocument() {
        return document;
    }

    @Override
    public EditorDocumentState getDocumentState() {
        return document;
    }

    @Override
    public boolean alignNodes(java.util.Set<UUID> nodeIds, NodeAlignmentAction action) {
        return nodeCommands.align(nodeIds, action);
    }

    @Override
    public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        return connectionEdits.disconnect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
    }

    @Override
    public boolean duplicateSelectedNode() {
        return nodeCommands.duplicateSelected();
    }

    public void setNodeDisplayMode(int mode) {
        session.setNodeDisplayMode(mode);
    }

    public void setShowNodePreviews(boolean show) {
        session.setShowNodePreviews(show);
    }

    @Override
    public void setNodeCustomColor(UUID nodeId, int color) {
        session.setNodeCustomColor(nodeId, color);
    }

    @Override
    public Integer getNodeCustomColor(UUID nodeId) {
        return session.getNodeCustomColor(nodeId);
    }

    @Override
    public void removeNodeCustomColor(UUID nodeId) {
        session.removeNodeCustomColor(nodeId);
    }

    @Override
    public boolean hasNodeCustomColor(UUID nodeId) {
        return session.hasNodeCustomColor(nodeId);
    }

    @Override
    public boolean toggleNodeDisabled(UUID nodeId) {
        return session.toggleNodeDisabled(nodeId);
    }

    @Override
    public void setNodeDisabled(UUID nodeId, boolean disabled) {
        session.setNodeDisabled(nodeId, disabled);
    }

    @Override
    public boolean isNodeDisabled(UUID nodeId) {
        return session.isNodeDisabled(nodeId);
    }

    @Override
    public boolean toggleNodeVisible(UUID nodeId) {
        return session.toggleNodeVisible(nodeId);
    }

    @Override
    public void setNodeVisible(UUID nodeId, boolean visible) {
        session.setNodeVisible(nodeId, visible);
    }

    @Override
    public boolean isNodeVisible(UUID nodeId) {
        return session.isNodeVisible(nodeId);
    }

}
