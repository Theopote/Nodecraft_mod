package com.nodecraft.gui.recommendation;

import com.nodecraft.gui.ai.AiNodeSchemaCatalog;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog.NodeSchema;
import com.nodecraft.gui.ai.AiNodeSchemaCatalog.PortSchema;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NodePortIndex {

    private volatile List<NodeSchema> cachedSchemas = List.of();
    private volatile Map<NodeDataType, List<CandidatePort>> downstreamByOutputType = Map.of();
    private volatile Map<NodeDataType, List<CandidatePort>> upstreamByInputType = Map.of();
    private volatile long cachedEpoch = -1L;

    public List<NodeSchema> schemas() {
        ensureIndexesBuilt();
        return cachedSchemas;
    }

    public void invalidate() {
        synchronized (this) {
            cachedSchemas = List.of();
            downstreamByOutputType = Map.of();
            upstreamByInputType = Map.of();
            cachedEpoch = -1L;
        }
        AiNodeSchemaCatalog.invalidateCache();
    }

    public List<CandidatePort> findDownstreamCandidates(NodeDataType outputType) {
        if (outputType == null) {
            return List.of();
        }
        ensureIndexesBuilt();
        return downstreamByOutputType.getOrDefault(outputType, List.of());
    }

    public List<CandidatePort> findUpstreamCandidates(NodeDataType inputType) {
        if (inputType == null) {
            return List.of();
        }
        ensureIndexesBuilt();
        return upstreamByInputType.getOrDefault(inputType, List.of());
    }

    private void ensureIndexesBuilt() {
        NodeRegistry registry = NodeRegistry.getInstance();
        long epoch = registry.getIntrospectionEpoch();
        if (cachedEpoch == epoch && !cachedSchemas.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (cachedEpoch == epoch && !cachedSchemas.isEmpty()) {
                return;
            }
            cachedSchemas = AiNodeSchemaCatalog.collectAll(registry);
            rebuildCompatibilityIndexes();
            cachedEpoch = epoch;
        }
    }

    private void rebuildCompatibilityIndexes() {
        Map<NodeDataType, List<CandidatePort>> downstream = new EnumMap<>(NodeDataType.class);
        Map<NodeDataType, List<CandidatePort>> upstream = new EnumMap<>(NodeDataType.class);

        for (NodeSchema schema : cachedSchemas) {
            for (PortSchema input : schema.inputs()) {
                NodeDataType inputType = parseType(input.dataType());
                CandidatePort candidate = new CandidatePort(
                        schema.typeId(),
                        schema.displayName(),
                        schema.category(),
                        input.id(),
                        inputType,
                        input.required());
                for (NodeDataType outputType : NodeDataType.values()) {
                    if (NodeDataType.isConnectableTo(outputType, inputType)) {
                        downstream.computeIfAbsent(outputType, ignored -> new ArrayList<>()).add(candidate);
                    }
                }
            }

            for (PortSchema output : schema.outputs()) {
                NodeDataType outputType = parseType(output.dataType());
                CandidatePort candidate = new CandidatePort(
                        schema.typeId(),
                        schema.displayName(),
                        schema.category(),
                        output.id(),
                        outputType,
                        true);
                for (NodeDataType inputType : NodeDataType.values()) {
                    if (NodeDataType.isConnectableTo(outputType, inputType)) {
                        upstream.computeIfAbsent(inputType, ignored -> new ArrayList<>()).add(candidate);
                    }
                }
            }
        }

        downstreamByOutputType = freezeIndex(downstream);
        upstreamByInputType = freezeIndex(upstream);
    }

    private static Map<NodeDataType, List<CandidatePort>> freezeIndex(Map<NodeDataType, List<CandidatePort>> mutable) {
        Map<NodeDataType, List<CandidatePort>> frozen = new EnumMap<>(NodeDataType.class);
        for (Map.Entry<NodeDataType, List<CandidatePort>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    public CandidatePort findPort(String nodeId, String portId, RecommendationDirection direction) {
        if (nodeId == null || portId == null) {
            return null;
        }
        for (NodeSchema schema : schemas()) {
            if (!schema.typeId().equalsIgnoreCase(nodeId)) {
                continue;
            }
            if (direction == RecommendationDirection.DOWNSTREAM) {
                for (PortSchema input : schema.inputs()) {
                    if (input.id().equals(portId)) {
                        return new CandidatePort(
                                schema.typeId(),
                                schema.displayName(),
                                schema.category(),
                                input.id(),
                                parseType(input.dataType()),
                                input.required());
                    }
                }
            } else {
                for (PortSchema output : schema.outputs()) {
                    if (output.id().equals(portId)) {
                        return new CandidatePort(
                                schema.typeId(),
                                schema.displayName(),
                                schema.category(),
                                output.id(),
                                parseType(output.dataType()),
                                true);
                    }
                }
            }
        }
        return null;
    }

    public static NodeDataType parseType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return NodeDataType.ANY;
        }
        return NodeDataType.fromId(typeId.toLowerCase());
    }

    public record CandidatePort(
            String nodeId,
            String displayName,
            String categoryId,
            String portId,
            NodeDataType dataType,
            boolean required
    ) {
    }
}
