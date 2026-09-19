package com.nodecraft.gui.editor.subgraph;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.GraphLoadResult;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.SubgraphExtractionService;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns nested subgraph edit stack and create/open/close/dissolve/rename operations.
 * <p>
 * See {@code docs/architecture/imgui-node-editor-breakup.md} (Phase K).
 */
public final class SubgraphEditService {

    /**
     * Editor-facing hooks used while mutating the active document / selection / history.
     */
    public interface Host {
        EditorDocumentState document();

        EditorInteractionState interaction();

        @Nullable
        ImGuiNodeHistory history();

        @Nullable
        INode addNodeWithState(
                String nodeTypeId,
                @Nullable UUID oldNodeId,
                float x,
                float y,
                @Nullable Object nodeState
        );

        void removeNodePosition(UUID nodeId);

        void removeSelectedNode(UUID nodeId);

        void clearSelectedNodes();

        void setSelectedNodeId(UUID nodeId);

        void notifyStructureDirty();

        void showSubgraphLoadNotice(String message);
    }

    private final Host host;
    private final Deque<EditContext> editStack = new ArrayDeque<>();

    public SubgraphEditService(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public boolean isEditing() {
        return !editStack.isEmpty();
    }

    public int editDepth() {
        return editStack.size();
    }

    public void clearStack() {
        editStack.clear();
    }

    public boolean createFromSelection() {
        EditorDocumentState document = host.document();
        EditorInteractionState interaction = host.interaction();
        if (document.getGraph() == null || interaction.getSelectedNodeIds().isEmpty()) {
            return false;
        }

        Set<UUID> selection = new LinkedHashSet<>(interaction.getSelectedNodeIds());
        String subgraphName = document.getGraph().getName() != null && !document.getGraph().getName().isBlank()
                ? document.getGraph().getName() + " Selection"
                : "Extracted Subgraph";
        ImGuiNodeHistory history = host.history();
        boolean wasRecording = history != null && history.isRecording();
        SavedGraph beforeSnapshot = null;

        try {
            syncGraphNodePositions(document.getGraph(), document.getNodePositions());
            if (wasRecording) {
                beforeSnapshot = toSavedGraphWithPositions(document.getGraph(), document.getNodePositions());
            }
            SubgraphExtractionService.ExtractionResult extraction =
                    SubgraphExtractionService.extract(document.getGraph(), selection, subgraphName);
            String embeddedGraphJson = GraphSerializer.toJson(extraction.savedGraph());
            Map<String, Object> subgraphState = buildSubgraphNodeState(extraction, subgraphName, embeddedGraphJson);
            NodePosition wrapperPosition = selectionCenter(document.getNodePositions(), selection);

            if (wasRecording) {
                history.pauseRecording();
            }
            try {
                INode wrapper = host.addNodeWithState(
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
                    if (document.getGraph().removeNode(nodeId)) {
                        host.removeNodePosition(nodeId);
                        host.removeSelectedNode(nodeId);
                    }
                }

                reconnectSubgraphBoundaries(document.getGraph(), wrapper.getId(), extraction);
                host.clearSelectedNodes();
                host.setSelectedNodeId(wrapper.getId());
                host.notifyStructureDirty();
                if (wasRecording) {
                    SavedGraph afterSnapshot = toSavedGraphWithPositions(document.getGraph(), document.getNodePositions());
                    history.resumeRecording();
                    history.recordGraphTransaction("Create Subgraph", beforeSnapshot, afterSnapshot);
                    history.pauseRecording();
                }
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

    public boolean openSelected() {
        EditorInteractionState interaction = host.interaction();
        if (interaction.getSelectedNodeIds().size() != 1) {
            return false;
        }
        return openNode(interaction.getSelectedNodeIds().iterator().next());
    }

    public boolean closeCurrent() {
        EditorDocumentState document = host.document();
        if (editStack.isEmpty() || document.getGraph() == null) {
            return false;
        }

        EditContext context = editStack.pop();
        try {
            syncGraphNodePositions(document.getGraph(), document.getNodePositions());
            SavedGraph savedGraph = toSavedGraphWithPositions(document.getGraph(), document.getNodePositions());
            String embeddedGraphJson = GraphSerializer.toJson(savedGraph);

            INode wrapperNode = context.parentGraph().getNode(context.wrapperNodeId());
            if (wrapperNode instanceof BaseNode wrapperBase) {
                Map<String, Object> state = copyStateMap(wrapperBase.getNodeState());
                state.put("embeddedGraphJson", embeddedGraphJson);
                wrapperBase.setNodeState(state);
            } else {
                NodeCraft.LOGGER.warn(
                        "Cannot write edited subgraph back because wrapper node is missing: {}",
                        context.wrapperNodeId()
                );
            }

            document.setGraph(context.parentGraph());
            document.replaceNodePositions(copyNodePositions(context.parentPositions()));
            host.clearSelectedNodes();
            if (document.getGraph().getNode(context.wrapperNodeId()) != null) {
                host.interaction().getSelectedNodeIds().add(context.wrapperNodeId());
                host.setSelectedNodeId(context.wrapperNodeId());
            }
            host.notifyStructureDirty();
            SavedGraph parentAfter = toSavedGraphWithPositions(document.getGraph(), document.getNodePositions());
            ImGuiNodeHistory history = host.history();
            if (history != null) {
                history.exitScope();
                history.recordGraphTransaction("Edit Subgraph", context.parentSnapshotBefore(), parentAfter);
            }
            NodeCraft.LOGGER.info("Closed subgraph editor and wrote changes back to wrapper {}", context.wrapperNodeId());
            return true;
        } catch (Exception e) {
            editStack.push(context);
            NodeCraft.LOGGER.error("Failed to close subgraph editor: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean openNode(UUID wrapperNodeId) {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null || wrapperNodeId == null) {
            return false;
        }
        INode wrapperNode = document.getGraph().getNode(wrapperNodeId);
        if (!isSubgraphNode(wrapperNode)) {
            return false;
        }

        String embeddedGraphJson = stateString(
                wrapperNode instanceof BaseNode baseNode ? baseNode.getNodeState() : null,
                "embeddedGraphJson"
        );
        if (embeddedGraphJson == null || embeddedGraphJson.isBlank()) {
            return false;
        }

        try {
            SavedGraph savedGraph = GraphSerializer.fromJson(embeddedGraphJson);
            GraphLoadResult loadResult = GraphSerializer.loadFromSavedGraph(savedGraph);
            if (!loadResult.hasLoadedNodes()) {
                return false;
            }
            LoadedGraph loadedGraph = toLoadedGraph(savedGraph, loadResult);

            syncGraphNodePositions(document.getGraph(), document.getNodePositions());
            SavedGraph parentSnapshotBefore = toSavedGraphWithPositions(document.getGraph(), document.getNodePositions());
            editStack.push(new EditContext(
                    document.getGraph(),
                    copyNodePositions(document.getNodePositions()),
                    wrapperNodeId,
                    parentSnapshotBefore
            ));
            ImGuiNodeHistory history = host.history();
            if (history != null) {
                history.enterScope();
            }
            document.setGraph(loadedGraph.graph());
            document.replaceNodePositions(loadedGraph.positions());
            host.clearSelectedNodes();
            host.notifyStructureDirty();
            NodeCraft.LOGGER.info("Opened embedded subgraph from wrapper {}", wrapperNodeId);

            String notice = loadResult.userMessage();
            if (notice != null && !notice.isBlank()) {
                host.showSubgraphLoadNotice(notice);
            }
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to open embedded subgraph: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean dissolveSelected() {
        EditorDocumentState document = host.document();
        EditorInteractionState interaction = host.interaction();
        if (document.getGraph() == null || interaction.getSelectedNodeIds().size() != 1) {
            return false;
        }

        UUID wrapperNodeId = interaction.getSelectedNodeIds().iterator().next();
        INode wrapperNode = document.getGraph().getNode(wrapperNodeId);
        if (!isSubgraphNode(wrapperNode)) {
            return false;
        }

        String embeddedGraphJson = stateString(
                wrapperNode instanceof BaseNode baseNode ? baseNode.getNodeState() : null,
                "embeddedGraphJson"
        );
        if (embeddedGraphJson == null || embeddedGraphJson.isBlank()) {
            return false;
        }

        ImGuiNodeHistory history = host.history();
        boolean wasRecording = history != null && history.isRecording();
        syncGraphNodePositions(document.getGraph(), document.getNodePositions());
        SavedGraph beforeSnapshot = wasRecording
                ? toSavedGraphWithPositions(document.getGraph(), document.getNodePositions())
                : null;

        try {
            SavedGraph savedGraph = GraphSerializer.fromJson(embeddedGraphJson);
            if (savedGraph == null || savedGraph.nodes == null || savedGraph.nodes.isEmpty()) {
                return false;
            }

            Map<String, String> graphInputKeys = new HashMap<>();
            Map<String, String> graphOutputKeys = new HashMap<>();
            for (SavedNode savedNode : savedGraph.nodes) {
                if (savedNode == null || savedNode.nodeId == null) {
                    continue;
                }
                if (SubgraphExtractionService.GRAPH_INPUT_TYPE_ID.equals(savedNode.typeId)) {
                    graphInputKeys.put(savedNode.nodeId, stateString(savedNode.state, "inputName"));
                } else if (SubgraphExtractionService.GRAPH_OUTPUT_TYPE_ID.equals(savedNode.typeId)) {
                    graphOutputKeys.put(savedNode.nodeId, stateString(savedNode.state, "outputName"));
                }
            }

            Map<String, List<BoundaryInputTarget>> inputTargets = new HashMap<>();
            Map<String, List<BoundaryOutputSource>> outputSources = new HashMap<>();
            if (savedGraph.connections != null) {
                for (SavedConnection connection : savedGraph.connections) {
                    String inputKey = graphInputKeys.get(connection.sourceNodeId);
                    if (inputKey != null) {
                        inputTargets.computeIfAbsent(keyToken(inputKey), ignored -> new ArrayList<>())
                                .add(new BoundaryInputTarget(connection.targetNodeId, connection.targetPortId));
                    }

                    String outputKey = graphOutputKeys.get(connection.targetNodeId);
                    if (outputKey != null) {
                        outputSources.computeIfAbsent(keyToken(outputKey), ignored -> new ArrayList<>())
                                .add(new BoundaryOutputSource(connection.sourceNodeId, connection.sourcePortId));
                    }
                }
            }

            List<NodeGraph.Connection> wrapperInputs = new ArrayList<>();
            List<NodeGraph.Connection> wrapperOutputs = new ArrayList<>();
            for (NodeGraph.Connection connection : document.getGraph().getConnections()) {
                if (connection.targetNode.getId().equals(wrapperNodeId)) {
                    wrapperInputs.add(connection);
                } else if (connection.sourceNode.getId().equals(wrapperNodeId)) {
                    wrapperOutputs.add(connection);
                }
            }

            NodePosition wrapperPosition = document.getNodePositions().getOrDefault(wrapperNodeId, new NodePosition(0.0f, 0.0f));
            NodePosition savedCenter = savedGraphCenter(savedGraph);
            float offsetX = wrapperPosition.x - savedCenter.x;
            float offsetY = wrapperPosition.y - savedCenter.y;

            Map<String, UUID> restoredNodeIds = new HashMap<>();
            int fallbackIndex = 0;
            for (SavedNode savedNode : savedGraph.nodes) {
                if (savedNode == null
                        || savedNode.nodeId == null
                        || graphInputKeys.containsKey(savedNode.nodeId)
                        || graphOutputKeys.containsKey(savedNode.nodeId)) {
                    continue;
                }

                BaseNode restoredBase = GraphSerializer.tryRestoreNode(savedNode).orElse(null);
                if (restoredBase == null) {
                    NodeCraft.LOGGER.warn(
                            "Skipping subgraph node during dissolve because it cannot be recreated: {}",
                            savedNode.typeId
                    );
                    continue;
                }

                document.getGraph().addNode(restoredBase);
                restoredNodeIds.put(savedNode.nodeId, restoredBase.getId());

                SavedPosition savedPosition =
                        savedGraph.nodePositions != null ? savedGraph.nodePositions.get(savedNode.nodeId) : null;
                float x = savedPosition != null ? savedPosition.x + offsetX : wrapperPosition.x + fallbackIndex * 24.0f;
                float y = savedPosition != null ? savedPosition.y + offsetY : wrapperPosition.y + fallbackIndex * 18.0f;
                document.getNodePositions().put(restoredBase.getId(), new NodePosition(x, y));
                restoredBase.setPosition(x, y);
                fallbackIndex++;
            }

            if (restoredNodeIds.isEmpty()) {
                return false;
            }

            if (savedGraph.connections != null) {
                for (SavedConnection connection : savedGraph.connections) {
                    UUID sourceId = restoredNodeIds.get(connection.sourceNodeId);
                    UUID targetId = restoredNodeIds.get(connection.targetNodeId);
                    if (sourceId != null && targetId != null) {
                        document.getGraph().connect(sourceId, connection.sourcePortId, targetId, connection.targetPortId);
                    }
                }
            }

            document.getGraph().removeNode(wrapperNodeId);
            host.removeNodePosition(wrapperNodeId);
            host.removeSelectedNode(wrapperNodeId);

            for (NodeGraph.Connection wrapperInput : wrapperInputs) {
                String inputKey = dynamicKeyFromInputPortId(wrapperInput.targetPort.getId());
                List<BoundaryInputTarget> targets = inputTargets.get(inputKey);
                if (targets == null) {
                    continue;
                }
                for (BoundaryInputTarget target : targets) {
                    UUID restoredTargetId = restoredNodeIds.get(target.nodeId());
                    if (restoredTargetId != null) {
                        document.getGraph().connect(
                                wrapperInput.sourceNode.getId(),
                                wrapperInput.sourcePort.getId(),
                                restoredTargetId,
                                target.portId()
                        );
                    }
                }
            }

            for (NodeGraph.Connection wrapperOutput : wrapperOutputs) {
                String outputKey = dynamicKeyFromOutputPortId(wrapperOutput.sourcePort.getId());
                List<BoundaryOutputSource> sources = outputSources.get(outputKey);
                if (sources == null) {
                    continue;
                }
                for (BoundaryOutputSource source : sources) {
                    UUID restoredSourceId = restoredNodeIds.get(source.nodeId());
                    if (restoredSourceId != null) {
                        document.getGraph().connect(
                                restoredSourceId,
                                source.portId(),
                                wrapperOutput.targetNode.getId(),
                                wrapperOutput.targetPort.getId()
                        );
                    }
                }
            }

            host.clearSelectedNodes();
            interaction.getSelectedNodeIds().addAll(restoredNodeIds.values());
            host.setSelectedNodeId(restoredNodeIds.values().iterator().next());
            host.notifyStructureDirty();
            if (wasRecording) {
                history.recordGraphTransaction(
                        "Dissolve Subgraph",
                        beforeSnapshot,
                        toSavedGraphWithPositions(document.getGraph(), document.getNodePositions())
                );
            }
            NodeCraft.LOGGER.info("Dissolved subgraph node {} into {} nodes", wrapperNodeId, restoredNodeIds.size());
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("Failed to dissolve selected subgraph: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean rename(UUID nodeId, String requestedName) {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null || nodeId == null || requestedName == null) {
            return false;
        }
        String name = requestedName.trim();
        INode node = document.getGraph().getNode(nodeId);
        if (!(node instanceof SubgraphNode subgraphNode) || name.isEmpty()) {
            return false;
        }
        if (name.equals(subgraphNode.getDisplayName())) {
            return true;
        }

        ImGuiNodeHistory history = host.history();
        boolean wasRecording = history != null && history.isRecording();
        syncGraphNodePositions(document.getGraph(), document.getNodePositions());
        SavedGraph before = wasRecording
                ? toSavedGraphWithPositions(document.getGraph(), document.getNodePositions())
                : null;
        Map<String, Object> state = copyStateMap(subgraphNode.getNodeState());
        state.put("displayName", name);
        subgraphNode.setNodeState(state);
        host.notifyStructureDirty();
        if (wasRecording) {
            history.recordGraphTransaction(
                    "Rename Subgraph",
                    before,
                    toSavedGraphWithPositions(document.getGraph(), document.getNodePositions())
            );
        }
        return true;
    }

    /**
     * Loads a saved graph into document-shaped {@link LoadedGraph} (used by history restore).
     */
    public static LoadedGraph toLoadedGraph(SavedGraph savedGraph, GraphLoadResult loadResult) {
        Map<UUID, NodePosition> positions = ImGuiNodeIO.buildEditorPositions(savedGraph, loadResult.nodesBySavedId());
        return new LoadedGraph(loadResult.graph(), positions);
    }

    public static SavedGraph toSavedGraphWithPositions(NodeGraph graph, Map<UUID, NodePosition> positions) {
        SavedGraph savedGraph = GraphSerializer.toSavedGraph(graph);
        savedGraph.nodePositions = new LinkedHashMap<>();
        if (positions != null) {
            for (Map.Entry<UUID, NodePosition> entry : positions.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                savedGraph.nodePositions.put(
                        entry.getKey().toString(),
                        new SavedPosition(entry.getValue().x, entry.getValue().y)
                );
            }
        }
        return savedGraph;
    }

    private Map<String, Object> buildSubgraphNodeState(
            SubgraphExtractionService.ExtractionResult extraction,
            String subgraphName,
            String embeddedGraphJson
    ) {
        List<String> inputKeys = extraction.inputKeys();
        List<String> outputKeys = extraction.outputKeys();
        String primaryInputKey = inputKeys.isEmpty() ? "in" : inputKeys.getFirst();
        String primaryOutputKey = outputKeys.isEmpty() ? "out" : outputKeys.getFirst();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("displayName", subgraphName);
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

    private static void reconnectSubgraphBoundaries(
            NodeGraph graph,
            UUID wrapperNodeId,
            SubgraphExtractionService.ExtractionResult extraction
    ) {
        for (SubgraphExtractionService.InputBinding binding : extraction.inputBindings()) {
            graph.connect(
                    binding.externalSourceNodeId(),
                    binding.externalSourcePortId(),
                    wrapperNodeId,
                    dynamicInputPortId(binding.inputKey())
            );
        }

        for (SubgraphExtractionService.OutputBinding binding : extraction.outputBindings()) {
            graph.connect(
                    wrapperNodeId,
                    dynamicOutputPortId(binding.outputKey()),
                    binding.externalTargetNodeId(),
                    binding.externalTargetPortId()
            );
        }
    }

    private static Map<UUID, NodePosition> copyNodePositions(Map<UUID, NodePosition> source) {
        Map<UUID, NodePosition> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<UUID, NodePosition> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue().copy());
            }
        }
        return copy;
    }

    private static Map<String, Object> copyStateMap(Object state) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (state instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, entry.getValue());
                }
            }
        }
        return copy;
    }

    private static void syncGraphNodePositions(NodeGraph graph, Map<UUID, NodePosition> positions) {
        if (graph == null || positions == null) {
            return;
        }
        for (Map.Entry<UUID, NodePosition> entry : positions.entrySet()) {
            INode node = graph.getNode(entry.getKey());
            NodePosition position = entry.getValue();
            if (node != null && position != null) {
                node.setPosition(position.x, position.y);
            }
        }
    }

    private static NodePosition selectionCenter(Map<UUID, NodePosition> positions, Set<UUID> selection) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;

        for (UUID nodeId : selection) {
            NodePosition position = positions.get(nodeId);
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

    private static boolean isSubgraphNode(INode node) {
        if (node == null || node.getTypeId() == null) {
            return false;
        }
        String canonicalId = NodeRegistry.getInstance().resolveCanonicalNodeId(node.getTypeId());
        return "utilities.organization.subgraph".equals(canonicalId);
    }

    private static String stateString(Object state, String key) {
        if (!(state instanceof Map<?, ?> map) || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    private static NodePosition savedGraphCenter(SavedGraph savedGraph) {
        if (savedGraph == null || savedGraph.nodePositions == null || savedGraph.nodePositions.isEmpty()) {
            return new NodePosition(0.0f, 0.0f);
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;

        for (Map.Entry<String, SavedPosition> entry : savedGraph.nodePositions.entrySet()) {
            SavedPosition position = entry.getValue();
            if (position == null) {
                continue;
            }
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            maxX = Math.max(maxX, position.x);
            maxY = Math.max(maxY, position.y);
            found = true;
        }

        return found
                ? new NodePosition((minX + maxX) / 2.0f, (minY + maxY) / 2.0f)
                : new NodePosition(0.0f, 0.0f);
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

    private static String dynamicKeyFromInputPortId(String portId) {
        String prefix = "dynamic_input_key_";
        return portId != null && portId.startsWith(prefix) ? portId.substring(prefix.length()) : null;
    }

    private static String dynamicKeyFromOutputPortId(String portId) {
        String prefix = "dynamic_output_key_";
        return portId != null && portId.startsWith(prefix) ? portId.substring(prefix.length()) : null;
    }

    private static String keyToken(String key) {
        if (key == null || key.isBlank()) {
            return "empty";
        }
        String normalized = key.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        return normalized.isEmpty() ? "empty" : normalized;
    }

    public record LoadedGraph(NodeGraph graph, Map<UUID, NodePosition> positions) {
    }

    private record BoundaryInputTarget(String nodeId, String portId) {
    }

    private record BoundaryOutputSource(String nodeId, String portId) {
    }

    private record EditContext(
            NodeGraph parentGraph,
            Map<UUID, NodePosition> parentPositions,
            UUID wrapperNodeId,
            SavedGraph parentSnapshotBefore
    ) {
    }
}
