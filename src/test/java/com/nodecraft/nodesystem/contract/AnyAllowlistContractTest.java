package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.nodes.math.compare.EqualsNode;
import com.nodecraft.nodesystem.nodes.math.compare.LessThanNode;
import com.nodecraft.nodesystem.nodes.math.logic.AndNode;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.math.logic.OrNode;
import com.nodecraft.nodesystem.nodes.math.logic.XorNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.AbsoluteNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.CreateBlockPaletteNode;
import com.nodecraft.nodesystem.nodes.reference.planes.ConstructPlaneNode;
import com.nodecraft.nodesystem.nodes.reference.points.BlockToPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.ClosestPointNode;
import com.nodecraft.nodesystem.nodes.reference.points.DistanceNode;
import com.nodecraft.nodesystem.nodes.reference.points.MidpointNode;
import com.nodecraft.nodesystem.nodes.reference.vectors.VectorScalarMultiplyNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPaletteData;
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
 * Node Language v1 freeze: ANY is allowlisted; cleaned families forbid ANY;
 * BLOCK_PALETTE is a strict typed port.
 */
class AnyAllowlistContractTest {

    /**
     * Prefixes still allowed to expose ANY (polymorphic containers / flow).
     * Adding a new prefix here is an explicit architecture decision.
     * Batch 11 removed blanket {@code math.list.} / {@code math.data_tree.} â€?
     * only the polymorphic item/value type ids below remain.
     */
    private static final Set<String> ANY_ALLOWED_PREFIXES = Set.of(
        "variable.",
        "math.random.random_list_item",
        "math.compare.equals",
        "math.compare.not_equals",
        "flow.",
        "utilities.",
        "output.debug.",
        "output.export.",
        "output.execute.clear_preview",
        "output.execute.bake_status",
        "world."
    );

    private static final Set<String> ANY_ALLOWED_TYPE_IDS = Set.of(
        "math.compare.equals",
        "math.compare.not_equals",
        // Polymorphic value selectors (Logic v1)
        "math.logic.if",
        "math.logic.switch",
        // Polymorphic list item / value containers (Batch 11)
        "math.list.get_item",
        "math.list.set_item",
        "math.list.insert_item",
        "math.list.remove_item",
        "math.sequence.repeat",
        "math.list.create_list",
        // Polymorphic tree item container
        "math.data_tree.item"
    );

    /** Families frozen in Batch 10 / 10.1 / 11 / reference ANY cleanup â€?must never regress to ANY. */
    private static final Set<String> ANY_FORBIDDEN_PREFIXES = Set.of(
        "math.trigonometry.",
        "math.scalar_math.",
        "math.fields.",
        "geometry.architectural_primitives.",
        "material.",
        "reference.",
        "input.values."
    );

    private static final Set<String> ANY_FORBIDDEN_TYPE_IDS = Set.of(
        "math.compare.less_than",
        "math.compare.greater_than",
        "math.compare.less_than_or_equal",
        "math.compare.greater_than_or_equal",
        "math.sequence.range",
        // Batch 11: typed tree/list structure ports
        "math.data_tree.merge",
        "math.data_tree.entwine",
        "math.data_tree.branch",
        "math.list.filter_list",
        "math.list.dispatch_list"
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
    void assistPassthroughAnyPortsMustBindTypeVariable() {
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("utilities.assist.")) {
                continue;
            }
            if ("utilities.assist.string_format".equals(nodeId)) {
                continue; // ANY sinks only â€?format to STRING
            }
            INode instance = tryCreate(nodeId);
            assertNotNull(instance, nodeId);
            for (IPort port : allPorts(instance)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    assertTrue(port.isPassthroughBinding(),
                            nodeId + "#" + port.getId() + " ANY must bind passthrough T");
                }
            }
        }
    }

    @Test
    void frozenFamiliesMustNotExposeAny() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!isForbidden(nodeId)) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            for (IPort port : allPorts(instance)) {
                if (port.getDataType() == NodeDataType.ANY) {
                    violations.add(nodeId + "#" + port.getId());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Frozen language families still expose ANY: " + violations);
    }

    @Test
    void anyPortsOutsideAllowlistAreRejected() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (isForbidden(nodeId)) {
                continue;
            }
            INode instance = tryCreate(nodeId);
            if (instance == null) {
                continue;
            }
            if (hasAnyPort(instance) && !isAllowlisted(nodeId)) {
                violations.add(nodeId);
            }
        }
        assertTrue(violations.isEmpty(),
            "ANY ports require an explicit allowlist entry (Batch 10.1 freeze): " + violations);
    }

    @Test
    void referencePointPortsAreTypedNotAny() {
        assertEquals(NodeDataType.POINT, findPort(new DistanceNode(), "input_point_a").getDataType());
        assertEquals(NodeDataType.POINT, findPort(new MidpointNode(), "input_point_a").getDataType());
        assertEquals(NodeDataType.POINT, findPort(new ConstructPlaneNode(), "input_origin").getDataType());
        assertEquals(NodeDataType.BLOCK_POS, findPort(new BlockToPointNode(), "input_coordinate").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(new VectorScalarMultiplyNode(), "input_scalar").getDataType());
        assertEquals(NodeDataType.POINT_LIST, findPort(new ClosestPointNode(), "input_coordinates").getDataType());
        assertEquals(NodeDataType.POINT, findPort(new ClosestPointNode(), "output_closest_point").getDataType());
    }

    @Test
    void polymorphicCoreKeepsAny_numericDoesNot() {
        assertTrue(hasAnyPort(new EqualsNode()));
        assertTrue(hasAnyPort(new IfNode()));
        assertFalse(hasAnyPort(new AndNode()));
        assertFalse(hasAnyPort(new OrNode()));
        assertFalse(hasAnyPort(new XorNode()));
        assertFalse(hasAnyPort(new LessThanNode()));
        assertFalse(hasAnyPort(new AbsoluteNode()));
        assertFalse(hasAnyPort(new SineNode()));
    }

    @Test
    void blockPalettePortIsStrictTyped() {
        assertEquals(NodeDataType.BLOCK_PALETTE, findPort(new CreateBlockPaletteNode(), "output_palette").getDataType());
        assertEquals(NodeDataType.BLOCK_PALETTE, findPort(new BlockPaletteNode(), "input_palette").getDataType());
        assertTrue(NodeDataType.BLOCK_PALETTE.isCompatible(BlockPaletteData.ofBlockIds(List.of("minecraft:stone"))));
        assertFalse(NodeDataType.BLOCK_PALETTE.isCompatible(List.of("minecraft:stone")));
        assertFalse(NodeDataType.BLOCK_PALETTE.isCompatible("minecraft:stone"));
        assertFalse(NodeDataType.BLOCK_PALETTE.isCompatible(42));
    }

    @Test
    void legacyListToPaletteRequiresCreateBlockPalette() {
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.LIST, NodeDataType.BLOCK_PALETTE)
        );
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.EXPLICIT_REQUIRED,
            TypeConversionRegistry.classify(NodeDataType.BLOCK_TYPE, NodeDataType.BLOCK_PALETTE)
        );
        TypeConversionRegistry.ConversionSuggestion suggestion =
            TypeConversionRegistry.getSuggestedConversion(NodeDataType.LIST, NodeDataType.BLOCK_PALETTE);
        assertNotNull(suggestion);
        assertEquals("material.basic_assignment.create_block_palette", suggestion.nodeId());
    }

    private static boolean isForbidden(String nodeId) {
        String id = nodeId.toLowerCase(Locale.ROOT);
        if (ANY_FORBIDDEN_TYPE_IDS.contains(id)) {
            return true;
        }
        for (String prefix : ANY_FORBIDDEN_PREFIXES) {
            if (id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowlisted(String nodeId) {
        String id = nodeId.toLowerCase(Locale.ROOT);
        if (ANY_ALLOWED_TYPE_IDS.contains(id)) {
            return true;
        }
        for (String prefix : ANY_ALLOWED_PREFIXES) {
            if (id.startsWith(prefix) || id.equals(prefix)) {
                return true;
            }
        }
        return false;
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
