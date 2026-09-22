package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.nodes.math.compare.EqualsNode;
import com.nodecraft.nodesystem.nodes.math.compare.LessThanNode;
import com.nodecraft.nodesystem.nodes.math.logic.IfNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.AbsoluteNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.DivisionNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.CreateBlockPaletteNode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 10 ANY policy freeze for cleaned math/material families.
 * Broader catalog ANY cleanup continues in later batches.
 */
class AnyAllowlistContractTest {

    /** Families finished in Batch 10 — must not expose ANY. */
    private static final Set<String> BATCH10_FORBIDDEN_PREFIXES = Set.of(
        "math.trigonometry.",
        "math.scalar_math.",
        "material."
    );

    private static final Set<String> BATCH10_FORBIDDEN_TYPE_IDS = Set.of(
        "math.compare.less_than",
        "math.compare.greater_than",
        "math.compare.less_than_or_equal",
        "math.compare.greater_than_or_equal",
        "math.compare.compare",
        "math.sequence.range"
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
    void batch10CleanedFamiliesMustNotExposeAny() {
        List<String> violations = new ArrayList<>();
        for (String nodeId : registry.getAllNodeIds()) {
            if (!isBatch10Forbidden(nodeId)) {
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
        assertTrue(violations.isEmpty(), "Batch 10 cleaned nodes still expose ANY: " + violations);
    }

    @Test
    void polymorphicNodesKeepAnyWhereIntended() {
        assertTrue(hasAnyPort(new EqualsNode()));
        assertTrue(hasAnyPort(new IfNode()));
        assertFalse(hasAnyPort(new LessThanNode()));
        assertFalse(hasAnyPort(new AbsoluteNode()));
        assertFalse(hasAnyPort(new DivisionNode()));
        assertFalse(hasAnyPort(new SineNode()));
    }

    @Test
    void blockPaletteLanguageIsTyped() {
        assertEquals(NodeDataType.BLOCK_PALETTE, findPort(new CreateBlockPaletteNode(), "output_palette").getDataType());
        assertEquals(NodeDataType.BLOCK_PALETTE, findPort(new BlockPaletteNode(), "input_palette").getDataType());
        assertTrue(NodeDataType.BLOCK_PALETTE.isCompatible(BlockPaletteData.ofBlockIds(List.of("minecraft:stone"))));
        assertTrue(NodeDataType.BLOCK_PALETTE.isCompatible(List.of("minecraft:stone")));
        assertFalse(NodeDataType.BLOCK_PALETTE.isCompatible(42));
    }

    private static boolean isBatch10Forbidden(String nodeId) {
        String id = nodeId.toLowerCase(Locale.ROOT);
        if (BATCH10_FORBIDDEN_TYPE_IDS.contains(id)) {
            return true;
        }
        for (String prefix : BATCH10_FORBIDDEN_PREFIXES) {
            if (id.startsWith(prefix)) {
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
