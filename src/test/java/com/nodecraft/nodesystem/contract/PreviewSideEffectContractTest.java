package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.PreviewSideEffectPolicy;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog + plan contracts for preview permanent side-effect skipping.
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
    void previewPlanSkipsOutputExecute_manualDoesNot() {
        ExecutionPlan preview = ExecutionPlan.preview(null);
        ExecutionPlan manual = ExecutionPlan.manual(null);
        assertTrue(preview.skipOutputExecuteSideEffects());
        assertFalse(manual.skipOutputExecuteSideEffects());
        assertTrue(PreviewSideEffectPolicy.shouldSkipPermanentSideEffects(preview));
        assertFalse(PreviewSideEffectPolicy.shouldSkipPermanentSideEffects(manual));
    }

    @Test
    void everyOutputExecuteCatalogNodeIsClassifiedAsPermanentSideEffect() {
        List<String> unclassified = new ArrayList<>();
        List<String> classified = new ArrayList<>();

        for (String nodeId : registry.getAllNodeIds()) {
            if (!PreviewSideEffectPolicy.isPermanentSideEffectTypeId(nodeId)) {
                continue;
            }
            NodeInfo info = registry.getNodeInfo(nodeId);
            if (info == null || info.getNodeClass() == null) {
                unclassified.add(nodeId + " (missing class)");
                continue;
            }
            try {
                INode instance = registry.createNodeInstance(nodeId);
                if (!PreviewSideEffectPolicy.isPermanentSideEffectNode(instance)) {
                    unclassified.add(nodeId + " (runtime typeId=" + instance.getTypeId() + ")");
                } else {
                    classified.add(nodeId);
                }
            } catch (Exception | LinkageError e) {
                // Fall back to registry id classification when ctor needs Minecraft.
                if (!PreviewSideEffectPolicy.isPermanentSideEffectTypeId(nodeId)) {
                    unclassified.add(nodeId + " (instantiate failed)");
                } else {
                    classified.add(nodeId);
                }
            }
        }

        assertTrue(classified.size() >= 5,
                "expected several output.execute.* nodes in catalog, found " + classified.size());
        assertTrue(unclassified.isEmpty(), "unclassified permanent side-effect nodes: " + unclassified);
    }

    @Test
    void previewPolicySkipsOnlyPermanentPrefix() {
        assertTrue(PreviewSideEffectPolicy.isPermanentSideEffectTypeId("output.execute.apply_changes"));
        assertFalse(PreviewSideEffectPolicy.isPermanentSideEffectTypeId("output.preview.blocks"));
        assertFalse(PreviewSideEffectPolicy.isPermanentSideEffectTypeId("world.write.clone_region"));
        assertFalse(PreviewSideEffectPolicy.isPermanentSideEffectTypeId(null));
    }
}
