package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.nodes.math.data_tree.EntwineNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.MergeTreesNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeBranchNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DispatchListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.FilterListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.GetItemNode;
import com.nodecraft.nodesystem.nodes.variable.GetVariableNode;
import com.nodecraft.nodesystem.nodes.variable.SetVariableNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 11 freeze: Data Tree / List / Variable language + LIST↔DATA_TREE conversion.
 */
class DataTreeListLanguageContractTest {

    private static final Set<String> STRUCTURAL_TREE_NODES = Set.of(
        "math.data_tree.merge",
        "math.data_tree.entwine",
        "math.data_tree.branch",
        "math.data_tree.flatten",
        "math.data_tree.graft_list",
        "math.data_tree.partition_list",
        "math.data_tree.simplify",
        "math.data_tree.shift_path",
        "math.data_tree.cull_empty",
        "math.data_tree.paths",
        "math.data_tree.statistics",
        "math.data_tree.viewer"
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void mergeAndEntwineAcceptOnlyDataTree() {
        assertPortType(new MergeTreesNode(), "input_a", NodeDataType.DATA_TREE);
        assertPortType(new MergeTreesNode(), "input_b", NodeDataType.DATA_TREE);
        assertPortType(new EntwineNode(), "input_a", NodeDataType.DATA_TREE);
        assertPortType(new EntwineNode(), "input_d", NodeDataType.DATA_TREE);
        assertFalse(hasAnyPort(new MergeTreesNode()));
        assertFalse(hasAnyPort(new EntwineNode()));
    }

    @Test
    void treePathPortsAreTreePath_itemOutputKeepsAny() {
        assertPortType(new TreeBranchNode(), "input_path", NodeDataType.TREE_PATH);
        assertPortType(new TreeItemNode(), "input_path", NodeDataType.TREE_PATH);
        assertPortType(new TreeItemNode(), "output_item", NodeDataType.ANY);
        assertFalse(hasAnyPort(new TreeBranchNode()));
    }

    @Test
    void filterAndDispatchConditionAreListMasks() {
        assertPortType(new FilterListNode(), "input_condition", NodeDataType.BOOLEAN_LIST);
        assertPortType(new DispatchListNode(), "input_condition", NodeDataType.BOOLEAN_LIST);
        assertFalse(hasAnyPort(new FilterListNode()));
        assertFalse(hasAnyPort(new DispatchListNode()));
    }

    @Test
    void polymorphicListItemPortsKeepAny() {
        assertTrue(hasAnyPort(new GetItemNode()));
        assertPortType(new GetItemNode(), "output_item", NodeDataType.ANY);
    }

    @Test
    void variablesKeepAnyAsDesignGoal() {
        assertPortType(new SetVariableNode(), "input_value", NodeDataType.ANY);
        assertPortType(new GetVariableNode(), "output_value", NodeDataType.ANY);
        assertTrue(hasAnyPort(new SetVariableNode()));
        assertTrue(hasAnyPort(new GetVariableNode()));
    }

    @Test
    void structuralDataTreeNodesForbidAnyExceptTreeItem() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : STRUCTURAL_TREE_NODES) {
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            if (hasAnyPort(instance)) {
                violations.add(nodeId);
            }
        }
        assertTrue(violations.isEmpty(), "Structural data-tree nodes must not expose ANY: " + violations);
    }

    @Test
    void listToDataTreeRequiresGraft_dataTreeToListRequiresFlatten() {
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.LIST, NodeDataType.DATA_TREE)
        );
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.DATA_TREE, NodeDataType.LIST)
        );
        TypeConversionRegistry.ConversionSuggestion graft =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.LIST, NodeDataType.DATA_TREE);
        assertNotNull(graft);
        assertEquals("math.data_tree.graft_list", graft.nodeId());
        TypeConversionRegistry.ConversionSuggestion flatten =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.DATA_TREE, NodeDataType.LIST);
        assertNotNull(flatten);
        assertEquals("math.data_tree.flatten", flatten.nodeId());
    }

    @Test
    void allDataTreeNodesExceptItemForbidUnexpectedAny() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.data_tree.")) {
                continue;
            }
            if ("math.data_tree.item".equals(nodeId.toLowerCase(Locale.ROOT))) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            if (hasAnyPort(instance)) {
                violations.add(nodeId);
            }
        }
        assertTrue(violations.isEmpty(), "Unexpected ANY on data-tree nodes: " + violations);
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        assertEquals(expected, findPort(node, portId).getDataType(), node.getTypeId() + "#" + portId);
    }

    private static boolean hasAnyPort(INode node) {
        for (IPort port : allPorts(node)) {
            if (port.getDataType() == NodeDataType.ANY) {
                return true;
            }
        }
        return false;
    }

    private static List<IPort> allPorts(INode node) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports;
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : allPorts(node)) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + portId + " on " + node.getTypeId());
    }

    private static INode tryCreate(String nodeId) {
        try {
            return registry.createNodeInstance(nodeId);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}
