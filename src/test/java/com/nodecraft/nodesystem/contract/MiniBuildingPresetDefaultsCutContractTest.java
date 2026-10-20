package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flagship preset-shaped probe matching architectural.residential.mini_building_v1:
 * Wall Along Path (thickness/height wired) + Window Array (depth ≥ thickness) → Difference.
 * Dimensions are voxel-visible (≥1.2) so 1-block sampling keeps through-wall cuts.
 */
class MiniBuildingPresetDefaultsCutContractTest {

    private static final double WALL_THICKNESS = 1.2d;
    private static final double OPENING_DEPTH = 1.2d;

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void miniBuildingPresetDimsCutOpeningCentersInVoxels() {
        BaseNode volume = (BaseNode) registry.createNodeInstance("geometry.primitives.box_from_corner_size");
        volume.setNodeState(Map.of(
            "cornerX", 0.0d, "cornerY", 0.0d, "cornerZ", 0.0d,
            "sizeX", 10.0d, "sizeY", 3.0d, "sizeZ", 8.0d
        ));
        volume.processNode(null);
        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, volume.getOutput("output_box_geometry"));
        BoxFaceData floor = requireFace(box, "Bottom");
        BoxFaceData front = requireFace(box, "Front");
        Vector3d faceNormal = front.getNormal();

        BoxFaceBoundaryPathNode boundary = new BoxFaceBoundaryPathNode();
        boundary.setInput("input_face", floor);
        boundary.processNode(null);
        PathData perimeter = assertInstanceOf(PathData.class, boundary.getOutput("output_path"));

        WallAlongPathProbe walls = new WallAlongPathProbe();
        walls.connectInput("input_height", NodeDataType.DOUBLE);
        walls.connectInput("input_thickness", NodeDataType.DOUBLE);
        walls.setInput("input_path", perimeter);
        walls.setInput("input_height", 3.0d);
        walls.setInput("input_thickness", WALL_THICKNESS);
        walls.processNode(null);
        assertEquals(Boolean.TRUE, walls.getOutput("output_valid"),
            String.valueOf(walls.getOutput("output_error")));

        WindowArrayProbe windows = new WindowArrayProbe();
        windows.connectInput("input_columns", NodeDataType.INTEGER);
        windows.connectInput("input_rows", NodeDataType.INTEGER);
        windows.connectInput("input_window_width", NodeDataType.DOUBLE);
        windows.connectInput("input_window_height", NodeDataType.DOUBLE);
        windows.connectInput("input_margin", NodeDataType.DOUBLE);
        windows.connectInput("input_depth", NodeDataType.DOUBLE);
        windows.setInput("input_face", front);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.5d);
        windows.setInput("input_window_height", 1.0d);
        windows.setInput("input_margin", 0.8d);
        windows.setInput("input_depth", OPENING_DEPTH);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"),
            String.valueOf(windows.getOutput("output_error")));
        assertEquals(2, windows.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", walls.getOutput("output_geometry"));
        difference.setInput("input_cutter", windows.getOutput("output_openings"));
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));

        BlockPosList solidWall = GeometryVoxelizer.voxelize(
            assertInstanceOf(GeometryData.class, walls.getOutput("output_geometry")), true);
        BlockPosList cutBlocks = GeometryVoxelizer.voxelize(
            assertInstanceOf(GeometryData.class, difference.getOutput("output_geometry")), true);
        Set<net.minecraft.util.math.BlockPos> cutSolid = ArchitecturalVoxelAssert.toSolidSet(cutBlocks);
        Set<net.minecraft.util.math.BlockPos> fullSolid = ArchitecturalVoxelAssert.toSolidSet(solidWall);

        assertTrue(fullSolid.size() > 0, "wall must voxelize");
        ArchitecturalVoxelAssert.assertFewerBlocksThan(cutSolid, fullSolid);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(cutSolid, centers);
        ArchitecturalVoxelAssert.assertPierBetweenWindowsHasBlock(
            cutSolid, centers.get(0), centers.get(1), faceNormal);
    }

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
            .filter(face -> name.equalsIgnoreCase(face.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing face " + name));
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

    private static final class WallAlongPathProbe extends WallAlongPathNode {
        void connectInput(String portId, NodeDataType type) {
            MiniBuildingPresetDefaultsCutContractTest.connectInput(this, portId, type);
        }
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType type) {
            MiniBuildingPresetDefaultsCutContractTest.connectInput(this, portId, type);
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
}
