package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.solids.ContourNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudePointListNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.LoftPointListsNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.SectionCutNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ShrinkwrapPointsOnSurfaceStripNode;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Geometry Solids / Section Topology v2 (Graph V94).
 */
class GeometrySolidsSectionTopologyV2ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV94() {
        assertEquals(94, GraphFormatVersion.V94);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V94);
    }

    @Test
    void migrateV93ToV94IsNoOp() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V93;
        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
    }

    @Test
    void solidsInventoryRemainsTwentyThree() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.solids."))
            .sorted()
            .toList();
        assertEquals(23, ids.size(), ids.toString());
        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.solids", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
    }

    @Test
    void sectionAndContourExposeRegionPorts() {
        assertPortType("geometry.solids.section_cut", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.solids.section_cut", "output_regions", false, NodeDataType.PLANAR_REGION_LIST);
        assertPortType("geometry.solids.section_cut", "output_regions_tree", false, NodeDataType.DATA_TREE);
        assertPortType("geometry.solids.contour", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.solids.contour", "output_regions", false, NodeDataType.PLANAR_REGION_LIST);
        assertPortType("geometry.solids.contour", "output_regions_tree", false, NodeDataType.DATA_TREE);
    }

    @Test
    void hollowExtrudeRegion_voxelSection_preservesHole() {
        // Hollow box (outer − inner) mid-slice — plan acceptance: region with holeCount == 1
        BoxGeometryData outer = new BoxGeometryData(new Vector3d(0, 2, 0), new Vector3d(5, 2, 5));
        BoxGeometryData inner = new BoxGeometryData(new Vector3d(0, 2, 0), new Vector3d(2, 2.1, 2));
        DifferenceGeometryData hollow = new DifferenceGeometryData(outer, inner);

        SectionCutNode section = new SectionCutNode();
        section.setInput("input_geometry", hollow);
        connectInput(section, "input_plane", NodeDataType.PLANE);
        section.setInput("input_plane", new PlaneData(
            new Vector3d(0.0d, 2.0d, 0.0d),
            new Vector3d(0.0d, 1.0d, 0.0d)
        ));
        connectInput(section, "input_thickness", NodeDataType.DOUBLE);
        section.setInput("input_thickness", 1.0d);
        section.processNode(null);

        assertEquals(Boolean.TRUE, section.getOutput("output_valid"),
            String.valueOf(section.getOutput("output_error")));
        assertNotNull(section.getOutput("output_profile"));
        PlanarRegionData sectionRegion = (PlanarRegionData) section.getOutput("output_region");
        assertNotNull(sectionRegion, "Section should emit a PLANAR_REGION with hole topology");
        assertEquals(1, sectionRegion.holeCount());
    }

    @Test
    void contourConnectedInvalidBasePlane_failsClosed() {
        ContourNode contour = new ContourNode();
        connectInput(contour, "input_base_plane", NodeDataType.PLANE);
        // Connected but no value → washout-free fail
        contour.setInput("input_base_plane", null);
        contour.setInput("input_geometry",
            new com.nodecraft.nodesystem.datatypes.BoxGeometryData(
                new Vector3d(0, 0, 0), new Vector3d(2, 2, 2)));
        contour.processNode(null);

        assertEquals(Boolean.FALSE, contour.getOutput("output_valid"));
        assertNull(contour.getOutput("output_profile"));
        assertNull(contour.getOutput("output_region"));
        String error = String.valueOf(contour.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("plane") || error.contains("base"), error);
    }

    @Test
    void sectionConnectedInvalidPlanesList_explicitError() {
        SectionCutNode section = new SectionCutNode();
        connectInput(section, "input_planes", NodeDataType.PLANE_LIST);
        section.setInput("input_planes", List.of("not-a-plane"));
        section.setInput("input_geometry",
            new com.nodecraft.nodesystem.datatypes.BoxGeometryData(
                new Vector3d(0, 0, 0), new Vector3d(2, 2, 2)));
        section.processNode(null);

        assertEquals(Boolean.FALSE, section.getOutput("output_valid"));
        String error = String.valueOf(section.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("planes list invalid"), error);
    }

    @Test
    void loftPointListIllegalEntry_failsClosedWithoutShortening() {
        LoftPointListsNode loft = new LoftPointListsNode();
        List<Object> mixed = new ArrayList<>();
        mixed.add(new PointData(0, 0, 0));
        mixed.add(new PointData(1, 0, 0));
        mixed.add("not-a-point");
        mixed.add(new PointData(2, 0, 0));
        loft.setInput("input_source_points", mixed);
        loft.setInput("input_target_points", List.of(
            new PointData(0, 1, 0),
            new PointData(1, 1, 0),
            new PointData(2, 1, 0)
        ));
        loft.processNode(null);

        assertEquals(Boolean.FALSE, loft.getOutput("output_valid"));
        String error = String.valueOf(loft.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertTrue(error.contains("point") || error.contains("non-"), error);
    }

    @Test
    void extrudePointListIllegalEntry_failsClosed() {
        ExtrudePointListNode extrude = new ExtrudePointListNode();
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        List<Object> mixed = new ArrayList<>();
        mixed.add(new PointData(0, 0, 0));
        mixed.add(new Vector3d(1, 0, 0)); // raw vector, not PointData
        mixed.add(new PointData(2, 0, 0));
        extrude.setInput("input_points", mixed);
        extrude.setInput("input_direction", new Vector3d(0, 1, 0));
        extrude.processNode(null);

        assertEquals(Boolean.FALSE, extrude.getOutput("output_valid"));
        assertTrue(String.valueOf(extrude.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("point"));
    }

    @Test
    void shrinkwrapEmptyPointList_succeedsEmpty() {
        ShrinkwrapPointsOnSurfaceStripNode node = new ShrinkwrapPointsOnSurfaceStripNode();
        // Need a valid strip — build a minimal one via ExtrudePointList
        ExtrudePointListNode extrude = new ExtrudePointListNode();
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_points", List.of(
            new PointData(0, 0, 0),
            new PointData(2, 0, 0),
            new PointData(2, 0, 2),
            new PointData(0, 0, 2)
        ));
        extrude.setInput("input_direction", new Vector3d(0, 1, 0));
        extrude.processNode(null);
        assertEquals(Boolean.TRUE, extrude.getOutput("output_valid"),
            String.valueOf(extrude.getOutput("output_error")));

        node.setInput("input_surface_strip", extrude.getOutput("output_surface_strip"));
        node.setInput("input_points", List.of());
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"),
            String.valueOf(node.getOutput("output_error")));
        assertEquals(List.of(), node.getOutput("output_points"));
        assertEquals(List.of(), node.getOutput("output_distances"));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        assertNotNull(node, typeId);
        List<? extends IPort> ports = input ? node.getInputPorts() : node.getOutputPorts();
        IPort port = ports.stream().filter(p -> portId.equals(p.getId())).findFirst().orElse(null);
        assertNotNull(port, typeId + " missing port " + portId);
        assertEquals(expected, port.getDataType(), typeId + "." + portId);
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }
}
