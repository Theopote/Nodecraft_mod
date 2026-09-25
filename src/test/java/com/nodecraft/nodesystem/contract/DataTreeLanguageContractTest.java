package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.ConstructTreePathNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.EntwineNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.FlattenTreeNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.GraftListNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.MergeTreesNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.ShiftPathNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeBranchNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeItemNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreePathsNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeStatisticsNode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freezes Data Tree v1: DataTree&lt;T&gt;, TREE_PATH, unique-path invariant (Graph V24).
 */
class DataTreeLanguageContractTest {

    @BeforeAll
    static void init() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void treePathAndIntegerListTypesExist() {
        assertEquals(TreePathData.class, NodeDataType.TREE_PATH.getJavaClass());
        assertEquals(ListElementKind.TREE_PATH, NodeDataType.TREE_PATH_LIST.getListElementKind());
        assertEquals(ListElementKind.INTEGER, NodeDataType.INTEGER_LIST.getListElementKind());
        assertNotNull(NodeRegistry.getInstance().getNodeInfo("math.data_tree.tree_path"));
    }

    @Test
    void graftThenFlattenPreservesPointListEffectiveType() {
        GraftListNode graft = new GraftListNode();
        FlattenTreeNode flatten = new FlattenTreeNode();

        BasePort pointListOut = outputPort("points", NodeDataType.POINT_LIST);
        assertTrue(pointListOut.connectTo(findPort(graft, "input_list")));
        assertTrue(findPort(graft, "output_tree").connectTo(findPort(flatten, "input_tree")));

        assertEquals(NodeDataType.POINT_LIST, PortTypeResolver.resolveEffectiveType(findPort(flatten, "output_list")));

        BasePort pointListIn = inputPort("points_in", NodeDataType.POINT_LIST);
        assertTrue(PortTypeResolver.isConnectable(findPort(flatten, "output_list"), pointListIn));
    }

    @Test
    void treeBranchAndItemRequireTreePathAndFailClosed() {
        TreeBranchNode branch = new TreeBranchNode();
        TreeItemNode item = new TreeItemNode();
        assertEquals(NodeDataType.TREE_PATH, findPort(branch, "input_path").getDataType());
        assertEquals(NodeDataType.TREE_PATH, findPort(item, "input_path").getDataType());

        BasePort stringOut = outputPort("s", NodeDataType.STRING);
        assertFalse(PortTypeResolver.isConnectable(stringOut, findPort(branch, "input_path")));
        assertFalse(PortTypeResolver.isConnectable(stringOut, findPort(item, "input_path")));

        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("a", "b"))
        ), ListElementKind.STRING);

        branch.setInput("input_tree", tree);
        branch.setInput("input_path", "not-a-path");
        branch.processNode(null);
        assertEquals(Boolean.FALSE, branch.getOutput("output_found"));

        item.setInput("input_tree", tree);
        item.setInput("input_path", TreePathData.of(0));
        item.setInput("input_index", 99);
        item.processNode(null);
        assertEquals(Boolean.FALSE, item.getOutput("output_found"));

        item.setInput("input_index", -1);
        item.processNode(null);
        assertEquals(Boolean.TRUE, item.getOutput("output_found"));
        assertEquals("b", item.getOutput("output_item"));
    }

    @Test
    void duplicatePathsMergeItemsAndShiftCollisionsMerge() {
        DataTreeData dup = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(1), List.of("A")),
                new DataTreeData.Branch(List.of(1), List.of("B"))
        ));
        assertEquals(1, dup.getBranchCount());
        assertEquals(List.of("A", "B"), dup.getBranch(List.of(1)).items());

        ShiftPathNode shift = new ShiftPathNode();
        DataTreeData source = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0, 1), List.of("A")),
                new DataTreeData.Branch(List.of(1, 1), List.of("B"))
        ));
        shift.setInput("input_tree", source);
        shift.setInput("input_shift", 1);
        shift.processNode(null);
        DataTreeData shifted = (DataTreeData) shift.getOutput("output_tree");
        assertEquals(1, shifted.getBranchCount());
        assertEquals(List.of("A", "B"), shifted.getBranch(List.of(1)).items());
    }

    @Test
    void mergeConcatenatesSamePathWhileEntwinePrefixesSource() {
        DataTreeData a = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A1", "A2"))
        ));
        DataTreeData b = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("B1"))
        ));

        MergeTreesNode merge = new MergeTreesNode();
        merge.setInput("input_a", a);
        merge.setInput("input_b", b);
        merge.processNode(null);
        DataTreeData merged = (DataTreeData) merge.getOutput("output_tree");
        assertEquals(1, merged.getBranchCount());
        assertEquals(List.of("A1", "A2", "B1"), merged.getBranch(List.of(0)).items());

        EntwineNode entwine = new EntwineNode();
        entwine.setInput("input_a", a);
        entwine.setInput("input_b", b);
        entwine.processNode(null);
        DataTreeData entwined = (DataTreeData) entwine.getOutput("output_tree");
        assertEquals(2, entwined.getBranchCount());
        assertEquals(List.of("A1", "A2"), entwined.getBranch(List.of(0, 0)).items());
        assertEquals(List.of("B1"), entwined.getBranch(List.of(1, 0)).items());
    }

    @Test
    void mergeAndEntwineRejectConflictingTreeElementKinds() {
        GraftListNode pointGraftA = new GraftListNode();
        GraftListNode pointGraftB = new GraftListNode();
        GraftListNode vectorGraft = new GraftListNode();
        assertTrue(outputPort("pl_a", NodeDataType.POINT_LIST).connectTo(findPort(pointGraftA, "input_list")));
        assertTrue(outputPort("pl_b", NodeDataType.POINT_LIST).connectTo(findPort(pointGraftB, "input_list")));
        assertTrue(outputPort("vl", NodeDataType.VECTOR_LIST).connectTo(findPort(vectorGraft, "input_list")));

        // Merge: POINT then VECTOR rejected; reverse also rejected; POINT+POINT allowed
        MergeTreesNode mergePv = new MergeTreesNode();
        assertTrue(findPort(pointGraftA, "output_tree").connectTo(findPort(mergePv, "input_a")));
        assertFalse(findPort(vectorGraft, "output_tree").connectTo(findPort(mergePv, "input_b")));

        MergeTreesNode mergeVp = new MergeTreesNode();
        assertTrue(findPort(vectorGraft, "output_tree").connectTo(findPort(mergeVp, "input_a")));
        assertFalse(findPort(pointGraftA, "output_tree").connectTo(findPort(mergeVp, "input_b")));

        MergeTreesNode mergePp = new MergeTreesNode();
        assertTrue(findPort(pointGraftA, "output_tree").connectTo(findPort(mergePp, "input_a")));
        assertTrue(findPort(pointGraftB, "output_tree").connectTo(findPort(mergePp, "input_b")));

        // Entwine: same same-T rule
        EntwineNode entwinePv = new EntwineNode();
        assertTrue(findPort(pointGraftA, "output_tree").connectTo(findPort(entwinePv, "input_a")));
        assertFalse(findPort(vectorGraft, "output_tree").connectTo(findPort(entwinePv, "input_b")));

        EntwineNode entwinePp = new EntwineNode();
        assertTrue(findPort(pointGraftA, "output_tree").connectTo(findPort(entwinePp, "input_a")));
        assertTrue(findPort(pointGraftB, "output_tree").connectTo(findPort(entwinePp, "input_b")));
    }

    @Test
    void graftMergeFlattenPreservesPointListThroughSameTMerge() {
        GraftListNode graftA = new GraftListNode();
        GraftListNode graftB = new GraftListNode();
        MergeTreesNode merge = new MergeTreesNode();
        FlattenTreeNode flatten = new FlattenTreeNode();

        assertTrue(outputPort("pl_a", NodeDataType.POINT_LIST).connectTo(findPort(graftA, "input_list")));
        assertTrue(outputPort("pl_b", NodeDataType.POINT_LIST).connectTo(findPort(graftB, "input_list")));
        assertTrue(findPort(graftA, "output_tree").connectTo(findPort(merge, "input_a")));
        assertTrue(findPort(graftB, "output_tree").connectTo(findPort(merge, "input_b")));
        assertTrue(findPort(merge, "output_tree").connectTo(findPort(flatten, "input_tree")));

        assertEquals(NodeDataType.POINT_LIST, PortTypeResolver.resolveEffectiveType(findPort(flatten, "output_list")));
        assertTrue(PortTypeResolver.isConnectable(
                findPort(flatten, "output_list"),
                inputPort("points_in", NodeDataType.POINT_LIST)));
    }

    @Test
    void treePathsAndStatisticsHaveSeparatedRoles() {
        assertEquals(NodeDataType.TREE_PATH_LIST, findPort(new TreePathsNode(), "output_paths").getDataType());
        assertFalse(hasPort(new TreePathsNode(), "output_path_strings"));
        assertFalse(hasPort(new TreeStatisticsNode(), "output_paths"));
        assertEquals(NodeDataType.INTEGER_LIST, findPort(new TreeStatisticsNode(), "output_branch_sizes").getDataType());
        assertEquals(NodeDataType.TREE_PATH, findPort(new ConstructTreePathNode(), "output_path").getDataType());
    }

    @Test
    void v23ToV24MigrationStripsPropsAndIllegalPathWires() {
        SavedGraph v23 = new SavedGraph();
        v23.formatVersion = GraphFormatVersion.V23;

        SavedNode item = new SavedNode();
        item.nodeId = "item";
        item.typeId = "math.data_tree.item";
        item.state = new HashMap<>(Map.of("allowNegativeIndex", true, "wrapIndex", true));

        SavedNode merge = new SavedNode();
        merge.nodeId = "merge";
        merge.typeId = "math.data_tree.merge";
        merge.state = new HashMap<>(Map.of("preserveSourceIndex", true));

        SavedNode partition = new SavedNode();
        partition.nodeId = "part";
        partition.typeId = "math.data_tree.partition_list";
        partition.state = new HashMap<>(Map.of("dropRemainder", true));

        SavedNode stringSrc = new SavedNode();
        stringSrc.nodeId = "str";
        stringSrc.typeId = "input.values.string";

        SavedNode branch = new SavedNode();
        branch.nodeId = "branch";
        branch.typeId = "math.data_tree.branch";

        SavedNode stats = new SavedNode();
        stats.nodeId = "stats";
        stats.typeId = "math.data_tree.statistics";

        SavedNode sink = new SavedNode();
        sink.nodeId = "sink";
        sink.typeId = "math.list.create_list";

        v23.nodes = new ArrayList<>(List.of(item, merge, partition, stringSrc, branch, stats, sink));

        SavedConnection stringToPath = new SavedConnection();
        stringToPath.sourceNodeId = "str";
        stringToPath.sourcePortId = "output_value";
        stringToPath.targetNodeId = "branch";
        stringToPath.targetPortId = "input_path";

        SavedConnection statsPaths = new SavedConnection();
        statsPaths.sourceNodeId = "stats";
        statsPaths.sourcePortId = "output_paths";
        statsPaths.targetNodeId = "sink";
        statsPaths.targetPortId = "input_0";

        v23.connections = new ArrayList<>(List.of(stringToPath, statsPaths));
        v23.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v23);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        @SuppressWarnings("unchecked")
        Map<String, Object> itemState = (Map<String, Object>) migrated.nodes.stream()
                .filter(n -> "item".equals(n.nodeId)).findFirst().orElseThrow().state;
        assertFalse(itemState.containsKey("allowNegativeIndex"));
        assertFalse(itemState.containsKey("wrapIndex"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mergeState = (Map<String, Object>) migrated.nodes.stream()
                .filter(n -> "merge".equals(n.nodeId)).findFirst().orElseThrow().state;
        assertFalse(mergeState.containsKey("preserveSourceIndex"));

        @SuppressWarnings("unchecked")
        Map<String, Object> partState = (Map<String, Object>) migrated.nodes.stream()
                .filter(n -> "part".equals(n.nodeId)).findFirst().orElseThrow().state;
        assertFalse(partState.containsKey("dropRemainder"));

        assertTrue(migrated.connections.isEmpty());
    }

    private static BasePort outputPort(String id, NodeDataType type) {
        BasePort port = new BasePort(id, id, id, type, null);
        port.setDirection(BasePort.Direction.OUTPUT);
        return port;
    }

    private static BasePort inputPort(String id, NodeDataType type) {
        BasePort port = new BasePort(id, id, id, type, null);
        port.setDirection(BasePort.Direction.INPUT);
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
