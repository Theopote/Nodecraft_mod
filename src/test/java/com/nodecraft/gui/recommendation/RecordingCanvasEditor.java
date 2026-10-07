package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeClipboard;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.ImGuiNodeInteraction;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal {@link ICanvasEditor} that records {@link #addNode} placements for recommendation apply tests.
 */
final class RecordingCanvasEditor implements ICanvasEditor {

    record Placement(String nodeTypeId, float x, float y, UUID nodeId) {
    }

    private final NodeGraph graph;
    private final Map<UUID, NodePosition> positions = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final Set<UUID> selectedIds = new HashSet<>();
    private UUID selectedNodeId;

    RecordingCanvasEditor(NodeGraph graph) {
        this.graph = graph;
        for (INode node : graph.getNodes()) {
            positions.put(node.getId(), new NodePosition(
                (float) node.getPositionX(),
                (float) node.getPositionY()));
        }
    }

    List<Placement> placements() {
        return placements;
    }

    @Override
    public NodeGraph getCurrentGraph() {
        return graph;
    }

    @Override
    public INode addNode(String nodeTypeId, float x, float y) {
        INode node = NodeRegistry.getInstance().createNodeInstance(nodeTypeId);
        if (node == null) {
            return null;
        }
        node.setPosition(x, y);
        graph.addNode(node);
        positions.put(node.getId(), new NodePosition(x, y));
        placements.add(new Placement(nodeTypeId, x, y, node.getId()));
        return node;
    }

    @Override
    public INode addNodeWithState(
            String nodeTypeId,
            @Nullable UUID oldNodeId,
            float x,
            float y,
            @Nullable Object nodeState) {
        return addNode(nodeTypeId, x, y);
    }

    @Override
    public NodePosition getNodePosition(UUID nodeId) {
        return positions.get(nodeId);
    }

    @Override
    public Map<UUID, NodePosition> getNodePositions() {
        return positions;
    }

    @Override
    public void setNodePositions(Map<UUID, NodePosition> positions) {
        this.positions.clear();
        if (positions != null) {
            this.positions.putAll(positions);
        }
    }

    @Override
    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    @Override
    public void setSelectedNodeId(UUID nodeId) {
        this.selectedNodeId = nodeId;
        selectedIds.clear();
        if (nodeId != null) {
            selectedIds.add(nodeId);
        }
    }

    @Override
    public Set<UUID> getSelectedNodeIds() {
        return selectedIds;
    }

    @Override
    public boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        return graph.connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
    }

    @Override
    public boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        return graph.disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
    }

    @Override
    public void setCurrentGraph(NodeGraph graph) {
        // fixed graph for this stub
    }

    @Override
    public float getCanvasZoom() {
        return 1f;
    }

    @Override
    public float getCanvasOffsetX() {
        return 0f;
    }

    @Override
    public float getCanvasOffsetY() {
        return 0f;
    }

    @Override
    public boolean isShowGrid() {
        return false;
    }

    @Override
    public void setShowGrid(boolean showGrid) {
    }

    @Override
    public void setCanvasZoom(float zoom) {
    }

    @Override
    public void setCanvasOffset(float x, float y) {
    }

    @Override
    public void clearNodePositions() {
        positions.clear();
    }

    @Override
    public void clearSelectedNodes() {
        selectedIds.clear();
        selectedNodeId = null;
    }

    @Override
    public void removeSelectedNode(UUID nodeId) {
        selectedIds.remove(nodeId);
    }

    @Override
    public void removeNodePosition(UUID nodeId) {
        positions.remove(nodeId);
    }

    @Override
    public UUID getNodeIdUnderMouse(float mouseX, float mouseY) {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public void setCanvasView(float zoom, float offsetX, float offsetY) {
    }

    @Override
    public void pasteNodesAtPosition(float x, float y) {
    }

    @Override
    public ImGuiNodeInteraction getInteraction() {
        return null;
    }

    @Override
    public Map<UUID, Map<String, ImVec2>> getPortScreenPositions() {
        return Map.of();
    }

    @Override
    public ImGuiNodeIO getNodeIO() {
        return null;
    }

    @Override
    public ImGuiNodeHistory getHistory() {
        return null;
    }

    @Override
    public ImGuiNodeClipboard getClipboard() {
        return null;
    }

    @Override
    public boolean undo() {
        return false;
    }

    @Override
    public boolean redo() {
        return false;
    }

    @Override
    public boolean copySelectedNodes() {
        return false;
    }

    @Override
    public boolean cutSelectedNodes() {
        return false;
    }

    @Override
    public boolean pasteNodesAt(float x, float y) {
        return false;
    }

    @Override
    public boolean deleteSelectedNodes() {
        return false;
    }

    @Override
    public void selectAllNodes() {
    }

    @Override
    public boolean createSubgraphFromSelection() {
        return false;
    }

    @Override
    public boolean openSelectedSubgraph() {
        return false;
    }

    @Override
    public boolean dissolveSelectedSubgraph() {
        return false;
    }

    @Override
    public boolean restoreGraphSnapshot(SavedGraph snapshot) {
        return false;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return false;
    }

    @Override
    public boolean duplicateSelectedNode() {
        return false;
    }

    @Override
    public boolean alignNodes(Set<UUID> nodeIds, NodeAlignmentAction action) {
        return false;
    }

    @Override
    public void setNodeCustomColor(UUID nodeId, int color) {
    }

    @Override
    public Integer getNodeCustomColor(UUID nodeId) {
        return null;
    }

    @Override
    public void removeNodeCustomColor(UUID nodeId) {
    }

    @Override
    public boolean hasNodeCustomColor(UUID nodeId) {
        return false;
    }

    @Override
    public boolean toggleNodeDisabled(UUID nodeId) {
        return false;
    }

    @Override
    public void setNodeDisabled(UUID nodeId, boolean disabled) {
    }

    @Override
    public boolean isNodeDisabled(UUID nodeId) {
        return false;
    }

    @Override
    public boolean toggleNodeVisible(UUID nodeId) {
        return false;
    }

    @Override
    public void setNodeVisible(UUID nodeId, boolean visible) {
    }

    @Override
    public boolean isNodeVisible(UUID nodeId) {
        return true;
    }
}
