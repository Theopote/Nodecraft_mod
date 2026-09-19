package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.execution.runtime.PreviewSideEffectPolicy;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog + plan contracts for preview side-effect enforcement.
 */
class PreviewSideEffectContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void previewPlanEnforcesEffectPolicy_manualDoesNot() {
        ExecutionPlan preview = ExecutionPlan.preview(null);
        ExecutionPlan manual = ExecutionPlan.manual(null);
        assertTrue(preview.skipOutputExecuteSideEffects());
        assertFalse(manual.skipOutputExecuteSideEffects());
        assertTrue(PreviewSideEffectPolicy.shouldSkipPermanentSideEffects(preview));
        assertFalse(PreviewSideEffectPolicy.shouldSkipPermanentSideEffects(manual));
    }

    @Test
    void everyCatalogNodeDeclaresExplicitNodeEffect() {
        List<String> missing = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                missing.add(nodeId + " (missing class)");
                continue;
            }
            com.nodecraft.nodesystem.api.NodeInfo annotation =
                    info.getNodeClass().getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
            if (annotation == null || annotation.effect() == NodeEffect.UNSPECIFIED) {
                missing.add(nodeId);
            }
        }
        assertTrue(missing.isEmpty(), "nodes missing explicit NodeEffect: " + missing);
    }

    @Test
    void declaredEffectMatchesResolverForAllCatalogNodes() {
        List<String> mismatches = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            com.nodecraft.nodesystem.api.NodeInfo annotation =
                    info.getNodeClass().getAnnotation(com.nodecraft.nodesystem.api.NodeInfo.class);
            if (annotation == null || annotation.effect() == NodeEffect.UNSPECIFIED) {
                continue;
            }
            try {
                INode instance = registry.createNodeInstance(nodeId);
                NodeEffect resolved = NodeEffectResolver.resolve(instance);
                if (resolved != annotation.effect()) {
                    mismatches.add(nodeId + " declared=" + annotation.effect() + " resolved=" + resolved);
                }
            } catch (Exception | LinkageError e) {
                NodeEffect resolved = NodeEffectResolver.resolve(info.getNodeClass(), nodeId);
                if (resolved != annotation.effect()) {
                    mismatches.add(nodeId + " declared=" + annotation.effect() + " resolved=" + resolved);
                }
            }
        }
        assertTrue(mismatches.isEmpty(), "NodeEffect mismatches: " + mismatches);
    }

    @Test
    void previewForbiddenEffectsAreSkippedByPolicy() {
        Set<NodeEffect> forbidden = EnumSet.of(
                NodeEffect.WORLD_WRITE,
                NodeEffect.FILE_IO,
                NodeEffect.NETWORK,
                NodeEffect.UI_EFFECT
        );
        ExecutionPlan preview = ExecutionPlan.preview(null);

        for (NodeEffect effect : forbidden) {
            assertFalse(effect.isAllowedInPreview(), effect + " must be forbidden in preview");
        }

        for (String nodeId : registry.getAllNodeIds()) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                continue;
            }
            NodeEffect effect;
            try {
                INode instance = registry.createNodeInstance(nodeId);
                effect = PreviewSideEffectPolicy.resolveEffect(instance);
            } catch (Exception | LinkageError e) {
                effect = NodeEffectResolver.resolve(info.getNodeClass(), nodeId);
            }
            if (forbidden.contains(effect)) {
                assertTrue(
                        PreviewSideEffectPolicy.shouldSkipNode(preview, createOrResolve(nodeId, info)),
                        nodeId + " (" + effect + ") must be skipped in preview"
                );
            }
        }
    }

    @Test
    void worldWriteNodesAreForbiddenInPreview() {
        List<String> worldWriteIds = registry.getAllNodeIds().stream()
                .filter(id -> id.startsWith("world.write."))
                .filter(id -> !"world.write.peek_last_undo".equals(id))
                .toList();
        assertTrue(worldWriteIds.size() >= 10, "expected world.write catalog entries");

        ExecutionPlan preview = ExecutionPlan.preview(null);
        for (String nodeId : worldWriteIds) {
            NodeInfo info = registry.getNodeInfo(nodeId);
            assertEquals(NodeEffect.WORLD_WRITE, NodeEffectResolver.resolve(info.getNodeClass(), nodeId), nodeId);
            INode node = createOrResolve(nodeId, info);
            assertTrue(NodeExecutor.isForbiddenInPreviewNode(node), nodeId);
            assertTrue(PreviewSideEffectPolicy.shouldSkipNode(preview, node), nodeId);
        }
    }

    @Test
    void previewAllowedEffectsIncludePureWorldReadAndPreviewWrite() {
        assertTrue(NodeEffect.PURE.isAllowedInPreview());
        assertTrue(NodeEffect.WORLD_READ.isAllowedInPreview());
        assertTrue(NodeEffect.PREVIEW_WRITE.isAllowedInPreview());
        assertFalse(NodeEffect.WORLD_WRITE.isAllowedInPreview());
        assertFalse(NodeEffectResolver.inferFromTypeId("world.write.clone_region").isAllowedInPreview());
        assertTrue(NodeEffectResolver.inferFromTypeId("output.preview.blocks").isAllowedInPreview());
        assertTrue(NodeEffectResolver.inferFromTypeId("world.read.get_block").isAllowedInPreview());
        assertEquals(NodeEffect.PURE, NodeEffectResolver.inferFromTypeId("output.execute.merge_block_placements"));
        assertEquals(NodeEffect.PREVIEW_WRITE, NodeEffectResolver.inferFromTypeId("output.execute.clear_preview"));
        assertEquals(NodeEffect.WORLD_READ, NodeEffectResolver.inferFromTypeId("world.write.peek_last_undo"));
    }

    private static INode createOrResolve(String nodeId, NodeInfo info) {
        try {
            return registry.createNodeInstance(nodeId);
        } catch (Exception | LinkageError e) {
            return new StubNode(nodeId);
        }
    }

    private static final class StubNode implements INode {
        private final String typeId;

        private StubNode(String typeId) {
            this.typeId = typeId;
        }

        @Override
        public java.util.UUID getId() {
            return java.util.UUID.randomUUID();
        }

        @Override
        public String getTypeId() {
            return typeId;
        }

        @Override
        public String getDisplayName() {
            return typeId;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public java.util.List<com.nodecraft.nodesystem.api.IPort> getInputPorts() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.nodecraft.nodesystem.api.IPort> getOutputPorts() {
            return java.util.List.of();
        }

        @Override
        public double getPositionX() {
            return 0;
        }

        @Override
        public double getPositionY() {
            return 0;
        }

        @Override
        public void setPosition(double x, double y) {
        }

        @Override
        public java.util.Map<String, Object> compute(java.util.Map<String, Object> inputs) {
            return java.util.Map.of();
        }

        @Override
        public Object getOutput(String portId) {
            return null;
        }

        @Override
        public void setInput(String portId, Object value) {
        }

        @Override
        public Object getNodeState() {
            return null;
        }

        @Override
        public void setNodeState(Object state) {
        }
    }
}
