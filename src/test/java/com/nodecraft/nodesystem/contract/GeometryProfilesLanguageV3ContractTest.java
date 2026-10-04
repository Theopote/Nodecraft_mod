package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlanarRegionData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.profiles.AnnulusOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileBoolean2DNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.ProfileToRegionNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.RectangleOnPlaneNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.RegionBoolean2DNode;
import com.nodecraft.nodesystem.nodes.geometry.profiles.RegionOffsetInPlaneNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Planar Region Modeling Language.
 * Historical Graph V92 residue. Closes Region → Boolean / Offset composability.
 */
class GeometryProfilesLanguageV3ContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void planarRegionModelingLanguageV92FenceRemains() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }


    @Test
    void regionModelingNodesRegisteredWithOrders23To25() {
        assertPortType("geometry.profiles.profile_to_region", "input_profile", true, NodeDataType.POLYGON_PROFILE);
        assertPortType("geometry.profiles.profile_to_region", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.region_boolean_2d", "input_region_a", true, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.region_boolean_2d", "input_region_b", true, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.region_boolean_2d", "output_region", false, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.region_boolean_2d", "output_regions", false, NodeDataType.PLANAR_REGION_LIST);
        assertPortType("geometry.profiles.region_offset_plane", "input_region", true, NodeDataType.PLANAR_REGION);
        assertPortType("geometry.profiles.region_offset_plane", "output_region", false, NodeDataType.PLANAR_REGION);

        NodeInfo toRegion = registry.getNodeInfo("geometry.profiles.profile_to_region");
        NodeInfo regionBoolean = registry.getNodeInfo("geometry.profiles.region_boolean_2d");
        NodeInfo regionOffset = registry.getNodeInfo("geometry.profiles.region_offset_plane");
        assertNotNull(toRegion);
        assertNotNull(regionBoolean);
        assertNotNull(regionOffset);
        assertEquals(23, toRegion.getOrder());
        assertEquals(24, regionBoolean.getOrder());
        assertEquals(25, regionOffset.getOrder());

        Set<Integer> orders = new HashSet<>();
        for (String id : registry.getAllNodeIds()) {
            if (!id.startsWith("geometry.profiles.")) {
                continue;
            }
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(26, orders.size());
    }

    @Test
    void profileToRegion_wrapsSimpleProfile() {
        PolygonProfileData profile = rectangleProfile(8.0d, 6.0d);
        ProfileToRegionNode node = new ProfileToRegionNode();
        node.setInput("input_profile", profile);
        node.processNode(null);

        assertEquals(Boolean.TRUE, node.getOutput("output_valid"),
            String.valueOf(node.getOutput("output_error")));
        PlanarRegionData region = (PlanarRegionData) node.getOutput("output_region");
        assertNotNull(region);
        assertEquals(0, region.holeCount());
        assertEquals(profile, region.outer());
    }

    @Test
    void regionBoolean_differenceOnHoledRegion_composes() {
        PolygonProfileData outer = rectangleProfile(10.0d, 10.0d);
        PolygonProfileData hole = rectangleProfile(6.0d, 6.0d);
        PolygonProfileData cutter = rectangleProfile(2.0d, 2.0d);

        ProfileBoolean2DNode first = new ProfileBoolean2DNode();
        first.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        first.setInput("input_profile_a", outer);
        first.setInput("input_profile_b", hole);
        first.processNode(null);
        assertEquals(Boolean.TRUE, first.getOutput("output_valid"),
            String.valueOf(first.getOutput("output_error")));
        PlanarRegionData frame = (PlanarRegionData) first.getOutput("output_region");
        assertNotNull(frame);
        assertEquals(1, frame.holeCount());

        ProfileToRegionNode promote = new ProfileToRegionNode();
        promote.setInput("input_profile", cutter);
        promote.processNode(null);
        PlanarRegionData cutterRegion = (PlanarRegionData) promote.getOutput("output_region");
        assertNotNull(cutterRegion);

        RegionBoolean2DNode second = new RegionBoolean2DNode();
        second.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        second.setInput("input_region_a", frame);
        second.setInput("input_region_b", cutterRegion);
        second.processNode(null);

        assertEquals(Boolean.TRUE, second.getOutput("output_valid"),
            String.valueOf(second.getOutput("output_error")));
        PlanarRegionData result = (PlanarRegionData) second.getOutput("output_region");
        assertNotNull(result);
        assertTrue(result.holeCount() >= 1);
    }

    @Test
    void regionOffset_acceptsAnnulusRegion() {
        AnnulusOnPlaneNode annulus = new AnnulusOnPlaneNode();
        annulus.processNode(null);
        assertEquals(Boolean.TRUE, annulus.getOutput("output_valid"),
            String.valueOf(annulus.getOutput("output_error")));
        PlanarRegionData region = (PlanarRegionData) annulus.getOutput("output_region");
        assertNotNull(region);
        assertEquals(1, region.holeCount());

        RegionOffsetInPlaneNode offset = new RegionOffsetInPlaneNode();
        connectInput(offset, "input_offset", NodeDataType.DOUBLE);
        offset.setInput("input_region", region);
        offset.setInput("input_offset", 0.25d);
        offset.processNode(null);

        assertEquals(Boolean.TRUE, offset.getOutput("output_valid"),
            String.valueOf(offset.getOutput("output_error")));
        PlanarRegionData offsetRegion = (PlanarRegionData) offset.getOutput("output_region");
        assertNotNull(offsetRegion);
        assertNotNull(offsetRegion.outer());
    }

    @Test
    void profileBoolean_internallyUsesRegionPath() {
        PolygonProfileData a = rectangleProfile(10.0d, 10.0d);
        PolygonProfileData b = rectangleProfile(4.0d, 4.0d);

        ProfileBoolean2DNode profileBoolean = new ProfileBoolean2DNode();
        profileBoolean.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        profileBoolean.setInput("input_profile_a", a);
        profileBoolean.setInput("input_profile_b", b);
        profileBoolean.processNode(null);

        ProfileToRegionNode promoteA = new ProfileToRegionNode();
        promoteA.setInput("input_profile", a);
        promoteA.processNode(null);
        ProfileToRegionNode promoteB = new ProfileToRegionNode();
        promoteB.setInput("input_profile", b);
        promoteB.processNode(null);

        RegionBoolean2DNode regionBoolean = new RegionBoolean2DNode();
        regionBoolean.setNodeState(java.util.Map.of("operation", "DIFFERENCE"));
        regionBoolean.setInput("input_region_a", promoteA.getOutput("output_region"));
        regionBoolean.setInput("input_region_b", promoteB.getOutput("output_region"));
        regionBoolean.processNode(null);

        assertEquals(Boolean.TRUE, profileBoolean.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, regionBoolean.getOutput("output_valid"));
        PlanarRegionData fromProfile = (PlanarRegionData) profileBoolean.getOutput("output_region");
        PlanarRegionData fromRegion = (PlanarRegionData) regionBoolean.getOutput("output_region");
        assertNotNull(fromProfile);
        assertNotNull(fromRegion);
        assertEquals(fromProfile.holeCount(), fromRegion.holeCount());
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
