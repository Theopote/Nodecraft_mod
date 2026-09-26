package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DataSeriesNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DispatchListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.FilterListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.GroupListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.InsertItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ListStatisticsNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MapListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MathRangeNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.RepeatNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ReverseListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ShuffleListNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes List / Collection v1 including V23 typed-list boundary and List&lt;T&gt; preservation.
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
    void doubleBooleanAndStringListKindsExist() {
        assertEquals(ListElementKind.DOUBLE, NodeDataType.DOUBLE_LIST.getListElementKind());
        assertEquals(ListElementKind.BOOLEAN, NodeDataType.BOOLEAN_LIST.getListElementKind());
        assertEquals(ListElementKind.STRING, NodeDataType.STRING_LIST.getListElementKind());
    }

    @Test
    void typedListRejectsWrongElementsAtRuntime() {
        assertTrue(NodeDataType.DOUBLE_LIST.isCompatible(List.of(1.0, 2)));
        assertFalse(NodeDataType.DOUBLE_LIST.isCompatible(List.of("x")));
        assertTrue(NodeDataType.POINT_LIST.isCompatible(List.of(new PointData(0, 0, 0))));
        assertFalse(NodeDataType.POINT_LIST.isCompatible(List.of("abc")));
        assertTrue(NodeDataType.LIST.isCompatible(List.of("abc", 1, new PointData(0, 0, 0))));
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
    }

    @Test
    void filterAndDispatchUseBooleanListMask() {
        assertEquals(NodeDataType.BOOLEAN_LIST, findPort(new FilterListNode(), "input_condition").getDataType());
        assertEquals(NodeDataType.BOOLEAN_LIST, findPort(new DispatchListNode(), "input_condition").getDataType());
        assertTrue(hasPort(new FilterListNode(), "output_valid"));
        assertTrue(hasPort(new DispatchListNode(), "output_valid"));
    }

    @Test
    void reverseListPreservesPointListEffectiveType() {
        ReverseListNode reverse = new ReverseListNode();
        IPort reverseIn = findPort(reverse, "input_list");
        IPort reverseOut = findPort(reverse, "output_list");
        assertEquals("T", reverseIn.getListTypeVariable());
        assertEquals("T", reverseOut.getListTypeVariable());

        // Synthetic POINT_LIST producer port
        BasePort pointOut = new BasePort("out", "Points", "points", NodeDataType.POINT_LIST, null);
        pointOut.setDirection(BasePort.Direction.OUTPUT);
        assertTrue(pointOut.connectTo(reverseIn));

        assertEquals(NodeDataType.POINT_LIST, PortTypeResolver.resolveEffectiveType(reverseOut));

        BasePort pointIn = new BasePort("in", "Points", "points", NodeDataType.POINT_LIST, null);
        pointIn.setDirection(BasePort.Direction.INPUT);
        assertTrue(PortTypeResolver.isConnectable(reverseOut, pointIn));
    }

    @Test
    void shuffleIsDeterministicForSeedZero() {
        ShuffleListNode a = new ShuffleListNode();
        ShuffleListNode b = new ShuffleListNode();
        List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        a.setInput("input_list", new ArrayList<>(source));
        a.setInput("input_seed", 0);
        b.setInput("input_list", new ArrayList<>(source));
        b.setInput("input_seed", 0);
        a.processNode(null);
        b.processNode(null);
        assertEquals(a.getOutput("output_list"), b.getOutput("output_list"));
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
        node.setInput("input_data", List.of("a", "b"));
        node.setInput("input_count", 3);
        node.processNode(null);
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) node.getOutput("output_result");
        assertEquals(3, result.size());
        assertEquals(List.of("a", "b"), result.getFirst());
    }

    @Test
    void groupListEmitsDataTreeNotNestedList() {
        GroupListNode node = new GroupListNode();
        assertEquals(NodeDataType.DATA_TREE, findPort(node, "output_tree").getDataType());
        assertFalse(hasPort(node, "output_groups"));
    }

    @Test
    void deletedStructureAndSortReduceAreUnregistered() {
        NodeRegistry registry = NodeRegistry.getInstance();
        assertEquals(null, registry.getNodeInfo("math.list.chunk"));
        assertEquals(null, registry.getNodeInfo("math.list.sort_list"));
        assertEquals(null, registry.getNodeInfo("math.list.reduce"));
        assertNotNull(registry.getNodeInfo("math.list.sort_numbers"));
        assertNotNull(registry.getNodeInfo("math.list.sort_text"));
        assertNotNull(registry.getNodeInfo("math.list.sum_numbers"));
        assertNotNull(registry.getNodeInfo("math.list.average"));
    }

    @Test
    void v22ToV23MigrationDropsListToTypedAndDeletesSortReduce() {
        SavedGraph v22 = new SavedGraph();
        v22.formatVersion = GraphFormatVersion.V22;

        SavedNode create = new SavedNode();
        create.nodeId = "create";
        create.typeId = "math.list.create_list";

        SavedNode sort = new SavedNode();
        sort.nodeId = "sort";
        sort.typeId = "math.list.sort_list";

        SavedNode length = new SavedNode();
        length.nodeId = "length";
        length.typeId = "math.list.list_length";

        SavedNode snap = new SavedNode();
        snap.nodeId = "snap";
        snap.typeId = "world.selection.snap_points_to_blocks";

        SavedNode filter = new SavedNode();
        filter.nodeId = "filter";
        filter.typeId = "math.list.filter_list";

        SavedNode shuffle = new SavedNode();
        shuffle.nodeId = "shuffle";
        shuffle.typeId = "math.list.shuffle_list";
        shuffle.state = new HashMap<>(Map.of("preserveInput", true, "seed", 1));

        SavedNode unrelated = new SavedNode();
        unrelated.nodeId = "unrelated";
        unrelated.typeId = "math.list.create_list";
        unrelated.state = new HashMap<>(Map.of("defaultValue", "keep-me", "itemCount", 2));

        v22.nodes = new ArrayList<>(List.of(create, sort, length, snap, filter, shuffle, unrelated));

        SavedConnection createToSort = new SavedConnection();
        createToSort.sourceNodeId = "create";
        createToSort.sourcePortId = "output_list";
        createToSort.targetNodeId = "sort";
        createToSort.targetPortId = "input_list";

        SavedConnection sortToLength = new SavedConnection();
        sortToLength.sourceNodeId = "sort";
        sortToLength.sourcePortId = "output_list";
        sortToLength.targetNodeId = "length";
        sortToLength.targetPortId = "input_list";

        SavedConnection listToTyped = new SavedConnection();
        listToTyped.sourceNodeId = "create";
        listToTyped.sourcePortId = "output_list";
        listToTyped.targetNodeId = "snap";
        listToTyped.targetPortId = "input_points";

        SavedConnection filterMask = new SavedConnection();
        filterMask.sourceNodeId = "create";
        filterMask.sourcePortId = "output_list";
        filterMask.targetNodeId = "filter";
        filterMask.targetPortId = "input_condition";

        v22.connections = new ArrayList<>(List.of(createToSort, sortToLength, listToTyped, filterMask));
        v22.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v22);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertTrue(migrated.nodes.stream().noneMatch(n -> "math.list.sort_list".equals(n.typeId)));
        assertTrue(migrated.nodes.stream().anyMatch(n -> "length".equals(n.nodeId)));
        assertTrue(migrated.connections.isEmpty(), "orphan wires to deleted Sort and illegal LISTâ†’typed/mask wires must be dropped");

        SavedNode migratedShuffle = migrated.nodes.stream()
                .filter(n -> "shuffle".equals(n.nodeId))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> shuffleState = (Map<String, Object>) migratedShuffle.state;
        assertFalse(shuffleState.containsKey("preserveInput"));
        assertEquals(1, shuffleState.get("seed"));

        SavedNode migratedUnrelated = migrated.nodes.stream()
                .filter(n -> "unrelated".equals(n.nodeId))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> unrelatedState = (Map<String, Object>) migratedUnrelated.state;
        assertEquals("keep-me", unrelatedState.get("defaultValue"),
                "global-looking keys must not be stripped from unrelated node types");
    }

    @Test
    void insertItemTypeVariableConnectionOrderIsInvariant() {
        // Order A: STRING â†?Value first, then POINT_LIST â†?List must reject
        InsertItemNode insertA = new InsertItemNode();
        BasePort stringOutA = outputPort("string_out", NodeDataType.STRING);
        BasePort pointListOutA = outputPort("point_list_out", NodeDataType.POINT_LIST);
        assertTrue(stringOutA.connectTo(findPort(insertA, "input_value")));
        assertFalse(pointListOutA.connectTo(findPort(insertA, "input_list")),
                "POINT_LIST must not bind T=POINT while STRING already occupies Value<T>");

        // Order B: POINT_LIST â†?List first, then STRING â†?Value must reject
        InsertItemNode insertB = new InsertItemNode();
        BasePort stringOutB = outputPort("string_out", NodeDataType.STRING);
        BasePort pointListOutB = outputPort("point_list_out", NodeDataType.POINT_LIST);
        assertTrue(pointListOutB.connectTo(findPort(insertB, "input_list")));
        assertFalse(stringOutB.connectTo(findPort(insertB, "input_value")),
                "STRING must not connect to Value once T=POINT from List");

        // Matching types succeed in either order
        InsertItemNode insertC = new InsertItemNode();
        BasePort pointOutC = outputPort("point_out", NodeDataType.POINT);
        BasePort pointListOutC = outputPort("point_list_out", NodeDataType.POINT_LIST);
        assertTrue(pointOutC.connectTo(findPort(insertC, "input_value")));
        assertTrue(pointListOutC.connectTo(findPort(insertC, "input_list")));
        assertEquals(NodeDataType.POINT, PortTypeResolver.resolveEffectiveType(findPort(insertC, "input_value")));
        assertEquals(NodeDataType.POINT_LIST, PortTypeResolver.resolveEffectiveType(findPort(insertC, "input_list")));

        InsertItemNode insertD = new InsertItemNode();
        BasePort pointOutD = outputPort("point_out", NodeDataType.POINT);
        BasePort pointListOutD = outputPort("point_list_out", NodeDataType.POINT_LIST);
        assertTrue(pointListOutD.connectTo(findPort(insertD, "input_list")));
        assertTrue(pointOutD.connectTo(findPort(insertD, "input_value")));
        assertEquals(NodeDataType.POINT, PortTypeResolver.resolveEffectiveType(findPort(insertD, "input_value")));
    }

    @Test
    void insertItemInvalidIndexIsFailClosed() {
        InsertItemNode insert = new InsertItemNode();
        insert.setInput("input_list", new ArrayList<>(List.of("A", "B", "C")));
        insert.setInput("input_index", 999);
        insert.setInput("input_value", "X");
        insert.processNode(null);
        assertEquals(Boolean.FALSE, insert.getOutput("output_valid"));
        assertEquals(List.of(), insert.getOutput("output_list"));
    }

    @Test
    void setInputUsesEffectiveTypeAfterListTypeBinding() {
        InsertItemNode insert = new InsertItemNode();
        BasePort pointListOut = outputPort("point_list_out", NodeDataType.POINT_LIST);
        assertTrue(pointListOut.connectTo(findPort(insert, "input_list")));
        assertEquals(NodeDataType.POINT, PortTypeResolver.resolveEffectiveType(findPort(insert, "input_value")));

        insert.setInput("input_value", "abc");
        insert.setInput("input_list", List.of(new PointData(1, 2, 3)));
        insert.setInput("input_index", 0);
        insert.processNode(null);
        // Rejected STRING must not have been stored; insert with null value still Valid at index 0
        assertEquals(Boolean.TRUE, insert.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) insert.getOutput("output_list");
        assertEquals(2, result.size());
        assertNull(result.get(0), "rejected STRING must not appear as inserted value");

        PointData point = new PointData(9, 9, 9);
        insert.setInput("input_value", point);
        insert.setInput("input_list", List.of(new PointData(1, 2, 3)));
        insert.setInput("input_index", 0);
        insert.processNode(null);
        @SuppressWarnings("unchecked")
        List<Object> ok = (List<Object>) insert.getOutput("output_list");
        assertEquals(point, ok.get(0));
    }

    private static BasePort outputPort(String id, NodeDataType type) {
        BasePort port = new BasePort(id, id, id, type, null);
        port.setDirection(BasePort.Direction.OUTPUT);
        return port;
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
