package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.CircleOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileBoolean2DNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileOffsetInPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ResamplePolygonProfileNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PolygonProfileValidator;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Profiles / Polygon Profile Language v1 (Graph V73).
 */
class GeometryProfilesLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV73() {
        assertEquals(73, GraphFormatVersion.V73);
        assertEquals(GraphFormatVersion.V73, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyTwentyThreeProfileNodesWithUniqueOrdersZeroToTwentyTwo() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.profiles."))
            .sorted()
            .toList();
        assertEquals(23, ids.size());
        assertFalse(ids.contains("geometry.profiles.convex_hull_3d_points"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.profiles", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(23, orders.size());
        for (int i = 0; i < 23; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void convexHull3DMovedToAnalysis() {
        assertTrue(registry.getAllNodeIds().contains("geometry.analysis.convex_hull_3d"));
        assertFalse(registry.getAllNodeIds().contains("geometry.profiles.convex_hull_3d_points"));
    }

    @Test
    void allProfileNodesHaveValidAndErrorWithoutRawListOrPolylinePorts() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.profiles."))
            .toList()) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " input is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
                if (port.getDataType() == NodeDataType.POLYLINE) {
                    errors.add(id + "." + port.getId() + " uses graph POLYLINE");
                }
            }
            for (IPort port : node.getOutputPorts()) {
                if (port.getDataType() == NodeDataType.ANY) {
                    errors.add(id + "." + port.getId() + " output is ANY");
                }
                if (port.getDataType() == NodeDataType.LIST) {
                    errors.add(id + "." + port.getId() + " uses raw LIST");
                }
                if (port.getDataType() == NodeDataType.POLYLINE) {
                    errors.add(id + "." + port.getId() + " uses graph POLYLINE");
                }
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void typedProfileListOutputsExist() {
        assertPortType("geometry.profiles.boolean_2d", "output_profiles", false, NodeDataType.POLYGON_PROFILE_LIST);
        assertPortType("geometry.profiles.offset_profile_plane", "output_profiles", false, NodeDataType.POLYGON_PROFILE_LIST);
        assertPortType("geometry.profiles.triangulate_2d", "output_triangles", false, NodeDataType.POLYGON_PROFILE_LIST);
        assertPortType("geometry.profiles.voronoi_cells_plane", "output_cells", false, NodeDataType.POLYGON_PROFILE_LIST);
    }

    @Test
    void polygonProfileValidatorRejectsNaNAndSelfIntersection() {
        List<Vector3d> nanPoint = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(Double.NaN, 0, 1),
            new Vector3d(1, 0, 1),
            new Vector3d(0, 0, 0)
        );
        assertNotNull(PolygonProfileValidator.validateConstruction(nanPoint, PlaneData.XZ_PLANE));

        List<Vector3d> bowTie = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(4, 0, 4),
            new Vector3d(4, 0, 0),
            new Vector3d(0, 0, 4),
            new Vector3d(0, 0, 0)
        );
        String bowTieError = PolygonProfileValidator.validateConstruction(bowTie, PlaneData.XZ_PLANE);
        assertNotNull(bowTieError);
        String lower = bowTieError.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("self-intersect") || lower.contains("zero area"),
            "expected self-intersect or zero area, was: " + bowTieError);
    }

    @Test
    void booleanDifferentPlanesFailsClosed() {
        ProfileBoolean2DNode node = new ProfileBoolean2DNode();
        PolygonProfileData a = unitSquareProfile();
        PolygonProfileData b = unitSquareProfileOnXY();
        node.setInput("input_profile_a", a);
        node.setInput("input_profile_b", b);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("same plane"));
    }

    @Test
    void booleanDifferenceSameProfileIsSuccessEmpty() {
        ProfileBoolean2DNode node = new ProfileBoolean2DNode();
        node.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        PolygonProfileData profile = unitSquareProfile();
        node.setInput("input_profile_a", profile);
        node.setInput("input_profile_b", profile);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_error"));
        assertNull(node.getOutput("output_profile"));
        assertEquals(0, node.getOutput("output_count"));
    }

    @Test
    void offsetZeroIsPassthrough() {
        ProfileOffsetInPlaneNode node = new ProfileOffsetInPlaneNode();
        PolygonProfileData profile = unitSquareProfile();
        node.setInput("input_profile", profile);
        node.setInput("input_offset", 0.0d);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(profile, node.getOutput("output_profile"));
        assertEquals(1, node.getOutput("output_count"));
    }

    @Test
    void resampleOverBudgetFailsClosed() {
        ResamplePolygonProfileNode node = new ResamplePolygonProfileNode();
        connectInput(node, "input_edge_count", NodeDataType.INTEGER);
        node.setInput("input_profile", unitSquareProfile());
        node.setInput("input_edge_count", GenerationLimits.MAX_PROFILE_VERTICES + 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNotNull(node.getOutput("output_error"));
    }

    @Test
    void migrateV72ToV73MovesConvexHull3DToAnalysis() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V72;
        SavedNode hull = new SavedNode();
        hull.nodeId = "hull";
        hull.typeId = "geometry.profiles.convex_hull_3d_points";
        graph.nodes = List.of(hull);

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V73, migrated.formatVersion);
        assertEquals("geometry.analysis.convex_hull_3d", migrated.nodes.getFirst().typeId);
    }

    @Test
    void connectedInvalidPlaneFailsClosed() {
        CircleOnPlaneNode circle = new CircleOnPlaneNode();
        connectInput(circle, "input_plane", NodeDataType.PLANE);
        circle.setInput("input_plane", "not-a-plane");
        circle.processNode(null);
        assertEquals(Boolean.FALSE, circle.getOutput("output_valid"));
        assertTrue(String.valueOf(circle.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("plane"));
    }

    private static PolygonProfileData unitSquareProfile() {
        List<Vector3d> closed = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(4, 0, 0),
            new Vector3d(4, 0, 4),
            new Vector3d(0, 0, 4),
            new Vector3d(0, 0, 0)
        );
        return new PolygonProfileData(closed, PlaneData.XZ_PLANE);
    }

    private static PolygonProfileData unitSquareProfileOnXY() {
        List<Vector3d> closed = List.of(
            new Vector3d(0, 0, 0),
            new Vector3d(4, 0, 0),
            new Vector3d(4, 4, 0),
            new Vector3d(0, 4, 0),
            new Vector3d(0, 0, 0)
        );
        return new PolygonProfileData(closed, PlaneData.XY_PLANE);
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
        target.getInput(inputPortId);
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = findPort(node, portId, input);
        assertNotNull(port, typeId + " missing " + portId);
        assertEquals(expected, port.getDataType());
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId, true) != null || findPort(node, portId, false) != null;
    }

    private static IPort findPort(INode node, String portId, boolean input) {
        List<IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        return ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
