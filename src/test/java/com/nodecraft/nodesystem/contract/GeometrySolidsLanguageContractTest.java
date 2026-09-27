package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeProfileNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.LoftProfilesNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MatchSectionsMode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MorphBetweenProfilesNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.MultiSectionLoftNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.PushPullBoxFaceNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ThickenSurfaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Solids / Surface Modeling v1 (Graph V72).
 */
class GeometrySolidsLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV72() {
        assertEquals(72, GraphFormatVersion.V72);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V72);
    }

    @Test
    void exactlyTwentyTwoSolidNodesWithUniqueOrdersZeroToTwentyOne() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.solids."))
            .sorted()
            .toList();
        assertEquals(22, ids.size());
        assertFalse(ids.contains("geometry.solids.extrude_profile"));
        assertFalse(ids.contains("geometry.solids.shell"));

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.solids", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(22, orders.size());
        for (int i = 0; i < 22; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void allSolidNodesHaveValidAndErrorWithoutRawListOrPolylinePorts() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.solids."))
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
    void sectionCutAndContourHaveNoLegacyGeometryMirrorInputs() {
        INode section = registry.createNodeInstance("geometry.solids.section_cut");
        INode contour = registry.createNodeInstance("geometry.solids.contour");
        for (INode node : List.of(section, contour)) {
            assertFalse(hasPort(node, "input_box_geometry"));
            assertFalse(hasPort(node, "input_cylinder_geometry"));
            assertFalse(hasPort(node, "input_sphere_geometry"));
            assertFalse(hasPort(node, "input_torus_geometry"));
            assertTrue(hasPort(node, "input_geometry"));
        }
    }

    @Test
    void thickenHasNoLatticeGeometryOutput() {
        ThickenSurfaceNode thicken = new ThickenSurfaceNode();
        assertFalse(hasPort(thicken, "output_geometry"));
        assertPortType("geometry.solids.thicken_surface", "output_side_caps", false, NodeDataType.SURFACE_STRIP_LIST);
        assertPortType("geometry.solids.offset_surface_strip", "output_surface_strip", false, NodeDataType.SURFACE_STRIP);
        assertFalse(hasPort(registry.createNodeInstance("geometry.solids.offset_surface_strip"), "output_geometry"));
    }

    @Test
    void loftDefaultsToStrictMatchSections() {
        LoftProfilesNode loft = new LoftProfilesNode();
        assertEquals(MatchSectionsMode.STRICT, loft.getMatchSectionsMode());
        MultiSectionLoftNode multi = new MultiSectionLoftNode();
        assertEquals(MatchSectionsMode.STRICT, multi.getMatchSectionsMode());
    }

    @Test
    void extrudeInvalidDirectionFailsClosed() {
        ExtrudeProfileNode extrude = new ExtrudeProfileNode();
        connectInput(extrude, "input_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_profile", unitSquareProfile());
        extrude.setInput("input_direction", new Vector3d(0, 0, 0));
        extrude.processNode(null);
        assertEquals(Boolean.FALSE, extrude.getOutput("output_valid"));
        assertNotNull(extrude.getOutput("output_error"));
        assertFalse(String.valueOf(extrude.getOutput("output_error")).isBlank());
    }

    @Test
    void morphRejectsOutOfRangeT() {
        MorphBetweenProfilesNode morph = new MorphBetweenProfilesNode();
        connectInput(morph, "input_source_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(morph, "input_target_profile", NodeDataType.POLYGON_PROFILE);
        connectInput(morph, "input_t", NodeDataType.DOUBLE);
        morph.setInput("input_source_profile", unitSquareProfile());
        morph.setInput("input_target_profile", unitSquareProfile());
        morph.setInput("input_t", 1.5d);
        morph.processNode(null);
        assertEquals(Boolean.FALSE, morph.getOutput("output_valid"));
        assertNull(morph.getOutput("output_profile"));
    }

    @Test
    void multiLoftRejectsInvalidProfileListMember() {
        MultiSectionLoftNode multi = new MultiSectionLoftNode();
        connectInput(multi, "input_profiles", NodeDataType.POLYGON_PROFILE_LIST);
        List<Object> bad = new ArrayList<>();
        bad.add(unitSquareProfile());
        bad.add("garbage");
        bad.add(unitSquareProfile());
        multi.setInput("input_profiles", bad);
        multi.processNode(null);
        assertEquals(Boolean.FALSE, multi.getOutput("output_valid"));
    }

    @Test
    void pushPullRejectsCollapsingBox() {
        // Box full size Y=10 (halfExtent=5); face 1 = +Y.
        // distance=-10 → newHalf=0 → collapse; distance=-9 → newHalf=0.5 → valid.
        BoxGeometryData box = new BoxGeometryData(new Vector3d(0, 0, 0), new Vector3d(5, 5, 5));

        PushPullBoxFaceNode collapse = new PushPullBoxFaceNode();
        connectInput(collapse, "input_distance", NodeDataType.DOUBLE);
        collapse.setInput("input_box_geometry", box);
        collapse.setInput("input_face_index", 1);
        collapse.setInput("input_distance", -10.0d);
        collapse.processNode(null);
        assertEquals(Boolean.FALSE, collapse.getOutput("output_valid"));
        assertNull(collapse.getOutput("output_box_geometry"));
        assertNull(collapse.getOutput("output_geometry"));
        assertNotNull(collapse.getOutput("output_error"));
        assertFalse(String.valueOf(collapse.getOutput("output_error")).isBlank());
        assertTrue(String.valueOf(collapse.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("collapse") || String.valueOf(collapse.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("invert"));

        PushPullBoxFaceNode keep = new PushPullBoxFaceNode();
        connectInput(keep, "input_distance", NodeDataType.DOUBLE);
        keep.setInput("input_box_geometry", box);
        keep.setInput("input_face_index", 1);
        keep.setInput("input_distance", -9.0d);
        keep.processNode(null);
        assertEquals(Boolean.TRUE, keep.getOutput("output_valid"));
        assertInstanceOf(BoxGeometryData.class, keep.getOutput("output_box_geometry"));
        BoxGeometryData result = (BoxGeometryData) keep.getOutput("output_box_geometry");
        assertEquals(0.5d, result.getHalfExtents().y, 1.0e-9d);
    }

    @Test
    void surfaceStripListTypeExists() {
        assertEquals(NodeDataType.SURFACE_STRIP_LIST, NodeDataType.forListElementKind(
            com.nodecraft.nodesystem.api.ListElementKind.SURFACE_STRIP));
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
