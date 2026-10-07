package com.nodecraft.gui.recommendation;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.OptionalPortDrive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefaultNodeRecommendationService implements NodeRecommendationService {

    private static final String GET_BOX_FACE_TYPE_ID = "reference.points.get_box_face";
    private static final String OUTPUT_FACE_PORT_ID = "output_face";
    private static final String INPUT_FACE_NAME_PORT_ID = "input_face_name";
    private static final String OUTPUT_NAME_PORT_ID = "output_name";
    /** Max exact-rule ports kept for selection/context recommendations. */
    private static final int MAX_EXACT_RULE_PORTS = 6;

    private final NodePortIndex portIndex = new NodePortIndex();
    private NodeRecommendationRules rules = new NodeRecommendationRules();
    private NodeRecommendationScorer scorer;
    private NodeRecommendationConnector connector;
    private volatile boolean initialized;

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        rules = NodeRecommendationRulesLoader.load();
        scorer = new NodeRecommendationScorer(rules, portIndex);
        connector = new NodeRecommendationConnector(portIndex, rules);
        initialized = true;
        NodeCraft.LOGGER.info("Node recommendation service initialized (rules v{})", rules.version);
    }

    @Override
    public synchronized void invalidateCache() {
        portIndex.invalidate();
    }

    @Override
    public synchronized void reloadRules() {
        rules = NodeRecommendationRulesLoader.load();
        scorer = new NodeRecommendationScorer(rules, portIndex);
        connector = new NodeRecommendationConnector(portIndex, rules);
        initialized = true;
    }

    @Override
    public List<NodeRecommendation> recommend(NodeGraph graph, NodeRecommendationContext context) {
        ensureInitialized();
        if (graph == null || context == null || context.sourceNodeId() == null) {
            return List.of();
        }

        INode sourceNode = graph.getNode(context.sourceNodeId());
        if (sourceNode == null) {
            return List.of();
        }

        if (isSelectionLevelSink(context, sourceNode)) {
            return List.of();
        }

        int limit = context.limit() != null ? context.limit() : rules.defaults.limit;
        NodeInfo sourceInfo = NodeRegistry.getInstance().getNodeInfo(sourceNode.getTypeId());
        String sourceCategory = sourceInfo != null ? sourceInfo.getCategoryId() : null;

        List<SourcePortContext> sourcePorts = resolveSourcePorts(sourceNode, context);
        List<NodeRecommendation> raw = new ArrayList<>();

        for (SourcePortContext sourcePort : sourcePorts) {
            List<NodePortIndex.CandidatePort> candidates = context.direction() == RecommendationDirection.DOWNSTREAM
                    ? portIndex.findDownstreamCandidates(sourcePort.dataType())
                    : portIndex.findUpstreamCandidates(sourcePort.dataType());

            String rulePortKey = resolveRulePortKey(sourceNode, sourcePort.portId(), context.direction());

            boolean listSelectionGate = isSelectionOrContext(context)
                    && sourcePort.dataType() == NodeDataType.LIST;

            boolean selectionOrContext = isSelectionOrContext(context);

            for (NodePortIndex.CandidatePort candidate : candidates) {
                if (shouldExclude(candidate.nodeId(), candidate.categoryId())) {
                    continue;
                }

                NodeEffect candidateEffect = resolveEffect(candidate.nodeId());
                if (candidateEffect == NodeEffect.UI_EFFECT) {
                    continue;
                }

                boolean hasExact = hasExactSourceNodeTarget(
                        sourceNode.getTypeId(),
                        rulePortKey,
                        candidate.nodeId(),
                        context.direction());

                // Selection: export/fileio categories stay out unless exact rule (Port Drag unaffected).
                if (selectionOrContext
                        && !hasExact
                        && isSelectionExcludedCategory(candidate.categoryId())) {
                    continue;
                }

                if (listSelectionGate
                        && !isAllowedListSelectionCandidate(
                                sourceNode.getTypeId(),
                                rulePortKey,
                                candidate.nodeId(),
                                context.direction())) {
                    continue;
                }

                String preferredPortId = resolvePreferredPortId(
                        sourceNode.getTypeId(),
                        rulePortKey,
                        sourcePort.dataType(),
                        candidate.nodeId(),
                        context.direction());

                // Skip same-type candidates unless an exact rule targets that type
                // (e.g. Column.output_top → Column.input_base stacking).
                if (candidate.nodeId().equalsIgnoreCase(sourceNode.getTypeId()) && !hasExact) {
                    continue;
                }

                // Selection hard-gate: WORLD_WRITE / CONTEXT_WRITE / FILE_IO / PREVIEW_WRITE
                // require exact sourceNodes targets. Port Drag allows type-compatible effects through.
                if (selectionOrContext
                        && isSelectionEffectGated(candidateEffect)
                        && !hasExact) {
                    continue;
                }

                NodePortIndex.CandidatePort resolvedCandidate = connector.pickBestCandidatePort(
                        context.direction(),
                        sourcePort.dataType(),
                        candidate.nodeId(),
                        preferredPortId);
                if (resolvedCandidate == null) {
                    continue;
                }

                raw.add(scorer.scoreCandidate(
                        context.direction(),
                        sourceNode.getTypeId(),
                        sourceCategory,
                        sourcePort.portId(),
                        rulePortKey,
                        sourcePort.dataType(),
                        resolvedCandidate,
                        preferredPortId));
            }
        }

        Map<String, NodeRecommendation> deduped = NodeRecommendationScorer.dedupeKeepBest(raw);
        return NodeRecommendationScorer.mergeAndSort(deduped, Math.max(1, limit));
    }

    @Override
    public List<NodeRecommendation> recommendForSelectedNode(NodeGraph graph, INode selectedNode, int limit) {
        if (selectedNode == null) {
            return List.of();
        }
        NodeRecommendationContext context = NodeRecommendationContext.forSelectedNode(
                selectedNode.getId(),
                (float) selectedNode.getPositionX(),
                (float) selectedNode.getPositionY(),
                limit);
        return recommend(graph, context);
    }

    @Override
    public NodeRecommendationApplyResult apply(
            ICanvasEditor editor,
            NodeGraph graph,
            NodeRecommendationContext context,
            NodeRecommendation recommendation) {
        ensureInitialized();
        return connector.apply(editor, graph, context, recommendation);
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private List<SourcePortContext> resolveSourcePorts(INode sourceNode, NodeRecommendationContext context) {
        List<SourcePortContext> ports = new ArrayList<>();

        if (context.sourcePortId() != null && context.sourceDataType() != null) {
            ports.add(new SourcePortContext(context.sourcePortId(), context.sourceDataType()));
            return ports;
        }

        if (context.sourcePortId() != null) {
            NodeDataType type = NodeRecommendationPorts.resolvePortDataType(
                    sourceNode,
                    context.sourcePortId(),
                    context.direction() == RecommendationDirection.DOWNSTREAM);
            if (type != null) {
                ports.add(new SourcePortContext(context.sourcePortId(), type));
                return ports;
            }
        }

        List<IPort> nodePorts = context.direction() == RecommendationDirection.DOWNSTREAM
                ? sourceNode.getOutputPorts()
                : sourceNode.getInputPorts();
        if (isSelectionOrContext(context)) {
            collectPortsForSelection(sourceNode.getTypeId(), nodePorts, ports, context.direction());
        } else {
            collectPorts(nodePorts, ports, false);
        }

        if (ports.isEmpty() && context.sourceDataType() != null) {
            ports.add(new SourcePortContext(null, context.sourceDataType()));
        }
        return ports;
    }

    private static boolean isSelectionOrContext(NodeRecommendationContext context) {
        return context.trigger() == RecommendationTrigger.SELECTION_PANEL
                || context.trigger() == RecommendationTrigger.NODE_CONTEXT_MENU;
    }

    private static void collectPorts(List<IPort> nodePorts, List<SourcePortContext> ports, boolean collapseByType) {
        if (collapseByType) {
            Map<NodeDataType, String> uniqueByType = new LinkedHashMap<>();
            for (IPort port : nodePorts) {
                if (port.getDataType() == NodeDataType.EXEC) {
                    continue;
                }
                uniqueByType.putIfAbsent(port.getDataType(), port.getId());
            }
            for (Map.Entry<NodeDataType, String> entry : uniqueByType.entrySet()) {
                ports.add(new SourcePortContext(entry.getValue(), entry.getKey()));
            }
            return;
        }

        for (IPort port : nodePorts) {
            if (port.getDataType() == NodeDataType.EXEC) {
                continue;
            }
            ports.add(new SourcePortContext(port.getId(), port.getDataType()));
        }
    }

    /**
     * Selection/context: keep exact-rule ports first (cap {@link #MAX_EXACT_RULE_PORTS}),
     * then one representative per remaining data type.
     */
    private void collectPortsForSelection(
            String sourceNodeTypeId,
            List<IPort> nodePorts,
            List<SourcePortContext> ports,
            RecommendationDirection direction) {
        List<SourcePortContext> exactRulePorts = new ArrayList<>();
        List<IPort> unruled = new ArrayList<>();

        for (IPort port : nodePorts) {
            if (port == null || port.getDataType() == NodeDataType.EXEC) {
                continue;
            }
            if (hasSourceNodePortRule(sourceNodeTypeId, port.getId(), direction)) {
                exactRulePorts.add(new SourcePortContext(port.getId(), port.getDataType()));
            } else {
                unruled.add(port);
            }
        }

        if (exactRulePorts.size() > MAX_EXACT_RULE_PORTS) {
            exactRulePorts = new ArrayList<>(exactRulePorts.subList(0, MAX_EXACT_RULE_PORTS));
        }
        ports.addAll(exactRulePorts);

        java.util.Set<NodeDataType> coveredTypes = new java.util.HashSet<>();
        for (SourcePortContext exact : exactRulePorts) {
            coveredTypes.add(exact.dataType());
        }

        Map<NodeDataType, String> uniqueByType = new LinkedHashMap<>();
        for (IPort port : unruled) {
            if (coveredTypes.contains(port.getDataType())) {
                continue;
            }
            uniqueByType.putIfAbsent(port.getDataType(), port.getId());
        }
        for (Map.Entry<NodeDataType, String> entry : uniqueByType.entrySet()) {
            ports.add(new SourcePortContext(entry.getValue(), entry.getKey()));
        }
    }

    /** True when sourceNodes declares rules for this physical port (or synthetic keys like {@code output_face:*}). */
    private boolean hasSourceNodePortRule(
            String sourceNodeTypeId,
            String portId,
            RecommendationDirection direction) {
        if (sourceNodeTypeId == null || portId == null || rules.sourceNodes == null) {
            return false;
        }
        NodeRecommendationRules.SourceNodeRule sourceRule =
                rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
        if (sourceRule == null) {
            return false;
        }
        Map<String, NodeRecommendationRules.PortDirectionRule> portMap =
                direction == RecommendationDirection.DOWNSTREAM
                        ? sourceRule.outputs
                        : sourceRule.inputs;
        if (portMap == null || portMap.isEmpty()) {
            return false;
        }
        if (portMap.containsKey(portId)) {
            return true;
        }
        String prefix = portId + ":";
        for (String key : portMap.keySet()) {
            if (key != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selection LIST recommendations: only high-confidence list ops from {@code outputTypes.list}
     * or exact sourceNodes targets.
     */
    private boolean isAllowedListSelectionCandidate(
            String sourceNodeTypeId,
            String rulePortKey,
            String candidateNodeId,
            RecommendationDirection direction) {
        if (hasExactSourceNodeTarget(sourceNodeTypeId, rulePortKey, candidateNodeId, direction)) {
            return true;
        }
        if (rules.outputTypes == null) {
            return false;
        }
        NodeRecommendationRules.OutputTypeRule listRule = rules.outputTypes.get("list");
        if (listRule == null) {
            return false;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                direction == RecommendationDirection.DOWNSTREAM ? listRule.downstream : listRule.upstream;
        if (entries == null) {
            return false;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry != null && entry.nodeId != null && entry.nodeId.equalsIgnoreCase(candidateNodeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps Get Box Face's physical {@code output_face} port to a synthetic rule key
     * ({@code output_face:horizontal} / {@code output_face:vertical}) based on the
     * resolved semantic face name.
     */
    private String resolveRulePortKey(INode sourceNode, String portId, RecommendationDirection direction) {
        if (direction != RecommendationDirection.DOWNSTREAM
                || sourceNode == null
                || !GET_BOX_FACE_TYPE_ID.equalsIgnoreCase(sourceNode.getTypeId())
                || !OUTPUT_FACE_PORT_ID.equals(portId)) {
            return portId;
        }
        String orientation = resolveGetBoxFaceOrientation(sourceNode);
        if (orientation == null) {
            return portId;
        }
        return OUTPUT_FACE_PORT_ID + ":" + orientation;
    }

    /**
     * Face orientation for Get Box Face recommendations.
     *
     * <p>Priority when Face Name is driven dynamically:
     * <ol>
     *   <li>resolved local input value</li>
     *   <li>upstream connected port / node output value (without forcing re-eval)</li>
     *   <li>last resolved {@code output_name} from a prior process</li>
     *   <li>{@code defaultFaceName} property only as last resort</li>
     * </ol>
     * Preferring {@code output_name} over {@code defaultFaceName} avoids mis-classifying a
     * connected-but-not-yet-refreshed face as the property default (e.g. front→horizontal via top).
     */
    private static String resolveGetBoxFaceOrientation(INode sourceNode) {
        String faceName = null;
        boolean faceNameConnected = OptionalPortDrive.isConnected(sourceNode, INPUT_FACE_NAME_PORT_ID);

        if (faceNameConnected) {
            Object connected = sourceNode.getInput(INPUT_FACE_NAME_PORT_ID);
            if (connected instanceof String text && !text.isBlank()) {
                faceName = text;
            }
            if (faceName == null) {
                faceName = readUpstreamFaceName(sourceNode);
            }
        }

        if (faceName == null) {
            Object resolved = sourceNode.getOutput(OUTPUT_NAME_PORT_ID);
            if (resolved instanceof String text && !text.isBlank()) {
                faceName = text;
            }
        }

        if (faceName == null && sourceNode instanceof GetBoxFaceNode getBoxFace) {
            String defaults = getBoxFace.getDefaultFaceName();
            if (defaults != null && !defaults.isBlank()) {
                faceName = defaults;
            }
        }

        String normalized = normalizeFaceName(faceName);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "top", "bottom" -> "horizontal";
            case "front", "back", "left", "right" -> "vertical";
            default -> null;
        };
    }

    /** Best-effort read of a live string on the Face Name upstream without re-processing the graph. */
    private static String readUpstreamFaceName(INode sourceNode) {
        IPort inputPort = sourceNode.getInputPorts().stream()
                .filter(port -> INPUT_FACE_NAME_PORT_ID.equals(port.getId()))
                .findFirst()
                .orElse(null);
        if (!(inputPort instanceof BasePort basePort)) {
            return null;
        }
        for (IPort upstream : basePort.getConnectedPorts()) {
            if (upstream == null) {
                continue;
            }
            Object portValue = upstream.getValue();
            if (portValue instanceof String text && !text.isBlank()) {
                return text;
            }
            INode upstreamNode = upstream.getNode();
            if (upstreamNode != null) {
                Object nodeOutput = upstreamNode.getOutput(upstream.getId());
                if (nodeOutput instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String normalizeFaceName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
        return switch (normalized) {
            case "top", "up", "upper" -> "top";
            case "bottom", "down", "lower" -> "bottom";
            case "left", "west" -> "left";
            case "right", "east" -> "right";
            case "front", "south" -> "front";
            case "back", "north", "rear" -> "back";
            default -> null;
        };
    }

    /** True when sourceNodes rules explicitly list {@code candidateNodeId} for this port/direction. */
    private boolean hasExactSourceNodeTarget(
            String sourceNodeTypeId,
            String sourcePortId,
            String candidateNodeId,
            RecommendationDirection direction) {
        if (sourceNodeTypeId == null || sourcePortId == null || candidateNodeId == null
                || rules.sourceNodes == null) {
            return false;
        }
        NodeRecommendationRules.SourceNodeRule sourceRule =
                rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
        if (sourceRule == null) {
            return false;
        }
        Map<String, NodeRecommendationRules.PortDirectionRule> portMap =
                direction == RecommendationDirection.DOWNSTREAM
                        ? sourceRule.outputs
                        : sourceRule.inputs;
        if (portMap == null) {
            return false;
        }
        NodeRecommendationRules.PortDirectionRule portRule = portMap.get(sourcePortId);
        if (portRule == null) {
            return false;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                direction == RecommendationDirection.DOWNSTREAM ? portRule.downstream : portRule.upstream;
        if (entries == null) {
            return false;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry != null && entry.nodeId != null && entry.nodeId.equalsIgnoreCase(candidateNodeId)) {
                return true;
            }
        }
        return false;
    }

    private String resolvePreferredPortId(
            String sourceNodeTypeId,
            String sourcePortId,
            NodeDataType sourceDataType,
            String candidateNodeId,
            RecommendationDirection direction) {
        String dirKey = direction == RecommendationDirection.DOWNSTREAM ? "downstream" : "upstream";

        if (sourceNodeTypeId != null && rules.sourceNodes != null) {
            NodeRecommendationRules.SourceNodeRule sourceRule =
                    rules.sourceNodes.get(sourceNodeTypeId.toLowerCase(Locale.ROOT));
            if (sourceRule != null && sourcePortId != null) {
                Map<String, NodeRecommendationRules.PortDirectionRule> portMap =
                        direction == RecommendationDirection.DOWNSTREAM
                                ? sourceRule.outputs
                                : sourceRule.inputs;
                if (portMap != null) {
                    NodeRecommendationRules.PortDirectionRule portRule = portMap.get(sourcePortId);
                    String fromRule = findConnectPortId(portRule, dirKey, candidateNodeId);
                    if (fromRule != null) {
                        return fromRule;
                    }
                }
            }
        }

        if (sourceDataType != null && rules.outputTypes != null) {
            NodeRecommendationRules.OutputTypeRule typeRule = rules.outputTypes.get(sourceDataType.getId());
            if (typeRule != null) {
                List<NodeRecommendationRules.RuleEntry> entries =
                        direction == RecommendationDirection.DOWNSTREAM ? typeRule.downstream : typeRule.upstream;
                return findConnectPortIdInEntries(entries, candidateNodeId);
            }
        }
        return null;
    }

    private String findConnectPortIdInEntries(List<NodeRecommendationRules.RuleEntry> entries, String candidateNodeId) {
        if (entries == null || candidateNodeId == null) {
            return null;
        }
        for (NodeRecommendationRules.RuleEntry entry : entries) {
            if (entry != null
                    && entry.nodeId != null
                    && entry.nodeId.equalsIgnoreCase(candidateNodeId)
                    && entry.connectPortId != null
                    && !entry.connectPortId.isBlank()) {
                return entry.connectPortId;
            }
        }
        return null;
    }

    private String findConnectPortId(
            NodeRecommendationRules.PortDirectionRule portRule,
            String dirKey,
            String candidateNodeId) {
        if (portRule == null || candidateNodeId == null) {
            return null;
        }
        List<NodeRecommendationRules.RuleEntry> entries =
                "downstream".equals(dirKey) ? portRule.downstream : portRule.upstream;
        return findConnectPortIdInEntries(entries, candidateNodeId);
    }

    private boolean shouldExclude(String nodeId, String categoryId) {
        if (nodeId == null) {
            return true;
        }
        for (String excluded : rules.defaults.excludeNodeIds) {
            if (nodeId.equalsIgnoreCase(excluded)) {
                return true;
            }
        }
        if (categoryId == null) {
            return false;
        }
        String lowerCategory = categoryId.toLowerCase(Locale.ROOT);
        for (String excludedCategory : rules.defaults.excludeCategories) {
            String lowerExcluded = excludedCategory.toLowerCase(Locale.ROOT);
            if (lowerCategory.equals(lowerExcluded) || lowerCategory.startsWith(lowerExcluded + ".")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSelectionExcludedCategory(String categoryId) {
        if (categoryId == null || rules.defaults.selectionExcludeCategories == null) {
            return false;
        }
        String lowerCategory = categoryId.toLowerCase(Locale.ROOT);
        for (String excludedCategory : rules.defaults.selectionExcludeCategories) {
            if (excludedCategory == null) {
                continue;
            }
            String lowerExcluded = excludedCategory.toLowerCase(Locale.ROOT);
            if (lowerCategory.equals(lowerExcluded) || lowerCategory.startsWith(lowerExcluded + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selection / context: side-effect candidates only via exact {@code sourceNodes} rules.
     * Port Drag does not use this hard gate.
     */
    private static boolean isSelectionEffectGated(NodeEffect effect) {
        return effect == NodeEffect.WORLD_WRITE
                || effect == NodeEffect.CONTEXT_WRITE
                || effect == NodeEffect.FILE_IO
                || effect == NodeEffect.PREVIEW_WRITE;
    }

    /**
     * Selection / context-menu recommendations hide for preview and world-write sinks
     * (status outputs are not useful modeling continuations). Port-drag remains open.
     */
    private boolean isSelectionLevelSink(NodeRecommendationContext context, INode sourceNode) {
        if (context.trigger() != RecommendationTrigger.SELECTION_PANEL
                && context.trigger() != RecommendationTrigger.NODE_CONTEXT_MENU) {
            return false;
        }
        String typeId = sourceNode.getTypeId();
        if (typeId != null && typeId.equalsIgnoreCase("output.execute.apply_changes")) {
            return true;
        }
        NodeEffect effect = resolveEffect(typeId);
        return effect == NodeEffect.PREVIEW_WRITE || effect == NodeEffect.WORLD_WRITE;
    }

    private static NodeEffect resolveEffect(String typeId) {
        if (typeId == null) {
            return NodeEffect.PURE;
        }
        NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
        Class<? extends INode> nodeClass = info != null ? info.getNodeClass() : null;
        return NodeEffectResolver.resolve(nodeClass, typeId);
    }

    private record SourcePortContext(String portId, NodeDataType dataType) {
    }
}
