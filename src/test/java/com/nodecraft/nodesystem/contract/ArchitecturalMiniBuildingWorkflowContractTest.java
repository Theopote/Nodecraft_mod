package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.BoxFaceBoundaryPathNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13.1 product acceptance: Floor Slab â†?Wall Along Path â†?Window Array â†?Roof Base â†?Voxelize â†?Preview.
 * <p>
 * Asserts the typed host/placement chain can assemble a small building without ANY,
 * without hidden BlockPos snap, and without world-write side effects in the PURE stage.
 */
class ArchitecturalMiniBuildingWorkflowContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void miniBuildingChainProducesVoxelPreviewableGeometry() {
        // Volume box defines shared footprint / wall / roof faces.
        BaseNode volume = (BaseNode) registry.createNodeInstance("geometry.primitives.box_from_corner_size");
        volume.setInput("input_corner", new Vector3d(0.0d, 0.0d, 0.0d));
        volume.setInput("input_size_x", 10.0d);
        volume.setInput("input_size_y", 3.0d);
        volume.setInput("input_size_z", 8.0d);
        volume.processNode(null);

        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, volume.getOutput("output_box_geometry"));
        assertNotNull(volume.getOutput("output_geometry"));

        BoxFaceData floorFace = requireFace(box, "Bottom");
        BoxFaceData frontFace = requireFace(box, "Front");
        BoxFaceData roofFace = requireFace(box, "Top");

        // Floor Slab â†?BOX_FACE
        FloorSlabNode floor = new FloorSlabNode();
        floor.setInput("input_face", floorFace);
        floor.setInput("input_thickness", 0.3d);
        floor.processNode(null);
        assertEquals(Boolean.TRUE, floor.getOutput("output_valid"));
        GeometryData floorGeom = assertInstanceOf(GeometryData.class, floor.getOutput("output_geometry"));
        assertNotNull(floor.getOutput("output_top_face"));

        // Perimeter PATH â†?face boundary polyline (implicit PATH connect)
        BoxFaceBoundaryPathNode boundary = new BoxFaceBoundaryPathNode();
        boundary.setInput("input_face", floorFace);
        boundary.processNode(null);
        assertEquals(Boolean.TRUE, boundary.getOutput("output_valid"));
        PolylineData perimeter = assertInstanceOf(PolylineData.class, boundary.getOutput("output_polyline"));
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.POLYLINE, NodeDataType.PATH));

        // Walls â†?PATH
        WallAlongPathNode walls = new WallAlongPathNode();
        walls.setInput("input_path", perimeter);
        walls.setInput("input_height", 3.0d);
        walls.setInput("input_thickness", 0.4d);
        walls.processNode(null);
        assertEquals(Boolean.TRUE, walls.getOutput("output_valid"));
        assertTrue(((Number) walls.getOutput("output_count")).intValue() >= 4,
            "closed rectangle should yield 4 wall segments");
        GeometryData wallGeom = assertInstanceOf(GeometryData.class, walls.getOutput("output_geometry"));

        // Windows â†?vertical BOX_FACE
        WindowArrayNode windows = new WindowArrayNode();
        windows.setInput("input_face", frontFace);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.5d);
        windows.setInput("input_window_height", 1.2d);
        windows.setInput("input_margin", 0.4d);
        windows.setInput("input_depth", 0.3d);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"));
        assertEquals(2, windows.getOutput("output_count"));
        assertNotNull(windows.getOutput("output_frames"));
        GeometryData windowGeom = assertInstanceOf(GeometryData.class, windows.getOutput("output_geometry"));

        // Roof Base â†?top BOX_FACE
        RoofBaseNode roof = new RoofBaseNode();
        roof.setInput("input_face", roofFace);
        roof.setInput("input_roof_type", "gable");
        roof.setInput("input_height", 2.0d);
        roof.setInput("input_thickness", 0.3d);
        roof.setInput("input_overhang", 0.4d);
        roof.processNode(null);
        assertEquals(Boolean.TRUE, roof.getOutput("output_valid"));
        GeometryData roofGeom = assertInstanceOf(GeometryData.class, roof.getOutput("output_geometry"));
        assertNotNull(roof.getOutput("output_eave_path"));
        assertNotNull(roof.getOutput("output_ridge_path"));

        // Combine â†?Voxelize (PURE, no world write)
        BaseNode combine = (BaseNode) registry.createNodeInstance("geometry.combine.geometry");
        combine.setInput("input_geometry_0", floorGeom);
        combine.setInput("input_geometry_1", wallGeom);
        combine.setInput("input_geometry_2", windowGeom);
        combine.setInput("input_geometry_3", roofGeom);
        combine.processNode(null);
        assertEquals(Boolean.TRUE, combine.getOutput("output_valid"));
        CompositeGeometryData building = assertInstanceOf(CompositeGeometryData.class, combine.getOutput("output_geometry"));
        assertTrue(building.size() >= 4);

        BaseNode voxelize = (BaseNode) registry.createNodeInstance("geometry.voxel.voxelize_geometry");
        voxelize.setInput("input_geometry", building);
        voxelize.processNode(null);
        BlockPosList blocks = assertInstanceOf(BlockPosList.class, voxelize.getOutput("output_blocks"));
        int blockCount = ((Number) voxelize.getOutput("output_count")).intValue();
        assertTrue(blockCount > 40, "expected a filled mini-building voxel volume, got " + blockCount);
        assertEquals(blockCount, blocks.size());

        // Preview Geometry accepts the combined building (type + runtime, no Apply Changes).
        PreviewGeometryNode preview = (PreviewGeometryNode) registry.createNodeInstance("output.preview.preview_geometry");
        assertEquals(NodeDataType.GEOMETRY, findPort(preview, "input_geometry").getDataType());
        assertTrue(NodeDataType.isConnectableTo(NodeDataType.GEOMETRY, NodeDataType.GEOMETRY));
        preview.setInput("input_geometry", building);
        preview.processNode(null);
        assertNotNull(preview.getOutput("output_success"));
    }

    @Test
    void miniBuildingGraphPortsStayTypedWithoutAny() {
        assertPortType("geometry.architectural_primitives.floor_slab", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.curves.face_boundary_curve", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.curves.face_boundary_curve", "output_polyline", false, NodeDataType.POLYLINE);
        assertPortType("geometry.architectural_primitives.wall_along_path", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.architectural_primitives.window_array", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.architectural_primitives.window_array", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("geometry.architectural_primitives.roof_base", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.architectural_primitives.roof_base", "output_eave_path", false, NodeDataType.PATH);
        assertPortType("geometry.voxel.voxelize_geometry", "output_blocks", false, NodeDataType.BLOCK_LIST);
        assertPortType("output.preview.preview_geometry", "input_geometry", true, NodeDataType.GEOMETRY);

        assertEquals(
            TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.POLYLINE, NodeDataType.PATH)
        );
        assertEquals(
            TypeConversionRegistry.ConversionPolicy.IMPLICIT_SAFE,
            TypeConversionRegistry.classify(NodeDataType.BOX_GEOMETRY, NodeDataType.BOX_GEOMETRY)
        );

        for (String nodeId : List.of(
            "geometry.architectural_primitives.floor_slab",
            "geometry.architectural_primitives.beam_grid",
            "geometry.architectural_primitives.wall_along_path",
            "geometry.architectural_primitives.window_array",
            "geometry.architectural_primitives.roof_base",
            "geometry.curves.face_boundary_curve",
            "geometry.combine.geometry",
            "geometry.voxel.voxelize_geometry"
        )) {
            INode node = registry.createNodeInstance(nodeId);
            for (IPort port : node.getInputPorts()) {
                assertFalse(port.getDataType() == NodeDataType.ANY,
                    nodeId + "#" + port.getId() + " must not be ANY");
            }
            for (IPort port : node.getOutputPorts()) {
                assertFalse(port.getDataType() == NodeDataType.ANY,
                    nodeId + "#" + port.getId() + " must not be ANY");
            }
        }
    }

    @Test
    void getBoxFaceResolvesSemanticFacesForMiniBuildingHosts() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(5.0d, 1.5d, 4.0d),
            new Vector3d(5.0d, 1.5d, 4.0d)
        );
        GetBoxFaceNode getFace = new GetBoxFaceNode();
        connectInput(getFace, "input_face_name", NodeDataType.STRING);
        getFace.setInput("input_box_geometry", box);
        getFace.setInput("input_face_name", "bottom");
        getFace.processNode(null);
        assertEquals(Boolean.TRUE, getFace.getOutput("output_found"));
        assertInstanceOf(BoxFaceData.class, getFace.getOutput("output_face"));

        getFace.setInput("input_face_name", "front");
        getFace.processNode(null);
        assertEquals(Boolean.TRUE, getFace.getOutput("output_found"));
        assertEquals("Front", getFace.getOutput("output_name"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
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

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
            .filter(face -> name.equalsIgnoreCase(face.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing face " + name));
    }

    private static void assertPortType(String typeId, String portId, boolean input, NodeDataType expected) {
        INode node = registry.createNodeInstance(typeId);
        IPort port = input ? findIn(node.getInputPorts(), portId) : findIn(node.getOutputPorts(), portId);
        assertNotNull(port, "missing port " + portId + " on " + typeId);
        assertEquals(expected, port.getDataType(), typeId + "#" + portId);
    }

    private static IPort findPort(INode node, String portId) {
        IPort port = findIn(node.getInputPorts(), portId);
        if (port != null) {
            return port;
        }
        port = findIn(node.getOutputPorts(), portId);
        assertNotNull(port, "missing port " + portId + " on " + node.getTypeId());
        return port;
    }

    private static IPort findIn(List<IPort> ports, String portId) {
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
