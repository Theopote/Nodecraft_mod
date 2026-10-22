package com.nodecraft.gui.editor.impl;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless contract for Phase 1 additional custom UI nodes.
 */
class CustomUIAdditionalNodesTest {

    private static final Set<String> ADDITIONAL_TYPE_IDS = Set.of(
        "math.scalar_math.graph_mapper",
        "math.data_tree.viewer",
        "material.basic_assignment.create_block_palette",
        "material.basic_assignment.weighted_palette",
        "material.block_state.build_block_state",
        "material.gradient_mapping.gradient_ramp_map"
    );

    @BeforeAll
    static void init() {
        System.setProperty("nodecraft.customui.headlessMetrics", "true");
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void additionalNodesImplementCustomUI() {
        for (String typeId : ADDITIONAL_TYPE_IDS) {
            INode node = NodeRegistry.getInstance().createNodeInstance(typeId);
            assertInstanceOf(ICustomUINode.class, node, typeId);
            ICustomUINode custom = (ICustomUINode) node;
            assertTrue(custom.hasCustomUI(), typeId);
            assertTrue(Float.isFinite(custom.getCustomUIHeight()), typeId + " height");
            assertTrue(Float.isFinite(custom.getMinRequiredUIWidth()), typeId + " width");
        }
    }

    @Test
    void additionalTypeIdsAreInAuditInventory() {
        for (String typeId : ADDITIONAL_TYPE_IDS) {
            assertTrue(CustomUINodeAuditTypeIds.ALL.contains(typeId), typeId);
        }
    }
}
