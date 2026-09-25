package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DataSeriesNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DispatchListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.GroupListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ListStatisticsNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MapListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MathRangeNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.RepeatNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes List / Collection v1: typed scalar lists, numeric producers, Create/Repeat semantics,
 * Group → DATA_TREE, and V21→V22 structure-node deletion.
 */
class ListLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void doubleAndBooleanListKindsExist() {
        assertEquals(ListElementKind.DOUBLE, NodeDataType.DOUBLE_LIST.getListElementKind());
        assertEquals(ListElementKind.BOOLEAN, NodeDataType.BOOLEAN_LIST.getListElementKind());
        assertTrue(NodeDataType.DOUBLE_LIST.isListType());
        assertTrue(NodeDataType.BOOLEAN_LIST.isListType());
    }

    @Test
    void numberSequenceAndSeriesEmitDoubleList() {
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(new MathRangeNode(), "output_numbers").getDataType());
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(new DataSeriesNode(), "output_series").getDataType());
        assertEquals("Number Series", new DataSeriesNode().getDisplayName());
    }

    @Test
    void listStatisticsAndMapNumbersAreStrictDoubleList() {
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(new ListStatisticsNode(), "input_list").getDataType());
        MapListNode map = new MapListNode();
        assertEquals("Map Numbers", map.getDisplayName());
        assertEquals("math.list.map_numbers", map.getTypeId());
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(map, "input_list").getDataType());
        assertEquals(NodeDataType.DOUBLE_LIST, findPort(map, "output_list").getDataType());
        assertFalse(hasPort(map, "output_changed_count"));
        assertTrue(hasPort(map, "output_count"));
    }

    @Test
    void dispatchListUsesBooleanListMaskWithoutScalarBroadcastPort() {
        DispatchListNode node = new DispatchListNode();
        assertEquals(NodeDataType.BOOLEAN_LIST, findPort(node, "input_condition").getDataType());
        assertEquals(NodeDataType.LIST, findPort(node, "input_list").getDataType());
        assertEquals(NodeDataType.LIST, findPort(node, "output_true").getDataType());
        assertEquals(NodeDataType.LIST, findPort(node, "output_false").getDataType());
    }

    @Test
    void createListIsAnyOnlyHeterogeneousBuilder() {
        CreateListNode node = new CreateListNode();
        assertEquals(NodeDataType.LIST, findPort(node, "output_list").getDataType());
        assertEquals(NodeDataType.ANY, findPort(node, "input_0").getDataType());
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) node.getNodeState();
        assertFalse(state.containsKey("allowDifferentTypes"));
    }

    @Test
    void repeatItemNeverTilesLists() {
        RepeatNode node = new RepeatNode();
        assertEquals("Repeat Item", node.getDisplayName());
        assertEquals(NodeDataType.ANY, findPort(node, "input_data").getDataType());
        assertEquals(NodeDataType.LIST, findPort(node, "output_result").getDataType());

        node.setInput("input_data", List.of("a", "b"));
        node.setInput("input_count", 3);
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) node.getOutput("output_result");
        assertEquals(3, result.size());
        assertEquals(List.of("a", "b"), result.getFirst());
        assertEquals(List.of("a", "b"), result.get(1));
        assertEquals(List.of("a", "b"), result.get(2));
    }

    @Test
    void groupListEmitsDataTreeNotNestedList() {
        GroupListNode node = new GroupListNode();
        assertEquals(NodeDataType.DATA_TREE, findPort(node, "output_tree").getDataType());
        assertFalse(hasPort(node, "output_groups"));
        assertEquals(NodeDataType.LIST, findPort(node, "output_unique_keys").getDataType());
    }

    @Test
    void deletedStructureNodesAreUnregistered() {
        NodeRegistry registry = NodeRegistry.getInstance();
        assertEquals(null, registry.getNodeInfo("math.list.chunk"));
        assertEquals(null, registry.getNodeInfo("math.list.combine_lists"));
        assertEquals(null, registry.getNodeInfo("math.list.zip"));
        assertEquals(null, registry.getNodeInfo("math.list.transpose"));
        assertNotNull(registry.getNodeInfo("math.list.map_numbers"));
        assertEquals(null, registry.getNodeInfo("math.list.map_list"));
    }

    @Test
    void v21ToV22MigrationDeletesStructureNodesAndRemapsMapList() {
        SavedGraph v21 = new SavedGraph();
        v21.formatVersion = GraphFormatVersion.V21;

        SavedNode chunk = new SavedNode();
        chunk.nodeId = "chunk";
        chunk.typeId = "math.list.chunk";

        SavedNode map = new SavedNode();
        map.nodeId = "map";
        map.typeId = "math.list.map_list";
        map.state = Map.of("operation", "ADD", "ignoreNonNumeric", true);

        SavedNode group = new SavedNode();
        group.nodeId = "group";
        group.typeId = "math.list.group_list";

        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "math.list.list_length";

        v21.nodes = new ArrayList<>(List.of(chunk, map, group, sink));

        SavedConnection legacyGroups = new SavedConnection();
        legacyGroups.sourceNodeId = "group";
        legacyGroups.sourcePortId = "output_groups";
        legacyGroups.targetNodeId = "sink";
        legacyGroups.targetPortId = "input_list";

        SavedConnection mapped = new SavedConnection();
        mapped.sourceNodeId = "map";
        mapped.sourcePortId = "output_list";
        mapped.targetNodeId = "sink";
        mapped.targetPortId = "input_list";

        v21.connections = new ArrayList<>(List.of(legacyGroups, mapped));
        v21.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v21);
        assertEquals(GraphFormatVersion.V22, migrated.formatVersion);
        assertEquals(3, migrated.nodes.size());
        assertTrue(migrated.nodes.stream().noneMatch(n -> "math.list.chunk".equals(n.typeId)));
        assertTrue(migrated.nodes.stream().anyMatch(n -> "math.list.map_numbers".equals(n.typeId)));
        assertTrue(migrated.nodes.stream().noneMatch(n -> "math.list.map_list".equals(n.typeId)));
        assertEquals(1, migrated.connections.size());
        assertEquals("output_list", migrated.connections.getFirst().sourcePortId);

        SavedNode migratedMap = migrated.nodes.stream()
                .filter(n -> "map".equals(n.nodeId))
                .findFirst()
                .orElseThrow();
        assertTrue(migratedMap.state instanceof Map<?, ?> state && !state.containsKey("ignoreNonNumeric"));
    }

    private static boolean hasPort(INode node, String portId) {
        return findPortOrNull(node, portId) != null;
    }

    private static IPort findPort(INode node, String portId) {
        IPort port = findPortOrNull(node, portId);
        assertNotNull(port, "missing port " + portId + " on " + node.getTypeId());
        return port;
    }

    private static IPort findPortOrNull(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (port != null && portId.equalsIgnoreCase(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
