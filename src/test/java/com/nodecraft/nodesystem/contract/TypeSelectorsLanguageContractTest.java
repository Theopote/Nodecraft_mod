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
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V56, GraphFormatVersion.CURRENT);
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
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:marble", false, true));
        assertTrue(RegistrySelectorUtils.computeValid("minecraft:stone", true, false));
        assertFalse(RegistrySelectorUtils.computeValid("mod_a:marble", true, false));
    }

    @Test
    void v32ToV33RemapsBlockStateSelectorAndTightensBlockTypeWires() {
        SavedGraph v32 = new SavedGraph();
        v32.formatVersion = GraphFormatVersion.V32;

        SavedNode legacyState = savedNode("legacy", "input.type_selectors.block_state_selector");
        legacyState.state = Map.of(
                "blockId", "minecraft:oak_stairs",
                "stateProperties", "facing=north"
        );

        SavedNode simpleBlock = savedNode("block", "input.type_selectors.block_type_selector");
        SavedNode assign = savedNode("assign", "material.basic_assignment.assign_block_type");
        SavedNode build = savedNode("build", "material.block_state.build_block_state");

        v32.nodes = new ArrayList<>(List.of(legacyState, simpleBlock, assign, build));
        v32.connections = new ArrayList<>(List.of(
                wire("legacy", "output_block_state", "build", "input_base_state"),
                wire("block", "output_block_id", "assign", "input_block_type"),
                wire("block", "output_block_id", "build", "input_property_name")
        ));
        v32.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v32);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals(BLOCK_TYPE_SELECTOR, findNode(migrated, "legacy").typeId);
        assertFalse(hasWire(migrated, "legacy", "output_block_state", "build", "input_base_state"));
        assertTrue(hasWire(migrated, "block", "output_block_id", "assign", "input_block_type"));
        assertFalse(hasWire(migrated, "block", "output_block_id", "build", "input_property_name"));

        SavedNode insertedBuild = migrated.nodes.stream()
                .filter(n -> BUILD_BLOCK_STATE.equals(n.typeId) && !"build".equals(n.nodeId))
                .findFirst()
                .orElse(null);
        assertNotNull(insertedBuild);
        assertTrue(hasWire(migrated, "legacy", "output_block_id", insertedBuild.nodeId, "input_block_type"));
        assertTrue(hasWire(migrated, insertedBuild.nodeId, "output_block_state", "build", "input_base_state"));
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
