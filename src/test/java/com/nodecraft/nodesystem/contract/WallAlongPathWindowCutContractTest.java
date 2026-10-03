package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
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
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.curves.BoxFaceBoundaryPathNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preset-shaped contract: Bottom Face Boundary → Wall Along Path vs Front Face → Window Array
 * → Difference. Opening cutters are centered on the face plane (outward normal is not “into host”).
 */
class WallAlongPathWindowCutContractTest {

    // Voxelization tests cell centers of 1-block cubes. A 0.4 wall centered on the
    // face never contains those centers; thickness/depth must span ±0.5 around the plane.
    private static final double WALL_THICKNESS = 1.2d;
    private static final double WINDOW_DEPTH = 1.2d;

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void openingCutterCenterEqualsFacePlaneCenter() {
        BoxGeometryData box = hostBox();
        BoxFaceData front = requireFace(box, "Front");

        WindowArrayProbe windows = windowsOnFront(front, 1, 2.0d, 2.0d, 1.0d);
        GeometryData openings = assertInstanceOf(GeometryData.class, windows.getOutput("output_openings"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        assertEquals(1, centers.size());

        List<GeometryData> leaves = new ArrayList<>();
        CompositeGeometryData.appendLeaves(leaves, openings);
        BoxGeometryData cutter = assertInstanceOf(BoxGeometryData.class, leaves.getFirst());

        Vector3d faceCenter = centers.getFirst().position();
        Vector3d boxCenter = cutter.getCenter();
        assertEquals(faceCenter.x, boxCenter.x, 1.0e-6d);
        assertEquals(faceCenter.y, boxCenter.y, 1.0e-6d);
        assertEquals(faceCenter.z, boxCenter.z, 1.0e-6d);

        Vector3d outward = front.getNormal().normalize();
        double offsetAlongNormal = new Vector3d(boxCenter).sub(front.getCenter()).dot(outward);
        assertEquals(0.0d, offsetAlongNormal, 1.0e-5d,
            "cutter must sit on the face plane, not offset by +Depth/2 along outward normal");
        assertEquals(WINDOW_DEPTH / 2.0d, cutter.getHalfExtents().z, 1.0e-6d);
    }

    @Test
    void differenceCutsThroughWallAlongPathThickness() {
        BoxGeometryData box = hostBox();
        BoxFaceData floorFace = requireFace(box, "Bottom");
        BoxFaceData frontFace = requireFace(box, "Front");
        Vector3d faceNormal = frontFace.getNormal();

        BoxFaceBoundaryPathNode boundary = new BoxFaceBoundaryPathNode();
        boundary.setInput("input_face", floorFace);
        boundary.processNode(null);
        PathData perimeter = assertInstanceOf(PathData.class, boundary.getOutput("output_path"));

        WallProbe walls = new WallProbe();
        walls.connectInput("input_height", NodeDataType.DOUBLE);
        walls.connectInput("input_thickness", NodeDataType.DOUBLE);
        walls.setInput("input_path", perimeter);
        walls.setInput("input_height", 4.0d);
        walls.setInput("input_thickness", WALL_THICKNESS);
        walls.processNode(null);
        assertEquals(Boolean.TRUE, walls.getOutput("output_valid"),
            String.valueOf(walls.getOutput("output_error")));
        GeometryData wallGeom = assertInstanceOf(GeometryData.class, walls.getOutput("output_geometry"));

        // Margin 1.2 + height 1.0 (TOP anchor) keeps lintel voxels above the opening band.
        WindowArrayProbe windows = windowsOnFront(frontFace, 2, 1.2d, 1.0d, 1.2d);
        GeometryData openings = assertInstanceOf(GeometryData.class, windows.getOutput("output_openings"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        assertEquals(2, centers.size());

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", wallGeom);
        difference.setInput("input_cutter", openings);
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));

        BlockPosList solidWall = GeometryVoxelizer.voxelize(wallGeom, true);
        BlockPosList cutBlocks = GeometryVoxelizer.voxelize(
            assertInstanceOf(GeometryData.class, difference.getOutput("output_geometry")), true);
        Set<net.minecraft.util.math.BlockPos> cutSolid = ArchitecturalVoxelAssert.toSolidSet(cutBlocks);
        Set<net.minecraft.util.math.BlockPos> fullSolid = ArchitecturalVoxelAssert.toSolidSet(solidWall);

        ArchitecturalVoxelAssert.assertFewerBlocksThan(cutSolid, fullSolid);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(cutSolid, centers);
        ArchitecturalVoxelAssert.assertOpeningCutsThroughThickness(
            cutSolid, centers.getFirst().position(), faceNormal, WINDOW_DEPTH / 2.0d);
        ArchitecturalVoxelAssert.assertPierBetweenWindowsHasBlock(
            cutSolid, centers.get(0), centers.get(1), faceNormal);

        List<Vector3d> corners = frontFace.getCorners();
        Vector3d faceY = new Vector3d(corners.get(3)).sub(corners.get(0)).normalize();
        ArchitecturalVoxelAssert.assertSillAndLintelSolid(
            cutSolid, centers.getFirst(), faceY, faceNormal, 0.5d, 0.35d);
    }

    private static BoxGeometryData hostBox() {
        BaseNode volume = (BaseNode) registry.createNodeInstance("geometry.primitives.box_from_corner_size");
        volume.setNodeState(java.util.Map.of(
            "cornerX", 0.0d,
            "cornerY", 0.0d,
            "cornerZ", 0.0d,
            "sizeX", 12.0d,
            "sizeY", 4.0d,
            "sizeZ", 8.0d
        ));
        volume.processNode(null);
        return assertInstanceOf(BoxGeometryData.class, volume.getOutput("output_box_geometry"));
    }

    private static WindowArrayProbe windowsOnFront(
        BoxFaceData front,
        int columns,
        double width,
        double height,
        double margin
    ) {
        WindowArrayProbe windows = new WindowArrayProbe();
        windows.connectInput("input_columns", NodeDataType.INTEGER);
        windows.connectInput("input_rows", NodeDataType.INTEGER);
        windows.connectInput("input_window_width", NodeDataType.DOUBLE);
        windows.connectInput("input_window_height", NodeDataType.DOUBLE);
        windows.connectInput("input_margin", NodeDataType.DOUBLE);
        windows.connectInput("input_depth", NodeDataType.DOUBLE);
        windows.setInput("input_face", front);
        windows.setInput("input_columns", columns);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", width);
        windows.setInput("input_window_height", height);
        windows.setInput("input_margin", margin);
        windows.setInput("input_depth", WINDOW_DEPTH);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"),
            String.valueOf(windows.getOutput("output_error")));
        assertEquals(columns, windows.getOutput("output_count"));
        return windows;
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

    private static final class WallProbe extends WallAlongPathNode {
        void connectInput(String portId, NodeDataType outputType) {
            WallAlongPathWindowCutContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType outputType) {
            WallAlongPathWindowCutContractTest.connectInput(this, portId, outputType);
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
}
