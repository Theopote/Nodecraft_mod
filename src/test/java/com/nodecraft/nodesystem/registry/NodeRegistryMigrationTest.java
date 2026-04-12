package com.nodecraft.nodesystem.registry;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryMigrationTest {

    private final NodeRegistry registry = NodeRegistry.getInstance();

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void resolveCanonicalNodeIdMapsRepresentativeLegacyIds() {
        assertEquals("output.preview.geometry_viewer",
            registry.resolveCanonicalNodeId("visualization.preview.geometry_viewer"));
        assertEquals("output.debug.data_inspector",
            registry.resolveCanonicalNodeId("visualization.debugging.panel"));
        assertEquals("input.numeric.integer",
            registry.resolveCanonicalNodeId("inputs.basic.integer_input"));
        assertEquals("input.type_selectors.block_type_selector",
            registry.resolveCanonicalNodeId("inputs.selectors.block_type_selector"));
        assertEquals("math.list_sequence.create_list",
            registry.resolveCanonicalNodeId("inputs.sources.create_list"));
        assertEquals("material.gradient_mapping.height_gradient_map",
            registry.resolveCanonicalNodeId("world.modification.material_mapper"));
        assertEquals("material.gradient_mapping.height_gradient_map",
            registry.resolveCanonicalNodeId("material.basic_assignment.replace_material"));
        assertEquals("math.random.random_number",
            registry.resolveCanonicalNodeId("math.randomness.random_number"));
        assertEquals("reference.vectors.vector",
            registry.resolveCanonicalNodeId("math.vector.construct"));
        assertEquals("world.write.set_block",
            registry.resolveCanonicalNodeId("world.modification.set_block"));
        assertEquals("math.logic.if",
            registry.resolveCanonicalNodeId("logic.if"));
    }

    @Test
    void registryInitializationResolvesLegacyIdsToCanonicalMetadataAndInstances() {
        registry.clear();
        registry.initialize();

        NodeInfo previewInfo = registry.getNodeInfo("visualization.preview.geometry_viewer");
        assertNotNull(previewInfo);
        assertEquals("output.preview.geometry_viewer", previewInfo.getId());
        assertEquals("output.preview", previewInfo.getCategoryId());

        NodeInfo ifInfo = registry.getNodeInfo("logic.if");
        assertNotNull(ifInfo);
        assertEquals("math.logic.if", ifInfo.getId());
        assertEquals("math.logic", ifInfo.getCategoryId());

        NodeInfo materialInfo = registry.getNodeInfo("world.modification.material_mapper");
        assertNotNull(materialInfo);
        assertEquals("material.gradient_mapping.height_gradient_map", materialInfo.getId());
        assertEquals("material.gradient_mapping", materialInfo.getCategoryId());

        INode node = registry.createNodeInstance("visualization.debugging.panel");
        assertNotNull(node);
        assertTrue(node.getClass().getName().endsWith(".PanelNode"));
    }
}
