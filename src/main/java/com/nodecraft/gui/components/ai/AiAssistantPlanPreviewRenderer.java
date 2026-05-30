package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiGraphDiffService;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanConnection;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanNode;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AiAssistantPlanPreviewRenderer {

    private AiAssistantPlanPreviewRenderer() {
    }

    record State(
            boolean hasPlan,
            String summary,
            String applyModeHint,
            String focusedNodeRef,
            boolean focusScrollPending,
            int nodeCount,
            int connectionCount,
            List<String> validationErrors,
            List<String> plannedNodeLines,
            List<String> plannedConnectionLines,
            List<AiPlanNode> planNodes,
            List<AiPlanConnection> planConnections,
            TopologyPreviewState topologyPreviewState,
            AiGraphDiffService.GraphDiffSummary heuristicDiff,
            AiGraphDiffService.MappedDiffSummary mappedDiff,
            boolean canApply,
            boolean canUndo,
                String undoDisabledReason,
            String statusMessage
    ) {
    }

    interface Actions {
        void applyPlan();

        void dryRunReport();

        void saveAsTemplate();

        void undoLastApply();

        void onTopologyNodeSelected(String nodeRef);

        void onTopologyFocusScrollConsumed();
    }

    static void renderPlanPreviewSection(State state, Actions actions) {
        ImGui.separator();
        ImGui.text("Plan Preview");

        if (!state.hasPlan()) {
            ImGui.textDisabled("No plan yet. Send a prompt to generate a plan.");
            if (state.statusMessage() != null && !state.statusMessage().isBlank()) {
                AiUiHelper.renderStatusMessage(state.statusMessage());
            }
            return;
        }

        ImGui.textWrapped(state.summary() == null ? "" : state.summary());
        if (state.applyModeHint() != null && !state.applyModeHint().isBlank()) {
            ImGui.textDisabled(state.applyModeHint());
        }
        ImGui.text("Nodes: " + state.nodeCount() + "  Connections: " + state.connectionCount());

        if (state.validationErrors() != null && !state.validationErrors().isEmpty()) {
            ImGui.textColored(1.0f, 0.45f, 0.35f, 1.0f, "Validation errors:");
            for (String error : state.validationErrors()) {
                ImGui.bulletText(error);
            }
        }

        if (ImGui.treeNode("Planned Nodes")) {
            if (state.planNodes() != null && !state.planNodes().isEmpty()) {
                boolean consumedScroll = false;
                for (AiPlanNode node : state.planNodes()) {
                    String line = buildPlanNodeLine(node);
                    boolean focused = state.focusedNodeRef() != null && state.focusedNodeRef().equals(node.ref());
                    if (focused) {
                        ImGui.bullet();
                        ImGui.sameLine();
                        ImGui.textColored(0.98f, 0.85f, 0.42f, 1.0f, line);
                        if (state.focusScrollPending() && !consumedScroll) {
                            ImGui.setScrollHereY(0.35f);
                            actions.onTopologyFocusScrollConsumed();
                            consumedScroll = true;
                        }
                    } else {
                        ImGui.bulletText(line);
                    }
                }
            } else if (state.plannedNodeLines() == null || state.plannedNodeLines().isEmpty()) {
                ImGui.textDisabled("None");
            } else {
                for (String line : state.plannedNodeLines()) {
                    ImGui.bulletText(line);
                }
            }
            ImGui.treePop();
        }

        if (ImGui.treeNode("Planned Connections")) {
            if (state.plannedConnectionLines() == null || state.plannedConnectionLines().isEmpty()) {
                ImGui.textDisabled("None");
            } else {
                for (String line : state.plannedConnectionLines()) {
                    ImGui.bulletText(line);
                }
            }
            ImGui.treePop();
        }

        if (ImGui.treeNode("Graph Topology Preview")) {
            ImGui.textDisabled("Auto-layout + drag nodes to adjust manually");
            if (ImGui.smallButton("Reset Topology Layout")) {
                topologyPreviewState.reset();
            }
            String selectedNodeRef = renderTopologyPreview(state.planNodes(), state.planConnections(), state.focusedNodeRef(), topologyPreviewState);
            if (selectedNodeRef != null && !selectedNodeRef.isBlank()) {
                actions.onTopologyNodeSelected(selectedNodeRef);
            }
            ImGui.treePop();
        }

        AiGraphDiffService.GraphDiffSummary diff = state.heuristicDiff();
        if (diff != null && ImGui.treeNode("Graph Diff (Heuristic)")) {
            ImGui.textDisabled("Compared by node type+params signature and typed connection signature.");
            ImGui.text("Potential additions: nodes=" + diff.nodeAdditions() + ", connections=" + diff.connectionAdditions());
            ImGui.text("Potential missing from plan: nodes=" + diff.nodeMissingFromPlan() + ", connections=" + diff.connectionMissingFromPlan());

            renderDiffSamples("Node additions", diff.nodeAdditionSamples());
            renderDiffSamples("Node missing from plan", diff.nodeMissingSamples());
            renderDiffSamples("Connection additions", diff.connectionAdditionSamples());
            renderDiffSamples("Connection missing from plan", diff.connectionMissingSamples());
            ImGui.treePop();
        }

        AiGraphDiffService.MappedDiffSummary mapped = state.mappedDiff();
        if (mapped != null && ImGui.treeNode("Mapped Diff (Preview)")) {
            ImGui.textDisabled("Greedy matching by type+params, then type fallback. Estimates reusable vs new nodes.");
            ImGui.text("Reusable matches=" + mapped.reusableNodeMatches()
                    + ", new nodes=" + mapped.newNodesToCreate());
            ImGui.text("Unchanged reused=" + mapped.unchangedReusableNodes()
                    + ", param updates=" + mapped.paramUpdateCandidates());
            ImGui.text("Connection additions=" + mapped.connectionAdditions()
                    + ", connection removal candidates=" + mapped.connectionRemovalCandidates()
                    + ", incoming replacements=" + mapped.incomingReplacementCandidates());

            renderDiffSamples("Node reuse matches", mapped.nodeReuseSamples());
            renderDiffSamples("Node creation candidates", mapped.nodeCreationSamples());
            renderDiffSamples("Param update candidates", mapped.paramUpdateSamples());
            renderDiffSamples("Connection additions", mapped.connectionAdditionSamples());
            renderDiffSamples("Connection removal candidates", mapped.connectionRemovalSamples());
            renderDiffSamples("Incoming replacement candidates", mapped.incomingReplacementSamples());
            ImGui.treePop();
        }

        if (!state.canApply()) ImGui.beginDisabled();
        if (ImGui.button("Apply Plan")) {
            actions.applyPlan();
        }
        if (!state.canApply()) ImGui.endDisabled();

        ImGui.sameLine();
        if (!state.canApply()) ImGui.beginDisabled();
        if (ImGui.button("Dry Run Report")) {
            actions.dryRunReport();
        }
        if (!state.canApply()) ImGui.endDisabled();

        ImGui.sameLine();
        if (!state.hasPlan()) ImGui.beginDisabled();
        if (ImGui.button("Save as Template")) {
            actions.saveAsTemplate();
        }
        if (!state.hasPlan()) ImGui.endDisabled();

        ImGui.sameLine();
        if (!state.canUndo()) ImGui.beginDisabled();
        if (ImGui.button("Undo Last AI Apply")) {
            actions.undoLastApply();
        }
        if (!state.canUndo()) ImGui.endDisabled();

        if (!state.canUndo() && state.undoDisabledReason() != null && !state.undoDisabledReason().isBlank()) {
            ImGui.textDisabled(state.undoDisabledReason());
        }

        if (state.statusMessage() != null && !state.statusMessage().isBlank()) {
            AiUiHelper.renderStatusMessage(state.statusMessage());
        }
    }

    private static void renderDiffSamples(String title, List<String> samples) {
        if (!ImGui.treeNode(title)) {
            return;
        }
        if (samples == null || samples.isEmpty()) {
            ImGui.textDisabled("None");
        } else {
            for (String sample : samples) {
                ImGui.bulletText(sample);
            }
        }
        ImGui.treePop();
    }

    private static String renderTopologyPreview(
            List<AiPlanNode> nodes,
            List<AiPlanConnection> connections,
            String focusedNodeRef,
            TopologyPreviewState topologyPreviewState
    ) {
        if (nodes == null || nodes.isEmpty()) {
            ImGui.textDisabled("No nodes to preview.");
            return null;
        }

        float previewWidth = Math.max(240.0f, ImGui.getContentRegionAvailX());
        float previewHeight = 170.0f;
        float densityScale = clamp(1.0f - Math.max(0, nodes.size() - 8) * 0.022f, 0.72f, 1.0f);
        float nodeWidth = 90.0f * densityScale;
        float nodeHeight = 28.0f * densityScale;
        float padding = 16.0f;
        float contentWidth = Math.max(1.0f, previewWidth - 2.0f * padding - nodeWidth);
        float contentHeight = Math.max(1.0f, previewHeight - 2.0f * padding - nodeHeight);

        String planKey = buildTopologyPlanKey(nodes, connections);
        topologyPreviewState.updatePlanKey(planKey);

        ImDrawList drawList = ImGui.getWindowDrawList();
        ImVec2 cursor = ImGui.getCursorScreenPos();

        int bgColor = ImGui.colorConvertFloat4ToU32(0.10f, 0.12f, 0.14f, 1.0f);
        int borderColor = ImGui.colorConvertFloat4ToU32(0.23f, 0.27f, 0.32f, 1.0f);
        int nodeColor = ImGui.colorConvertFloat4ToU32(0.20f, 0.42f, 0.66f, 1.0f);
        int focusedNodeColor = ImGui.colorConvertFloat4ToU32(0.78f, 0.56f, 0.18f, 1.0f);
        int nodeBorderColor = ImGui.colorConvertFloat4ToU32(0.58f, 0.74f, 0.88f, 1.0f);
        int focusedNodeBorderColor = ImGui.colorConvertFloat4ToU32(0.98f, 0.88f, 0.58f, 1.0f);
        int textColor = ImGui.colorConvertFloat4ToU32(0.96f, 0.97f, 0.98f, 1.0f);
        int edgeColor = ImGui.colorConvertFloat4ToU32(0.72f, 0.78f, 0.84f, 0.95f);

        drawList.addRectFilled(cursor.x, cursor.y, cursor.x + previewWidth, cursor.y + previewHeight, bgColor, 6.0f);
        drawList.addRect(cursor.x, cursor.y, cursor.x + previewWidth, cursor.y + previewHeight, borderColor, 6.0f, 0, 1.0f);

        Map<String, float[]> autoUvByRef = buildAdaptiveTopologyUv(nodes, connections, contentWidth, contentHeight, nodeWidth, nodeHeight);
        Map<String, float[]> nodeAnchors = new HashMap<>();
        for (AiPlanNode node : nodes) {
            float[] uv = topologyPreviewState.getManualUv(node.ref());
            if (uv == null) {
                uv = autoUvByRef.get(node.ref());
            }
            if (uv == null) {
                uv = new float[]{0.5f, 0.5f};
            }
            float nx = cursor.x + padding + clamp(uv[0], 0.0f, 1.0f) * contentWidth;
            float ny = cursor.y + padding + clamp(uv[1], 0.0f, 1.0f) * contentHeight;
            nodeAnchors.put(node.ref(), new float[]{nx, ny});
        }

        ImVec2 mouse = ImGui.getIO().getMousePos();
        boolean mouseInCanvas = pointInRect(mouse.x, mouse.y, cursor.x, cursor.y, previewWidth, previewHeight);

        if (topologyPreviewState.getDraggingNodeRef() != null) {
            if (ImGui.isMouseDown(0)) {
                float nx = clamp(mouse.x - topologyPreviewState.getDragOffsetX(), cursor.x + padding, cursor.x + padding + contentWidth);
                float ny = clamp(mouse.y - topologyPreviewState.getDragOffsetY(), cursor.y + padding, cursor.y + padding + contentHeight);
                float u = (nx - (cursor.x + padding)) / contentWidth;
                float v = (ny - (cursor.y + padding)) / contentHeight;
                String draggingNodeRef = topologyPreviewState.getDraggingNodeRef();
                topologyPreviewState.setManualUv(draggingNodeRef, clamp(u, 0.0f, 1.0f), clamp(v, 0.0f, 1.0f));
                nodeAnchors.put(draggingNodeRef, new float[]{nx, ny});
            } else {
                topologyPreviewState.stopDragging();
            }
        }

        if (mouseInCanvas && ImGui.isMouseClicked(0) && topologyPreviewState.getDraggingNodeRef() == null) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                AiPlanNode node = nodes.get(i);
                float[] anchor = nodeAnchors.get(node.ref());
                if (anchor == null) {
                    continue;
                }
                float nx = anchor[0];
                float ny = anchor[1];
                if (mouse.x >= nx && mouse.x <= nx + nodeWidth && mouse.y >= ny && mouse.y <= ny + nodeHeight) {
                    topologyPreviewState.startDragging(node.ref(), mouse.x - nx, mouse.y - ny);
                    break;
                }
            }
        }

        if (connections != null) {
            for (AiPlanConnection connection : connections) {
                float[] from = nodeAnchors.get(connection.sourceRef());
                float[] to = nodeAnchors.get(connection.targetRef());
                if (from == null || to == null) {
                    continue;
                }
                float x1 = from[0] + nodeWidth;
                float y1 = from[1] + nodeHeight * 0.5f;
                float x2 = to[0];
                float y2 = to[1] + nodeHeight * 0.5f;
                drawList.addLine(x1, y1, x2, y2, edgeColor, 1.4f);
            }
        }

        for (AiPlanNode node : nodes) {
            float[] anchor = nodeAnchors.get(node.ref());
            if (anchor == null) {
                continue;
            }
            float nx = anchor[0];
            float ny = anchor[1];
            boolean focused = focusedNodeRef != null && focusedNodeRef.equals(node.ref());
            drawList.addRectFilled(nx, ny, nx + nodeWidth, ny + nodeHeight, focused ? focusedNodeColor : nodeColor, 4.0f);
            drawList.addRect(nx, ny, nx + nodeWidth, ny + nodeHeight, focused ? focusedNodeBorderColor : nodeBorderColor, 4.0f, 0, 1.0f);
            drawList.addText(nx + 5.0f, ny + 7.0f, textColor, shortTypeId(node.typeId()));
        }

        String clickedRef = null;
        if (mouseInCanvas && ImGui.isMouseClicked(0)) {
            for (AiPlanNode node : nodes) {
                float[] anchor = nodeAnchors.get(node.ref());
                if (anchor == null) {
                    continue;
                }
                float nx = anchor[0];
                float ny = anchor[1];
                if (mouse.x >= nx && mouse.x <= nx + nodeWidth && mouse.y >= ny && mouse.y <= ny + nodeHeight) {
                    clickedRef = node.ref();
                    break;
                }
            }
        }

        ImGui.dummy(previewWidth, previewHeight);
        return clickedRef;
    }

    private static String buildTopologyPlanKey(List<AiPlanNode> nodes, List<AiPlanConnection> connections) {
        if (nodes == null || nodes.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder(256);
        for (AiPlanNode node : nodes) {
            sb.append(node.ref()).append('|').append(node.typeId()).append(';');
        }
        sb.append('#');
        if (connections != null) {
            for (AiPlanConnection connection : connections) {
                sb.append(connection.sourceRef()).append('.').append(connection.sourcePortId())
                        .append("->")
                        .append(connection.targetRef()).append('.').append(connection.targetPortId())
                        .append(';');
            }
        }
        return sb.toString();
    }

    private static Map<String, float[]> buildAdaptiveTopologyUv(
            List<AiPlanNode> nodes,
            List<AiPlanConnection> connections,
            float contentWidth,
            float contentHeight,
            float nodeWidth,
            float nodeHeight
    ) {
        Map<String, float[]> result = new HashMap<>();
        if (nodes == null || nodes.isEmpty()) {
            return result;
        }

        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (AiPlanNode node : nodes) {
            outgoing.put(node.ref(), new LinkedHashSet<>());
            indegree.put(node.ref(), 0);
        }
        if (connections != null) {
            for (AiPlanConnection connection : connections) {
                if (!outgoing.containsKey(connection.sourceRef()) || !indegree.containsKey(connection.targetRef())) {
                    continue;
                }
                if (outgoing.get(connection.sourceRef()).add(connection.targetRef())) {
                    indegree.put(connection.targetRef(), indegree.get(connection.targetRef()) + 1);
                }
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        List<String> sortedRefs = nodes.stream().map(AiPlanNode::ref).sorted().toList();
        for (String ref : sortedRefs) {
            if (indegree.getOrDefault(ref, 0) == 0) {
                queue.add(ref);
            }
        }

        List<String> topoOrder = new ArrayList<>(nodes.size());
        Map<String, Integer> layer = new HashMap<>();
        while (!queue.isEmpty()) {
            String ref = queue.removeFirst();
            topoOrder.add(ref);
            int currentLayer = layer.getOrDefault(ref, 0);
            for (String target : outgoing.getOrDefault(ref, Set.of())) {
                layer.put(target, Math.max(layer.getOrDefault(target, 0), currentLayer + 1));
                int next = indegree.getOrDefault(target, 0) - 1;
                indegree.put(target, next);
                if (next == 0) {
                    queue.addLast(target);
                }
            }
        }

        Set<String> visited = new LinkedHashSet<>(topoOrder);
        for (String ref : sortedRefs) {
            if (!visited.contains(ref)) {
                topoOrder.add(ref);
                layer.putIfAbsent(ref, 0);
            }
        }

        Map<Integer, List<String>> byLayer = new LinkedHashMap<>();
        int maxLayer = 0;
        for (String ref : topoOrder) {
            int lv = Math.max(0, layer.getOrDefault(ref, 0));
            maxLayer = Math.max(maxLayer, lv);
            byLayer.computeIfAbsent(lv, key -> new ArrayList<>()).add(ref);
        }

        float vGap = 8.0f;
        float intraColumnXGap = 18.0f;
        float interLayerGap = 44.0f;
        int maxRowsPerColumn = Math.max(1, (int) ((contentHeight + vGap) / Math.max(1.0f, nodeHeight + vGap)));

        Map<String, float[]> pre = new HashMap<>();
        float xCursor = 0.0f;
        for (int lv = 0; lv <= maxLayer; lv++) {
            List<String> refs = byLayer.getOrDefault(lv, List.of());
            if (refs.isEmpty()) {
                xCursor += nodeWidth + interLayerGap;
                continue;
            }

            refs.sort(Comparator.naturalOrder());
            int cols = Math.max(1, (int) Math.ceil((double) refs.size() / (double) maxRowsPerColumn));
            float layerWidth = nodeWidth + (cols - 1) * (nodeWidth + intraColumnXGap);

            for (int i = 0; i < refs.size(); i++) {
                int col = i / maxRowsPerColumn;
                int row = i % maxRowsPerColumn;
                float px = xCursor + col * (nodeWidth + intraColumnXGap);
                float py = row * (nodeHeight + vGap);
                pre.put(refs.get(i), new float[]{px, py});
            }

            xCursor += layerWidth + interLayerGap;
        }

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] point : pre.values()) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }

        float spanX = Math.max(1.0f, maxX - minX);
        float spanY = Math.max(1.0f, maxY - minY);
        for (AiPlanNode node : nodes) {
            float[] point = pre.get(node.ref());
            if (point == null) {
                result.put(node.ref(), new float[]{0.5f, 0.5f});
                continue;
            }
            float u = clamp((point[0] - minX) / spanX, 0.0f, 1.0f);
            float v = clamp((point[1] - minY) / spanY, 0.0f, 1.0f);
            result.put(node.ref(), new float[]{u, v});
        }

        return result;
    }

    private static boolean pointInRect(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    private static String buildPlanNodeLine(AiPlanNode node) {
        if (node == null) {
            return "unknown";
        }
        return node.ref() + " -> " + node.typeId()
                + "  (" + Math.round(node.offsetX())
                + ", " + Math.round(node.offsetY()) + ")";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortTypeId(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return "unknown";
        }

        String normalized = typeId;
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            normalized = normalized.substring(slash + 1);
        }
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            normalized = normalized.substring(dot + 1);
        }
        if (normalized.length() > 15) {
            return normalized.substring(0, 12) + "...";
        }
        return normalized;
    }

}
