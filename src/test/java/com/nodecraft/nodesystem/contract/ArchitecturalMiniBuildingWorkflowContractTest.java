package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.TypeConversionRegistry;
import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.FloorSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RoofBaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowFrameNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.BoxFaceBoundaryPathNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.nodes.reference.points.GetBoxFaceNode;
import com.nodecraft.nodesystem.nodes.transform.placement.PlaceGeometryOnFramesNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13.1 product acceptance: Floor Slab → Wall Along Path → Window Array → Difference →
 * Window Frame → Place On Frames → Roof Base → Voxelize → Preview.
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
        BaseNode volume = (BaseNode) registry.createNodeInstance("geometry.primitives.box_from_corner_size");
        volume.setNodeState(java.util.Map.of(
            "cornerX", 0.0d,
            "cornerY", 0.0d,
            "cornerZ", 0.0d,
            "sizeX", 10.0d,
            "sizeY", 3.0d,
            "sizeZ", 8.0d
        ));
        volume.processNode(null);

        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, volume.getOutput("output_box_geometry"));
        BoxFaceData floorFace = requireFace(box, "Bottom");
        BoxFaceData roofFace = requireFace(box, "Top");

        // Dedicated thin wall volume: WallWithOpenings host slab on +outward side of Front.
        BaseNode frontVolume = (BaseNode) registry.createNodeInstance("geometry.primitives.box_from_corner_size");
        frontVolume.setNodeState(java.util.Map.of(
            "cornerX", 0.0d,
            "cornerY", 0.0d,
            "cornerZ", 8.0d,
            "sizeX", 10.0d,
            "sizeY", 3.0d,
            "sizeZ", 0.5d
        ));
        frontVolume.processNode(null);
        BoxGeometryData frontBox = assertInstanceOf(BoxGeometryData.class, frontVolume.getOutput("output_box_geometry"));
        BoxFaceData frontFace = requireFace(frontBox, "Front");
        Vector3d faceNormal = frontFace.getNormal();

        FloorSlabNode floor = new FloorSlabNode();
        floor.setInput("input_face", floorFace);
        floor.setInput("input_thickness", 0.3d);
        floor.processNode(null);
        assertEquals(Boolean.TRUE, floor.getOutput("output_valid"));
        GeometryData floorGeom = assertInstanceOf(GeometryData.class, floor.getOutput("output_geometry"));

        BoxFaceBoundaryPathNode boundary = new BoxFaceBoundaryPathNode();
        boundary.setInput("input_face", floorFace);
        boundary.processNode(null);
        PathData perimeter = assertInstanceOf(PathData.class, boundary.getOutput("output_path"));

        WallAlongPathNode walls = new WallAlongPathNode();
        walls.setInput("input_path", perimeter);
        walls.setInput("input_height", 3.0d);
        walls.setInput("input_thickness", 0.4d);
        walls.processNode(null);
        assertEquals(Boolean.TRUE, walls.getOutput("output_valid"));
        GeometryData perimeterWalls = assertInstanceOf(GeometryData.class, walls.getOutput("output_geometry"));

        // Front host wall occupies +outward normal from the face (WallWithOpenings host-side rule).
        // Window Array cutters are centered on the same face plane.
        WallWithOpeningsNode frontWall = new WallWithOpeningsNode();
        frontWall.setInput("input_face", frontFace);
        frontWall.setInput("input_columns", 1);
        frontWall.setInput("input_rows", 1);
        frontWall.setInput("input_wall_thickness", 0.4d);
        frontWall.setInput("input_opening_width", 0.5d);
        frontWall.setInput("input_opening_height", 0.5d);
        frontWall.setInput("input_margin", 0.4d);
        frontWall.processNode(null);
        assertEquals(Boolean.TRUE, frontWall.getOutput("output_valid"));
        GeometryData frontWallGeom = assertInstanceOf(GeometryData.class, frontWall.getOutput("output_geometry"));

        WindowArrayProbe windows = new WindowArrayProbe();
        windows.connectInput("input_columns", NodeDataType.INTEGER);
        windows.connectInput("input_rows", NodeDataType.INTEGER);
        windows.connectInput("input_window_width", NodeDataType.DOUBLE);
        windows.connectInput("input_window_height", NodeDataType.DOUBLE);
        windows.connectInput("input_margin", NodeDataType.DOUBLE);
        windows.connectInput("input_depth", NodeDataType.DOUBLE);
        windows.setInput("input_face", frontFace);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.5d);
        windows.setInput("input_window_height", 1.2d);
        windows.setInput("input_margin", 0.4d);
        windows.setInput("input_depth", 0.8d);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"));
        assertEquals(2, windows.getOutput("output_count"));
        GeometryData openings = assertInstanceOf(GeometryData.class, windows.getOutput("output_openings"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", frontWallGeom);
        difference.setInput("input_cutter", openings);
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));
        GeometryData cutFrontWall = assertInstanceOf(GeometryData.class, difference.getOutput("output_geometry"));

        WindowFrameProbe windowFrame = new WindowFrameProbe();
        windowFrame.connectInput("input_frame_width", NodeDataType.DOUBLE);
        windowFrame.connectInput("input_frame_height", NodeDataType.DOUBLE);
        windowFrame.connectInput("input_frame_thickness", NodeDataType.DOUBLE);
        windowFrame.connectInput("input_depth", NodeDataType.DOUBLE);
        windowFrame.setInput("input_frame_width", 1.5d);
        windowFrame.setInput("input_frame_height", 1.2d);
        windowFrame.setInput("input_frame_thickness", 0.1d);
        windowFrame.setInput("input_depth", 0.15d);
        windowFrame.processNode(null);

        PlaceFramesProbe placeFrames = new PlaceFramesProbe();
        placeFrames.connectInput("input_frames", NodeDataType.FRAME_LIST);
        placeFrames.setInput("input_geometry", windowFrame.getOutput("output_geometry"));
        placeFrames.setInput("input_frames", windows.getOutput("output_frames"));
        placeFrames.processNode(null);
        assertEquals(Boolean.TRUE, placeFrames.getOutput("output_valid"));
        GeometryData frameInstances = assertInstanceOf(GeometryData.class, placeFrames.getOutput("output_geometry"));

        RoofBaseProbe roof = new RoofBaseProbe();
        roof.connectInput("input_roof_type", NodeDataType.STRING);
        roof.connectInput("input_height", NodeDataType.DOUBLE);
        roof.connectInput("input_thickness", NodeDataType.DOUBLE);
        roof.connectInput("input_overhang", NodeDataType.DOUBLE);
        roof.setInput("input_face", roofFace);
        roof.setInput("input_roof_type", "gable");
        roof.setInput("input_height", 2.0d);
        roof.setInput("input_thickness", 0.3d);
        roof.setInput("input_overhang", 0.4d);
        roof.processNode(null);
        GeometryData roofGeom = assertInstanceOf(GeometryData.class, roof.getOutput("output_geometry"));

        BaseNode combine = (BaseNode) registry.createNodeInstance("geometry.combine.geometry");
        combine.setNodeState(java.util.Map.of("inputCount", 5));
        connectInput(combine, "input_geometry_0", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_1", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_2", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_3", NodeDataType.GEOMETRY);
        connectInput(combine, "input_geometry_4", NodeDataType.GEOMETRY);
        combine.setInput("input_geometry_0", floorGeom);
        combine.setInput("input_geometry_1", perimeterWalls);
        combine.setInput("input_geometry_2", cutFrontWall);
        combine.setInput("input_geometry_3", frameInstances);
        combine.setInput("input_geometry_4", roofGeom);
        combine.processNode(null);
        CompositeGeometryData building = assertInstanceOf(CompositeGeometryData.class, combine.getOutput("output_geometry"));

        BaseNode voxelize = (BaseNode) registry.createNodeInstance("geometry.voxel.voxelize_geometry");
        voxelize.setInput("input_geometry", building);
        voxelize.processNode(null);
        BlockPosList blocks = assertInstanceOf(BlockPosList.class, voxelize.getOutput("output_blocks"));
        assertTrue(blocks.size() > 40);

        BlockPosList solidFront = GeometryVoxelizer.voxelize(frontWallGeom, true);
        BlockPosList cutFrontBlocks = GeometryVoxelizer.voxelize(cutFrontWall, true);
        Set<net.minecraft.util.math.BlockPos> cutSolid = ArchitecturalVoxelAssert.toSolidSet(cutFrontBlocks);
        Set<net.minecraft.util.math.BlockPos> fullSolid = ArchitecturalVoxelAssert.toSolidSet(solidFront);
        ArchitecturalVoxelAssert.assertFewerBlocksThan(cutSolid, fullSolid);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(cutSolid, centers);
        ArchitecturalVoxelAssert.assertPierBetweenWindowsHasBlock(cutSolid, centers.get(0), centers.get(1), faceNormal);
        // Sill/lintel covered by WallWithWindowsWorkflowContractTest.

        PreviewGeometryNode preview = (PreviewGeometryNode) registry.createNodeInstance("output.preview.preview_geometry");
        preview.setInput("input_geometry", building);
        preview.processNode(null);
        assertNotNull(preview.getOutput("output_success"));
    }

    @Test
    void miniBuildingGraphPortsStayTypedWithoutAny() {
        assertPortType("geometry.architectural_primitives.floor_slab", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.curves.face_boundary_curve", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.curves.face_boundary_curve", "output_path", false, NodeDataType.PATH);
        assertPortType("geometry.architectural_primitives.wall_along_path", "input_path", true, NodeDataType.PATH);
        assertPortType("geometry.architectural_primitives.window_array", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.architectural_primitives.window_array", "output_openings", false, NodeDataType.GEOMETRY);
        assertPortType("geometry.architectural_primitives.window_array", "output_frames", false, NodeDataType.FRAME_LIST);
        assertPortType("geometry.architectural_primitives.window_frame", "output_geometry", false, NodeDataType.GEOMETRY);
        assertPortType("geometry.architectural_primitives.roof_base", "input_face", true, NodeDataType.BOX_FACE);
        assertPortType("geometry.voxel.voxelize_geometry", "output_blocks", false, NodeDataType.BLOCK_LIST);

        for (String nodeId : List.of(
            "geometry.architectural_primitives.floor_slab",
            "geometry.architectural_primitives.wall_along_path",
            "geometry.architectural_primitives.window_array",
            "geometry.architectural_primitives.window_frame",
            "geometry.architectural_primitives.roof_base",
            "geometry.combine.geometry",
            "geometry.voxel.voxelize_geometry"
        )) {
            INode node = registry.createNodeInstance(nodeId);
            for (IPort port : node.getInputPorts()) {
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
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalMiniBuildingWorkflowContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RoofBaseProbe extends RoofBaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalMiniBuildingWorkflowContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class WindowFrameProbe extends WindowFrameNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalMiniBuildingWorkflowContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PlaceFramesProbe extends PlaceGeometryOnFramesNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalMiniBuildingWorkflowContractTest.connectInput(this, portId, outputType);
        }
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

    private static IPort findIn(List<IPort> ports, String portId) {
        for (IPort port : ports) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }
}
