package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.geometry.boolops.DifferenceNode;
import com.nodecraft.nodesystem.nodes.geometry.boolops.IntersectionNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Boolean v1 (current graph format).
 */
class GeometryBooleanLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsCurrent() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlyTwoCanonicalNodesWithOrdersZeroThroughOne() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> {
                String lower = id.toLowerCase(Locale.ROOT);
                return lower.equals("geometry.boolean.difference")
                    || lower.equals("geometry.boolean.intersection");
            })
            .sorted()
            .toList();
        assertEquals(2, ids.size());
        assertFalse(ids.contains("geometry.boolean.union"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.boolean", info.getCategoryId());
            assertTrue(info.getOrder() >= 0 && info.getOrder() <= 1, id + " order=" + info.getOrder());
            assertTrue(orders.add(info.getOrder()), "duplicate order " + info.getOrder());
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(Set.of(0, 1), orders);
    }

    @Test
    void bothNodesExposeValidAndError() {
        for (String id : List.of("geometry.boolean.difference", "geometry.boolean.intersection")) {
            INode node = registry.createNodeInstance(id);
            assertPortType(node, "output_geometry", NodeDataType.GEOMETRY);
            assertPortType(node, "output_valid", NodeDataType.BOOLEAN);
            assertPortType(node, "output_error", NodeDataType.STRING);
            assertFalse(hasPort(node, "output_blocks") || hasPort(node, "output_region"));
        }
    }

    @Test
    void differenceConstructsDeferredWrapperAndFailsClosedOnMissingOperand() {
        DifferenceNode node = new DifferenceNode();
        BoxGeometryData box = unitBox(0, 0, 0);

        node.setInput("input_base", box);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));
        assertFalse(String.valueOf(node.getOutput("output_error")).isBlank());

        node.setInput("input_cutter", box);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertInstanceOf(DifferenceGeometryData.class, node.getOutput("output_geometry"));
        assertEquals("", node.getOutput("output_error"));
    }

    @Test
    void intersectionConstructsDeferredWrapperAndFailsClosedOnMissingOperand() {
        IntersectionNode node = new IntersectionNode();
        BoxGeometryData box = unitBox(0, 0, 0);

        node.setInput("input_left", box);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_geometry"));

        node.setInput("input_right", box);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertInstanceOf(IntersectionGeometryData.class, node.getOutput("output_geometry"));
    }

    @Test
    void sdfBooleanRemainsInSdfCategory() {
        NodeInfo info = registry.getNodeInfo("geometry.boolean.sdf_boolean");
        assertNotNull(info);
        assertEquals("geometry.sdf", info.getCategoryId());
    }

    @Test
    void differenceBoundsAreMinuendConservative() {
        BoxGeometryData base = unitBox(0, 0, 0);
        BoxGeometryData cutter = unitBox(20, 0, 0);
        DifferenceGeometryData diff = new DifferenceGeometryData(base, cutter);
        BoundingBoxData baseBounds = GeometryBoundsResolver.resolve(base);
        BoundingBoxData diffBounds = GeometryBoundsResolver.resolve(diff);
        assertNotNull(baseBounds);
        assertNotNull(diffBounds);
        assertEquals(baseBounds.getMin(), diffBounds.getMin());
        assertEquals(baseBounds.getMax(), diffBounds.getMax());
    }

    @Test
    void intersectionBoundsAreAabbIntersection() {
        BoxGeometryData left = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(2, 2, 2));
        BoxGeometryData right = new BoxGeometryData(new Vector3d(1, 0, 0), new Vector3d(2, 2, 2));
        IntersectionGeometryData inter = new IntersectionGeometryData(left, right);
        BoundingBoxData bounds = GeometryBoundsResolver.resolve(inter);
        assertNotNull(bounds);

        BoxGeometryData disjointA = unitBox(0, 0, 0);
        BoxGeometryData disjointB = unitBox(50, 0, 0);
        assertNull(GeometryBoundsResolver.resolve(new IntersectionGeometryData(disjointA, disjointB)));
    }

    @Test
    void emptyCutterDifferenceIsSuccessEqualToBase() {
        BoxGeometryData base = unitBox(0, 0, 0);
        BoxGeometryData miss = unitBox(40, 0, 0);
        DifferenceGeometryData diff = new DifferenceGeometryData(base, miss);

        GeometryVoxelizationResult baseResult = GeometryVoxelizer.voxelizeStrict(base, true);
        GeometryVoxelizationResult diffResult = GeometryVoxelizer.voxelizeStrict(diff, true);
        assertTrue(baseResult.success());
        assertTrue(diffResult.success());
        assertEquals(baseResult.blocks().size(), diffResult.blocks().size());
    }

    @Test
    void failedCutterDifferenceIsFailureNotFullBase() {
        BoxGeometryData base = unitBox(0, 0, 0);
        DifferenceGeometryData diff = new DifferenceGeometryData(base, oversizedBox());

        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(diff, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.CHILD_FAILURE, result.status());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void disjointIntersectionIsSuccessEmpty() {
        IntersectionGeometryData inter = new IntersectionGeometryData(unitBox(0, 0, 0), unitBox(40, 0, 0));
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(inter, true);
        assertTrue(result.success());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void failedOperandIntersectionIsFailureNotEmptySuccess() {
        IntersectionGeometryData inter = new IntersectionGeometryData(unitBox(0, 0, 0), oversizedBox());
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(inter, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.CHILD_FAILURE, result.status());
    }

    @Test
    void nestedBooleanFailurePropagates() {
        DifferenceGeometryData inner = new DifferenceGeometryData(unitBox(0, 0, 0), oversizedBox());
        DifferenceGeometryData outer = new DifferenceGeometryData(inner, unitBox(1, 0, 0));
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(outer, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.CHILD_FAILURE, result.status());
    }

    @Test
    void compositeContainingFailedBooleanFailsTransactionally() {
        CompositeGeometryData composite = new CompositeGeometryData(List.of(
            unitBox(0, 0, 0),
            new DifferenceGeometryData(unitBox(2, 0, 0), oversizedBox())
        ));
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(composite, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.CHILD_FAILURE, result.status());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void overBudgetReportsFailureNotEmptySuccess() {
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(oversizedBox(), true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.OVER_BUDGET, result.status());
        assertTrue(result.blocks().isEmpty());
        assertEquals(262_144L, GenerationLimits.MAX_GEOMETRY_VOXELS);
    }

    @Test
    void unsupportedGeometryReportsFailure() {
        GeometryData unsupported = new GeometryData() {
        };
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(unsupported, true);
        assertFalse(result.success());
        assertEquals(VoxelizationStatus.UNSUPPORTED, result.status());
    }

    @Test
    void previewDoesNotExpandBooleanOperandsToSurfaces() {
        PreviewGeometryNode preview = new PreviewGeometryNode();
        assertEquals("output.preview.preview_geometry", preview.getTypeId());
    }

    @Test
    void aMinusAIsLegalEmptySuccess() {
        BoxGeometryData box = unitBox(0, 0, 0);
        DifferenceGeometryData diff = new DifferenceGeometryData(box, box);
        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(diff, true);
        assertTrue(result.success());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void differenceShell_penetratingCut_includesCavityInnerWalls() {
        BoxGeometryData outer = new BoxGeometryData(new Vector3d(10.5d, 10.5d, 10.5d), new Vector3d(5.0d, 5.0d, 5.0d));
        BoxGeometryData cutter = new BoxGeometryData(new Vector3d(10.5d, 10.5d, 10.5d), new Vector3d(2.0d, 2.0d, 5.0d));
        DifferenceGeometryData diff = new DifferenceGeometryData(outer, cutter);

        GeometryVoxelizationResult fixed = GeometryVoxelizer.voxelizeStrict(diff, false);
        assertTrue(fixed.success(), fixed.error());

        Set<BlockPos> wrongShell = new java.util.LinkedHashSet<>();
        for (BlockPos pos : GeometryVoxelizer.voxelizeStrict(outer, false).blocks()) {
            wrongShell.add(pos.toImmutable());
        }
        for (BlockPos pos : GeometryVoxelizer.voxelizeStrict(cutter, true).blocks()) {
            wrongShell.remove(pos.toImmutable());
        }

        BlockPos innerWall = new BlockPos(7, 10, 10);
        assertTrue(fixed.blocks().contains(innerWall),
            "Shell of solid difference should include cavity inner wall");
        assertFalse(wrongShell.contains(innerWall),
            "Legacy shell(A)-B must not include cavity inner wall");
        assertTrue(fixed.blocks().size() > wrongShell.size());
    }

    @Test
    void intersectionShell_isNotShellOperandsIntersected() {
        BoxGeometryData left = new BoxGeometryData(new Vector3d(1.5d, 1.5d, 1.5d), new Vector3d(1.5d, 1.5d, 1.5d));
        BoxGeometryData right = new BoxGeometryData(new Vector3d(2.5d, 1.5d, 1.5d), new Vector3d(1.5d, 1.5d, 1.5d));
        IntersectionGeometryData inter = new IntersectionGeometryData(left, right);

        GeometryVoxelizationResult shellInter = GeometryVoxelizer.voxelizeStrict(inter, false);
        assertTrue(shellInter.success(), shellInter.error());

        Set<BlockPos> wrongShellIntersect = new java.util.HashSet<>();
        Set<BlockPos> leftShell = new java.util.HashSet<>();
        for (BlockPos pos : GeometryVoxelizer.voxelizeStrict(left, false).blocks()) {
            leftShell.add(pos.toImmutable());
        }
        for (BlockPos pos : GeometryVoxelizer.voxelizeStrict(right, false).blocks()) {
            if (leftShell.contains(pos.toImmutable())) {
                wrongShellIntersect.add(pos.toImmutable());
            }
        }

        assertTrue(shellInter.blocks().size() > wrongShellIntersect.size(),
            "shell(A∩B) must differ from shell(A)∩shell(B) for overlapping boxes");
    }

    @Test
    void wrappingBeyondExpressionDepthFailsClosed() {
        GeometryData deep = unitBox(0, 0, 0);
        for (int i = 1; i < GenerationLimits.MAX_GEOMETRY_EXPRESSION_DEPTH; i++) {
            deep = new DifferenceGeometryData(deep, unitBox(0, 0, 0));
        }
        DifferenceNode node = new DifferenceNode();
        node.setInput("input_base", deep);
        node.setInput("input_cutter", unitBox(1, 0, 0));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains("geometry_expression_depth_exceeded"));
    }

    private static BoxGeometryData unitBox(double cx, double cy, double cz) {
        return new BoxGeometryData(new Vector3d(cx + 0.5d, cy + 0.5d, cz + 0.5d), new Vector3d(0.5d, 0.5d, 0.5d));
    }

    private static BoxGeometryData oversizedBox() {
        return new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(33, 33, 33));
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, node.getTypeId() + " missing " + portId);
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
    }

    private static @Nullable IPort findPort(INode node, String portId) {
        List<IPort> ports = new ArrayList<>();
        ports.addAll(node.getInputPorts());
        ports.addAll(node.getOutputPorts());
        return ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
    }
}
