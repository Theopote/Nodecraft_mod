package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the effective {@link NodeEffect} for a node from its annotation and type id.
 */
public final class NodeEffectResolver {

    /**
     * Explicit overrides where type-id prefix heuristics would mis-classify behavior.
     */
    private static final Map<String, NodeEffect> EXPLICIT_OVERRIDES = Map.of(
            "output.execute.merge_block_placements", NodeEffect.PURE,
            "output.execute.clear_preview", NodeEffect.PREVIEW_WRITE,
            "world.write.peek_last_undo", NodeEffect.WORLD_READ,
            "output.execute.bake_status", NodeEffect.UI_EFFECT
    );

    private NodeEffectResolver() {
    }

    public static NodeEffect resolve(INode node) {
        if (node == null) {
            return NodeEffect.PURE;
        }
        NodeInfo annotation = node.getClass().getAnnotation(NodeInfo.class);
        if (annotation != null && annotation.effect() != NodeEffect.UNSPECIFIED) {
            return annotation.effect();
        }
        return inferFromTypeId(node.getTypeId());
    }

    public static NodeEffect resolve(Class<? extends INode> nodeClass, String typeId) {
        if (nodeClass != null) {
            NodeInfo annotation = nodeClass.getAnnotation(NodeInfo.class);
            if (annotation != null && annotation.effect() != NodeEffect.UNSPECIFIED) {
                return annotation.effect();
            }
        }
        return inferFromTypeId(typeId);
    }

    /**
     * Prefix-based inference for nodes without an explicit {@link NodeInfo#effect()} declaration.
     */
    public static NodeEffect inferFromTypeId(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return NodeEffect.PURE;
        }
        String normalized = typeId.toLowerCase(Locale.ROOT);
        NodeEffect override = EXPLICIT_OVERRIDES.get(normalized);
        if (override != null) {
            return override;
        }
        if (normalized.startsWith("world.write.")) {
            return NodeEffect.WORLD_WRITE;
        }
        if (normalized.startsWith("world.read.")
                || normalized.startsWith("world.query.")
                || normalized.startsWith("world.selection.")
                || normalized.startsWith("input.context.")) {
            return NodeEffect.WORLD_READ;
        }
        if (normalized.startsWith("output.preview.")) {
            return NodeEffect.PREVIEW_WRITE;
        }
        if (normalized.startsWith("output.execute.")) {
            return NodeEffect.WORLD_WRITE;
        }
        if (normalized.startsWith("utilities.fileio.") || normalized.startsWith("output.export.")) {
            return NodeEffect.FILE_IO;
        }
        if (normalized.startsWith("output.debug.")) {
            return NodeEffect.UI_EFFECT;
        }
        return NodeEffect.PURE;
    }
}
