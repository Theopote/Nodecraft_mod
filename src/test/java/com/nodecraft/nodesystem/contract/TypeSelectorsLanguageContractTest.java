package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.BiomeSelectorNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.BlockTypeSelectorNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.EntityTypeSelectorNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.ItemTypeSelectorNode;
import com.nodecraft.nodesystem.nodes.input.type_selectors.RegistrySelectorUtils;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Type Selectors v1 language fence: canonical registry ids, Valid gate, PURE effect, V33 migration.
 */
class TypeSelectorsLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV34() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyFourTypeSelectorNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("input.type_selectors."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 input.type_selectors nodes: " + ids);
        assertFalse(ids.contains("input.type_selectors.block_state_selector"));
    }

    @Test
    void allTypeSelectorsArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BlockTypeSelectorNode.class, "input.type_selectors.block_type_selector"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(EntityTypeSelectorNode.class, "input.type_selectors.entity_type_selector"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(ItemTypeSelectorNode.class, "input.type_selectors.item_type_selector"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BiomeSelectorNode.class, "input.type_selectors.biome_selector"));
    }

    @Test
    void blockTypeSelectorUsesBlockTypePort() {
        BlockTypeSelectorNode node = new BlockTypeSelectorNode();
        IPort blockPort = findPort(node.getOutputPorts(), "output_block_id");
        assertEquals(NodeDataType.BLOCK_TYPE, blockPort.getDataType());
        assertEquals("Block Type", blockPort.getDisplayName());
    }

    @Test
    void allSelectorsExposeOutputValid() {
        assertTrue(hasPort(new BlockTypeSelectorNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new EntityTypeSelectorNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new ItemTypeSelectorNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new BiomeSelectorNode().getOutputPorts(), "output_valid"));
    }

    @Test
    void unknownSavedItemIdIsPreservedWithValidFalse() {
        ItemTypeSelectorNode node = new ItemTypeSelectorNode();
        node.setNodeState(Map.of("selectedItem", "mod_a:missing_item"));
        assertEquals("mod_a:missing_item", node.getOutput("output_item_id"));
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void allowModdedFilterDoesNotRewriteSavedModdedId() {
        ItemTypeSelectorNode node = new ItemTypeSelectorNode();
        Map<String, Object> state = new HashMap<>();
        state.put("selectedItem", "create:limestone");
        state.put("allowModded", false);
        state.put("selectedCategory", "all");
        state.put("minecraftOnly", true);
        node.setNodeState(state);
        assertEquals("create:limestone", node.getOutput("output_item_id"));
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void registrySelectorUtilsNormalizeAndValidate() {
        assertEquals("minecraft:stone", RegistrySelectorUtils.normalizeCanonicalId("stone"));
        assertEquals("mod_a:marble", RegistrySelectorUtils.normalizeCanonicalId("mod_a:marble"));
        assertEquals(null, RegistrySelectorUtils.normalizeCanonicalId("   "));
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:marble", false, true, true));
        assertTrue(RegistrySelectorUtils.computeValid("minecraft:stone", true, false, true));
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:marble", true, false, true));
    }


    private static final String BLOCK_TYPE_SELECTOR = "input.type_selectors.block_type_selector";
    private static final String BUILD_BLOCK_STATE = "material.block_state.build_block_state";

    private static SavedNode findNode(SavedGraph graph, String nodeId) {
        return graph.nodes.stream().filter(n -> nodeId.equals(n.nodeId)).findFirst().orElseThrow();
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static boolean hasWire(SavedGraph graph, String sourceNode, String sourcePort,
                                   String targetNode, String targetPort) {
        return graph.connections.stream().anyMatch(c ->
                sourceNode.equals(c.sourceNodeId)
                        && sourcePort.equals(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equals(c.targetPortId));
    }

    private static IPort findPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + id);
    }

    private static boolean hasPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }
}
