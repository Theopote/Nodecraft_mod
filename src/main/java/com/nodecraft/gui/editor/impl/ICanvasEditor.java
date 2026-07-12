package com.nodecraft.gui.editor.impl;

import java.util.Map;
import java.util.UUID;
import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the interaction surface required by the canvas graph editor.
 */
public interface ICanvasEditor {
    enum NodeAlignmentAction {
        ALIGN_LEFT,
        ALIGN_CENTER,
        DISTRIBUTE_HORIZONTAL
    }

    /**
     * Returns the live exec frontier snapshot while preview execution is running.
     */
    default ExecFrontierSnapshot getActiveExecFrontierSnapshot() {
        return ExecFrontierSnapshot.EMPTY;
    }

    float getCanvasZoom();
    float getCanvasOffsetX();
    float getCanvasOffsetY();
    NodeGraph getCurrentGraph();
    UUID getSelectedNodeId();
    void setSelectedNodeId(UUID nodeId);
    boolean connectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId);
    boolean disconnectPorts(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId);

    Map<UUID, NodePosition> getNodePositions();
    NodePosition getNodePosition(UUID nodeId);
    void setCurrentGraph(NodeGraph graph);
    void setNodePositions(Map<UUID, NodePosition> positions);

    boolean isShowGrid();
    void setShowGrid(boolean showGrid);
    void setCanvasZoom(float zoom);
    void setCanvasOffset(float x, float y);
    void clearNodePositions();
    void clearSelectedNodes();
    void removeSelectedNode(UUID nodeId);
    void removeNodePosition(UUID nodeId);
    UUID getNodeIdUnderMouse(float mouseX, float mouseY);
    void close();
    INode addNode(String nodeTypeId, float x, float y);

    /**
     * Creates and adds a node with an optional historical UUID and initial state.
     * Primarily used by undo/redo history restoration.
     */
    INode addNodeWithState(String nodeTypeId, @Nullable UUID oldNodeId, float x, float y, @Nullable Object nodeState);

    java.util.Set<UUID> getSelectedNodeIds();
    void setCanvasView(float zoom, float offsetX, float offsetY);
    void pasteNodesAtPosition(float x, float y);

    ImGuiNodeInteraction getInteraction();
    Map<UUID, Map<String, ImVec2>> getPortScreenPositions();
    ImGuiNodeIO getNodeIO();
    ImGuiNodeHistory getHistory();
    ImGuiNodeClipboard getClipboard();

    boolean undo();
    boolean redo();
    boolean copySelectedNodes();
    boolean cutSelectedNodes();
    boolean pasteNodesAt(float x, float y);
    boolean deleteSelectedNodes();

    boolean createSubgraphFromSelection();
    boolean openSelectedSubgraph();
    boolean dissolveSelectedSubgraph();
    boolean restoreGraphSnapshot(SavedGraph snapshot);

    boolean hasUnsavedChanges();
    boolean duplicateSelectedNode();
    boolean alignNodes(java.util.Set<UUID> nodeIds, NodeAlignmentAction action);

    void setNodeCustomColor(UUID nodeId, int color);
    Integer getNodeCustomColor(UUID nodeId);
    void removeNodeCustomColor(UUID nodeId);
    boolean hasNodeCustomColor(UUID nodeId);

    boolean toggleNodeDisabled(UUID nodeId);
    void setNodeDisabled(UUID nodeId, boolean disabled);
    boolean isNodeDisabled(UUID nodeId);

    boolean toggleNodeVisible(UUID nodeId);
    void setNodeVisible(UUID nodeId, boolean visible);
    boolean isNodeVisible(UUID nodeId);
}
