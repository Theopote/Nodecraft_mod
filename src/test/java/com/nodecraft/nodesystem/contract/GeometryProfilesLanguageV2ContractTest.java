package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.profiles.AnnularSectorOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.AnnulusOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileBoolean2DNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileOffsetInPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.RectangleOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.SectorOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.solids.ExtrudeRegionNode;
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
 * Language fence for Planar Region Language v2.
 * Historical Graph V91 residue. {@link GraphFormatVersion#CURRENT} is stamp-only.
 */
class GeometryProfilesLanguageV2ContractTest {

    private static final double EPS = 1e-3d;

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void planarRegionLanguageV91FenceRemains() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }


    @Test
    void planarRegionTypesExist() {
        assertEquals(NodeDataType.PLANAR_REGION_LIST, NodeDataType.forListElementKind(
            com.nodecraft.nodesystem.api.ListElementKind.PLANAR_REGION));
        assertEquals(NodeDataType.PLANAR_REGION,
            NodeDataType.elementTypeForKind(com.nodecraft.nodesystem.api.ListElementKind.PLANAR_REGION));
        assertTrue(NodeDataType.PLANAR_REGION.isCompatible(PlanarRegionData.of(
            rectangleProfile(10.0d, 10.0d))));
    }

    @Test
    void exactlyTwentySixProfileNodesRemain() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.profiles."))
            .sorted()
            .toList();
        assertEquals(26, ids.size());

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.profiles", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        for (int i = 0; i < 26; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void booleanAndOffsetExposePlanarRegionPorts() {
        assertPortType("geometry.profiles.boolean_2d", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.boolean_2d", "output_regions", false, NodeDataType.PLANAR_REGION_LIST);
        assertPortType("geometry.profiles.offset_profile_plane", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.annulus_profile", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.solids.extrude_region", "input_region", true, NodeDataType.PLANAR_REGION);
    }

    @Test
    void booleanDifference_producesRegionWithHole() {
        PolygonProfileData outer = rectangleProfile(10.0d, 10.0d);
        PolygonProfileData inner = rectangleProfile(6.0d, 6.0d);

        ProfileBoolean2DNode booleanNode = new ProfileBoolean2DNode();
        booleanNode.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        booleanNode.setInput("input_profile_a", outer);
        booleanNode.setInput("input_profile_b", inner);
        booleanNode.processNode(null);

        assertEquals(Boolean.TRUE, booleanNode.getOutput("output_valid"),
            String.valueOf(booleanNode.getOutput("output_error")));
        PlanarRegionData region = (PlanarRegionData) booleanNode.getOutput("output_region");
        assertNotNull(region);
        assertEquals(1, region.holeCount());
        assertEquals(1, booleanNode.getOutput("output_count"));
    }

    @Test
    void booleanDifference_sameProfile_emptyRegion() {
        PolygonProfileData profile = rectangleProfile(5.0d, 5.0d);
        ProfileBoolean2DNode booleanNode = new ProfileBoolean2DNode();
        booleanNode.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        booleanNode.setInput("input_profile_a", profile);
        booleanNode.setInput("input_profile_b", profile);
        booleanNode.processNode(null);

        assertEquals(Boolean.TRUE, booleanNode.getOutput("output_valid"));
        assertNull(booleanNode.getOutput("output_region"));
        assertEquals(0, booleanNode.getOutput("output_count"));
    }

    @Test
    void annulus_outputsCanonicalRegion() {
        AnnulusOnPlaneNode annulus = new AnnulusOnPlaneNode();
        annulus.processNode(null);
        assertEquals(Boolean.TRUE, annulus.getOutput("output_valid"),
            String.valueOf(annulus.getOutput("output_error")));
        PlanarRegionData region = (PlanarRegionData) annulus.getOutput("output_region");
        assertNotNull(region);
        assertEquals(1, region.holeCount());
        double area = (Double) annulus.getOutput("output_area");
        assertEquals(Math.PI * (25.0d - 4.0d), area, 1e-6d);
    }

    @Test
    void offsetNegative_createsHoledRegionOrSimpleShrink() {
        PolygonProfileData profile = rectangleProfile(10.0d, 10.0d);
        ProfileOffsetInPlaneNode offset = new ProfileOffsetInPlaneNode();
        connectInput(offset, "input_offset", NodeDataType.DOUBLE);
        offset.setInput("input_profile", profile);
        offset.setInput("input_offset", -1.0d);
        offset.processNode(null);

        assertEquals(Boolean.TRUE, offset.getOutput("output_valid"),
            String.valueOf(offset.getOutput("output_error")));
        PlanarRegionData region = (PlanarRegionData) offset.getOutput("output_region");
        assertNotNull(region);
        // Negative offset on a simple rectangle typically shrinks without holes;
        // either empty-hole region or holed topology is acceptable as long as Valid.
        assertNotNull(region.outer());
    }

    @Test
    void sectorSweep720Degrees_failsClosed() {
        SectorOnPlaneNode sector = new SectorOnPlaneNode();
        connectInput(sector, "input_start_angle", NodeDataType.DOUBLE);
        connectInput(sector, "input_end_angle", NodeDataType.DOUBLE);
        sector.setInput("input_start_angle", 0.0d);
        sector.setInput("input_end_angle", 720.0d);
        sector.processNode(null);
        assertEquals(Boolean.FALSE, sector.getOutput("output_valid"));
        assertNull(sector.getOutput("output_profile"));
        assertTrue(String.valueOf(sector.getOutput("output_error")).toLowerCase(Locale.ROOT)
            .contains("360"));
    }

    @Test
    void sectorSweep350Degrees_succeeds() {
        SectorOnPlaneNode sector = new SectorOnPlaneNode();
        connectInput(sector, "input_start_angle", NodeDataType.DOUBLE);
        connectInput(sector, "input_end_angle", NodeDataType.DOUBLE);
        sector.setInput("input_start_angle", 0.0d);
        sector.setInput("input_end_angle", 350.0d);
        sector.processNode(null);
        assertEquals(Boolean.TRUE, sector.getOutput("output_valid"),
            String.valueOf(sector.getOutput("output_error")));
        assertNotNull(sector.getOutput("output_profile"));
    }

    @Test
    void annularSectorSweep360_failsClosed() {
        AnnularSectorOnPlaneNode sector = new AnnularSectorOnPlaneNode();
        connectInput(sector, "input_start_angle", NodeDataType.DOUBLE);
        connectInput(sector, "input_end_angle", NodeDataType.DOUBLE);
        sector.setInput("input_start_angle", 0.0d);
        sector.setInput("input_end_angle", 360.0d);
        sector.processNode(null);
        assertEquals(Boolean.FALSE, sector.getOutput("output_valid"));
        assertNull(sector.getOutput("output_profile"));
    }

    @Test
    void sectorSelfIntersectingSweep_noUncaughtException() {
        SectorOnPlaneNode sector = new SectorOnPlaneNode();
        connectInput(sector, "input_start_angle", NodeDataType.DOUBLE);
        connectInput(sector, "input_end_angle", NodeDataType.DOUBLE);
        sector.setInput("input_start_angle", 0.0d);
        sector.setInput("input_end_angle", 720.0d);
        sector.processNode(null);
        assertEquals(Boolean.FALSE, sector.getOutput("output_valid"));
    }

    @Test
    void annulusExtrudeRegion_producesValidGeometry() {
        AnnulusOnPlaneNode annulus = new AnnulusOnPlaneNode();
        annulus.processNode(null);
        assertEquals(Boolean.TRUE, annulus.getOutput("output_valid"));
        PlanarRegionData region = (PlanarRegionData) annulus.getOutput("output_region");

        ExtrudeRegionNode extrude = new ExtrudeRegionNode();
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_region", region);
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0.0d, 2.0d, 0.0d));
        extrude.processNode(null);

        assertEquals(Boolean.TRUE, extrude.getOutput("output_valid"),
            String.valueOf(extrude.getOutput("output_error")));
        assertInstanceOf(DifferenceGeometryData.class, extrude.getOutput("output_geometry"));
        assertEquals(2.0d, (Double) extrude.getOutput("output_height"), EPS);
    }

    @Test
    void frameProfileExtrudeRegion_producesDifference() {
        PolygonProfileData outer = rectangleProfile(10.0d, 10.0d);
        PolygonProfileData inner = rectangleProfile(6.0d, 6.0d);

        ProfileBoolean2DNode booleanNode = new ProfileBoolean2DNode();
        booleanNode.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        booleanNode.setInput("input_profile_a", outer);
        booleanNode.setInput("input_profile_b", inner);
        booleanNode.processNode(null);
        assertEquals(Boolean.TRUE, booleanNode.getOutput("output_valid"));
        PlanarRegionData region = (PlanarRegionData) booleanNode.getOutput("output_region");
        assertNotNull(region);

        ExtrudeRegionNode extrude = new ExtrudeRegionNode();
        connectInput(extrude, "input_direction", NodeDataType.VECTOR);
        extrude.setInput("input_region", region);
        extrude.setInput("input_direction", new com.nodecraft.nodesystem.datatypes.VectorData(0.0d, 0.5d, 0.0d));
        extrude.processNode(null);

        assertEquals(Boolean.TRUE, extrude.getOutput("output_valid"),
            String.valueOf(extrude.getOutput("output_error")));
        assertInstanceOf(DifferenceGeometryData.class, extrude.getOutput("output_geometry"));
    }

    private static PolygonProfileData rectangleProfile(double width, double height) {
        RectangleOnPlaneNode rect = new RectangleOnPlaneNode();
        connectInput(rect, "input_width", NodeDataType.DOUBLE);
        connectInput(rect, "input_height", NodeDataType.DOUBLE);
        rect.setInput("input_width", width);
        rect.setInput("input_height", height);
        rect.processNode(null);
        assertEquals(Boolean.TRUE, rect.getOutput("output_valid"),
            String.valueOf(rect.getOutput("output_error")));
        return (PolygonProfileData) rect.getOutput("output_profile");
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
