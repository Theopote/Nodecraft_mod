package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.nodes.geometry.primitives.CapsuleByAxisRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.CylinderByAxisRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.DeconstructCapsuleNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.DeconstructCylinderNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.DeconstructSphereNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.DeconstructTorusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.SphereByCenterRadiusNode;
import com.nodecraft.nodesystem.nodes.geometry.primitives.TorusByCenterAxisRadiiNode;
import com.nodecraft.nodesystem.nodes.transform.basic_transforms.RotateGeometryAroundAxisNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import com.nodecraft.nodesystem.util.GeometryBoundsResolver;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Language fence for Primitive Geometry Language v2 (Graph V90).
 */
class GeometryPrimitivesLanguageV2ContractTest {

    private static final double EPS = 1e-6d;

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV90() {
        assertEquals(90, GraphFormatVersion.V90);
        assertEquals(GraphFormatVersion.V90, GraphFormatVersion.CURRENT);
    }

    @Test
    void migrateV89ToV90IsNoOp() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V89;
        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V90, migrated.formatVersion);
    }

    @Test
    void exactlyThirtyOnePrimitiveNodesWithUniqueOrdersZeroToThirty() {
        List<String> ids = registry.getAllNodeIds().stream()
            .filter(id -> id.startsWith("geometry.primitives."))
            .sorted()
            .toList();
        assertEquals(31, ids.size());

        Set<Integer> orders = new HashSet<>();
        for (String id : ids) {
            NodeInfo info = registry.getNodeInfo(id);
            assertNotNull(info);
            assertEquals("geometry.primitives", info.getCategoryId());
            assertTrue(orders.add(info.getOrder()), "duplicate order for " + id);
            INode node = registry.createNodeInstance(id);
            assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(node.getClass(), id));
        }
        assertEquals(31, orders.size());
        for (int i = 0; i < 31; i++) {
            assertTrue(orders.contains(i), "missing order " + i);
        }
    }

    @Test
    void v90AddsDeconstructTorusAndCapsuleAtOrdersTwentyNineAndThirty() {
        assertOrder("geometry.primitives.deconstruct_torus", 29);
        assertOrder("geometry.primitives.deconstruct_capsule", 30);
    }

    @Test
    void allPrimitiveNodesHaveValidAndErrorWithoutBannedPortTypes() {
        List<String> errors = new ArrayList<>();
        for (String id : registry.getAllNodeIds().stream()
            .filter(s -> s.startsWith("geometry.primitives."))
            .toList()) {
            INode node = registry.createNodeInstance(id);
            if (!hasPort(node, "output_valid")) {
                errors.add(id + " missing output_valid");
            }
            if (!hasPort(node, "output_error")) {
                errors.add(id + " missing output_error");
            }
            for (IPort port : node.getInputPorts()) {
                banPortType(errors, id, port);
            }
            for (IPort port : node.getOutputPorts()) {
                banPortType(errors, id, port);
            }
        }
        assertTrue(errors.isEmpty(), String.join(System.lineSeparator(), errors));
    }

    @Test
    void deconstructSphere_smallRadius_continuousBoundsNotVoxelized() {
        SphereData sphere = new SphereData(new Vector3d(0.2d, 0.2d, 0.2d), 0.1d);
        DeconstructSphereNode deconstruct = new DeconstructSphereNode();
        deconstruct.setInput("input_sphere", sphere);
        deconstruct.processNode(null);

        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        BoundingBoxData box = (BoundingBoxData) deconstruct.getOutput("output_bounding_box");
        assertNotNull(box);
        assertEquals(0.1d, box.getMin().x, EPS);
        assertEquals(0.1d, box.getMin().y, EPS);
        assertEquals(0.1d, box.getMin().z, EPS);
        assertEquals(0.3d, box.getMax().x, EPS);
        assertEquals(0.3d, box.getMax().y, EPS);
        assertEquals(0.3d, box.getMax().z, EPS);

        RegionData voxelRegion = GeometryVoxelizer.createBoundingRegion(sphere);
        BoundingBoxData voxelBox = GeometryVoxelizer.createBoundingBox(voxelRegion);
        assertNotNull(voxelBox);
        assertNotEquals(box.getMin().x, voxelBox.getMin().x, 1e-3d);
    }

    @Test
    void deconstructCylinder_smallRadius_continuousBoundsNotInflated() {
        CylinderGeometryData cylinder = new CylinderGeometryData(
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(0.0d, 5.0d, 0.0d),
            0.2d
        );
        DeconstructCylinderNode deconstruct = new DeconstructCylinderNode();
        deconstruct.setInput("input_cylinder", cylinder);
        deconstruct.processNode(null);

        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        BoundingBoxData box = (BoundingBoxData) deconstruct.getOutput("output_bounding_box");
        assertNotNull(box);
        assertEquals(-0.2d, box.getMin().x, EPS);
        assertEquals(-0.2d, box.getMin().y, EPS);
        assertEquals(-0.2d, box.getMin().z, EPS);
        assertEquals(0.2d, box.getMax().x, EPS);
        assertEquals(5.2d, box.getMax().y, EPS);
        assertEquals(0.2d, box.getMax().z, EPS);

        RegionData voxelRegion = GeometryVoxelizer.createBoundingRegion(cylinder);
        BoundingBoxData voxelBox = GeometryVoxelizer.createBoundingBox(voxelRegion);
        assertNotNull(voxelBox);
        assertTrue(voxelBox.getMin().x <= -0.9d, "voxelizer inflates small-radius cylinder bounds");
    }

    @Test
    void deconstructRegion_derivedFromContinuousBoundsNotVoxelizerRegion() {
        CylinderGeometryData cylinder = new CylinderGeometryData(
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(0.0d, 5.0d, 0.0d),
            0.2d
        );
        DeconstructCylinderNode deconstruct = new DeconstructCylinderNode();
        deconstruct.setInput("input_cylinder", cylinder);
        deconstruct.processNode(null);

        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        BoundingBoxData box = (BoundingBoxData) deconstruct.getOutput("output_bounding_box");
        RegionData region = (RegionData) deconstruct.getOutput("output_region");
        assertNotNull(box);
        assertNotNull(region);
        assertTrue(region.isComplete());

        RegionData expectedRegion = BoxBlockGenerator.regionFromBoundingBox(box);
        assertEquals(expectedRegion.corner1(), region.corner1());
        assertEquals(expectedRegion.corner2(), region.corner2());

        RegionData voxelRegion = GeometryVoxelizer.createBoundingRegion(cylinder);
        BoundingBoxData voxelBox = GeometryVoxelizer.createBoundingBox(voxelRegion);
        assertNotNull(voxelBox);
        assertTrue(Math.abs(box.getMin().x - voxelBox.getMin().x) > 0.5d,
            "Region must come from continuous bounds, not voxelizer-inflated region");
    }

    @Test
    void sphereConstructDeconstructRoundTrip() {
        SphereByCenterRadiusNode construct = new SphereByCenterRadiusNode();
        connectInput(construct, "input_center", NodeDataType.POINT);
        connectInput(construct, "input_radius", NodeDataType.DOUBLE);
        construct.setInput("input_center", new PointData(1.0d, 2.0d, 3.0d));
        construct.setInput("input_radius", 4.0d);
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));

        DeconstructSphereNode deconstruct = new DeconstructSphereNode();
        deconstruct.setInput("input_sphere", construct.getOutput("output_sphere"));
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertEquals(4.0d, (Double) deconstruct.getOutput("output_radius"), EPS);
        PointData center = (PointData) deconstruct.getOutput("output_center");
        assertEquals(1.0d, center.getX(), EPS);
        assertEquals(2.0d, center.getY(), EPS);
        assertEquals(3.0d, center.getZ(), EPS);
    }

    @Test
    void cylinderConstructDeconstructRoundTrip() {
        CylinderByAxisRadiusNode construct = new CylinderByAxisRadiusNode();
        connectInput(construct, "input_start", NodeDataType.POINT);
        connectInput(construct, "input_end", NodeDataType.POINT);
        connectInput(construct, "input_radius", NodeDataType.DOUBLE);
        construct.setInput("input_start", new PointData(0.0d, 0.0d, 0.0d));
        construct.setInput("input_end", new PointData(0.0d, 8.0d, 0.0d));
        construct.setInput("input_radius", 2.5d);
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));

        DeconstructCylinderNode deconstruct = new DeconstructCylinderNode();
        deconstruct.setInput("input_cylinder", construct.getOutput("output_cylinder"));
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertEquals(8.0d, (Double) deconstruct.getOutput("output_height"), EPS);
        assertEquals(2.5d, (Double) deconstruct.getOutput("output_radius"), EPS);
    }

    @Test
    void torusConstructDeconstructRoundTrip() {
        TorusByCenterAxisRadiiNode construct = new TorusByCenterAxisRadiiNode();
        connectInput(construct, "input_major_radius", NodeDataType.DOUBLE);
        connectInput(construct, "input_minor_radius", NodeDataType.DOUBLE);
        construct.setInput("input_major_radius", 5.0d);
        construct.setInput("input_minor_radius", 1.0d);
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));

        DeconstructTorusNode deconstruct = new DeconstructTorusNode();
        deconstruct.setInput("input_torus", construct.getOutput("output_torus"));
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertEquals(5.0d, (Double) deconstruct.getOutput("output_major_radius"), EPS);
        assertEquals(1.0d, (Double) deconstruct.getOutput("output_minor_radius"), EPS);
    }

    @Test
    void capsuleConstructDeconstructRoundTrip() {
        CapsuleByAxisRadiusNode construct = new CapsuleByAxisRadiusNode();
        connectInput(construct, "input_start", NodeDataType.POINT);
        connectInput(construct, "input_end", NodeDataType.POINT);
        connectInput(construct, "input_radius", NodeDataType.DOUBLE);
        construct.setInput("input_start", new PointData(0.0d, 0.0d, 0.0d));
        construct.setInput("input_end", new PointData(0.0d, 6.0d, 0.0d));
        construct.setInput("input_radius", 1.5d);
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));

        DeconstructCapsuleNode deconstruct = new DeconstructCapsuleNode();
        deconstruct.setInput("input_geometry", construct.getOutput("output_geometry"));
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertEquals(6.0d, (Double) deconstruct.getOutput("output_axis_length"), EPS);
        assertEquals(1.5d, (Double) deconstruct.getOutput("output_radius"), EPS);
        assertEquals(9.0d, (Double) deconstruct.getOutput("output_total_length"), EPS);
        assertNotNull(deconstruct.getOutput("output_cylinder"));
        assertNotNull(deconstruct.getOutput("output_start_hemisphere"));
        assertNotNull(deconstruct.getOutput("output_end_hemisphere"));
    }

    @Test
    void plainCylinderIsNotAcceptedAsCapsule() {
        CylinderGeometryData cylinder = new CylinderGeometryData(
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(0.0d, 5.0d, 0.0d),
            1.0d
        );

        DeconstructCapsuleNode node = new DeconstructCapsuleNode();
        node.setInput("input_geometry", cylinder);
        node.processNode(null);

        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertNull(node.getOutput("output_cylinder"));
        assertNull(node.getOutput("output_start_hemisphere"));
        assertNull(node.getOutput("output_end_hemisphere"));
    }

    @Test
    void rotatedCylinderDeconstructPreservesHeightAndRadius() {
        CylinderByAxisRadiusNode construct = new CylinderByAxisRadiusNode();
        connectInput(construct, "input_start", NodeDataType.POINT);
        connectInput(construct, "input_end", NodeDataType.POINT);
        connectInput(construct, "input_radius", NodeDataType.DOUBLE);
        construct.setInput("input_start", new PointData(0.0d, 0.0d, 0.0d));
        construct.setInput("input_end", new PointData(0.0d, 5.0d, 0.0d));
        construct.setInput("input_radius", 1.0d);
        construct.processNode(null);
        assertEquals(Boolean.TRUE, construct.getOutput("output_valid"));

        RotateGeometryAroundAxisNode rotate = new RotateGeometryAroundAxisNode();
        connectInput(rotate, "input_geometry", NodeDataType.GEOMETRY);
        connectInput(rotate, "input_center", NodeDataType.POINT);
        connectInput(rotate, "input_axis", NodeDataType.VECTOR);
        connectInput(rotate, "input_angle", NodeDataType.DOUBLE);
        rotate.setInput("input_geometry", construct.getOutput("output_geometry"));
        rotate.setInput("input_center", new PointData(0.0d, 0.0d, 0.0d));
        rotate.setInput("input_axis", new Vector3d(0.0d, 0.0d, 1.0d));
        rotate.setInput("input_angle", 90.0d);
        rotate.processNode(null);
        assertEquals(Boolean.TRUE, rotate.getOutput("output_valid"));

        DeconstructCylinderNode deconstruct = new DeconstructCylinderNode();
        deconstruct.setInput("input_cylinder", rotate.getOutput("output_geometry"));
        deconstruct.processNode(null);
        assertEquals(Boolean.TRUE, deconstruct.getOutput("output_valid"));
        assertEquals(5.0d, (Double) deconstruct.getOutput("output_height"), EPS);
        assertEquals(1.0d, (Double) deconstruct.getOutput("output_radius"), EPS);

        PointData start = (PointData) deconstruct.getOutput("output_start");
        PointData end = (PointData) deconstruct.getOutput("output_end");
        assertEquals(0.0d, start.getX(), EPS);
        assertEquals(0.0d, start.getY(), EPS);
        assertEquals(0.0d, start.getZ(), EPS);
        assertEquals(-5.0d, end.getX(), EPS);
        assertEquals(0.0d, end.getY(), EPS);
        assertEquals(0.0d, end.getZ(), EPS);

        GeometryData rotated = (GeometryData) rotate.getOutput("output_geometry");
        BoundingBoxData rotatedBounds = GeometryBoundsResolver.resolve(rotated);
        BoundingBoxData deconstructBounds = (BoundingBoxData) deconstruct.getOutput("output_bounding_box");
        assertNotNull(rotatedBounds);
        assertNotNull(deconstructBounds);
        assertEquals(rotatedBounds.getMin().x, deconstructBounds.getMin().x, EPS);
        assertEquals(rotatedBounds.getMin().y, deconstructBounds.getMin().y, EPS);
        assertEquals(rotatedBounds.getMax().x, deconstructBounds.getMax().x, EPS);
        assertEquals(rotatedBounds.getMax().y, deconstructBounds.getMax().y, EPS);
    }

    private static void assertOrder(String typeId, int expected) {
        NodeInfo info = registry.getNodeInfo(typeId);
        assertNotNull(info, typeId);
        assertEquals(expected, info.getOrder(), typeId);
    }

    private static void banPortType(List<String> errors, String id, IPort port) {
        if (port.getDataType() == NodeDataType.ANY) {
            errors.add(id + "." + port.getId() + " is ANY");
        }
        if (port.getDataType() == NodeDataType.LIST) {
            errors.add(id + "." + port.getId() + " uses raw LIST");
        }
        if (port.getDataType() == NodeDataType.LINE) {
            errors.add(id + "." + port.getId() + " uses graph LINE");
        }
        if (port.getDataType() == NodeDataType.POLYLINE) {
            errors.add(id + "." + port.getId() + " uses graph POLYLINE");
        }
    }

    private static boolean hasPort(INode node, String portId) {
        return node.getInputPorts().stream().anyMatch(p -> portId.equals(p.getId()))
            || node.getOutputPorts().stream().anyMatch(p -> portId.equals(p.getId()));
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
