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
        assertEquals("math.compare.compare",
            registry.resolveCanonicalNodeId("control.flow.compare"));
        assertEquals("math.logic.if",
            registry.resolveCanonicalNodeId("control.flow.branch"));
        assertEquals("material.gradient_mapping.height_gradient_map",
            registry.resolveCanonicalNodeId("world.modification.material_mapper"));
        assertEquals("material.gradient_mapping.height_gradient_map",
            registry.resolveCanonicalNodeId("material.basic_assignment.replace_material"));
        assertEquals("math.list_sequence.range",
            registry.resolveCanonicalNodeId("data.sequence.range"));
        assertEquals("math.random.random_number",
            registry.resolveCanonicalNodeId("math.randomness.random_number"));
        assertEquals("reference.vectors.vector",
            registry.resolveCanonicalNodeId("math.vector.construct"));
        assertEquals("pattern.linear.linear_array",
            registry.resolveCanonicalNodeId("spatial.arrays.linear_array"));
        assertEquals("geometry.solids.deconstruct_surface_strip",
            registry.resolveCanonicalNodeId("spatial.analysis.deconstruct_surface_strip"));
        assertEquals("world.write.set_block",
            registry.resolveCanonicalNodeId("world.modification.set_block"));
    }

    @Test
    void registryInitializationResolvesLegacyIdsToCanonicalMetadataAndInstances() {
        registry.clear();
        registry.initialize();

        NodeInfo previewInfo = registry.getNodeInfo("visualization.preview.geometry_viewer");
        assertNotNull(previewInfo);
        assertEquals("output.preview.geometry_viewer", previewInfo.getId());
        assertEquals("output.preview", previewInfo.getCategoryId());

        NodeInfo ifInfo = registry.getNodeInfo("control.flow.branch");
        assertNotNull(ifInfo);
        assertEquals("math.logic.if", ifInfo.getId());
        assertEquals("math.logic", ifInfo.getCategoryId());

        NodeInfo materialInfo = registry.getNodeInfo("world.modification.material_mapper");
        assertNotNull(materialInfo);
        assertEquals("material.gradient_mapping.height_gradient_map", materialInfo.getId());
        assertEquals("material.gradient_mapping", materialInfo.getCategoryId());

        NodeInfo surfaceStripInfo = registry.getNodeInfo("spatial.analysis.deconstruct_surface_strip");
        assertNotNull(surfaceStripInfo);
        assertEquals("geometry.solids.deconstruct_surface_strip", surfaceStripInfo.getId());
        assertEquals("geometry.solids", surfaceStripInfo.getCategoryId());

        INode node = registry.createNodeInstance("visualization.debugging.panel");
        assertNotNull(node);
        assertTrue(node.getClass().getName().endsWith(".PanelNode"));

        INode surfaceStripNode = registry.createNodeInstance("spatial.analysis.deconstruct_surface_strip");
        assertNotNull(surfaceStripNode);
        assertTrue(surfaceStripNode.getClass().getName().endsWith(".DeconstructSurfaceStripNode"));
    }
}
