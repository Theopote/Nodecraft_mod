package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.ArchitecturalPrimitiveSupport;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallSlabNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2: opening cutters must pierce full wall thickness for Front/Back/Left/Right hosts and yaw-rotated slabs.
 * Each case builds a thin host box whose Front face points along the requested world normal.
 */
class WallFaceDirectionOpeningCutContractTest {

    /** Host slab occupies 0..thickness along +normal; centered cutters need depth ≥ 2×thickness. */
    private static final double THICKNESS = 1.2d;
    private static final double DEPTH = 2.4d;

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void createOrientationMapsLocalAxesToWorldColumns() {
        Vector3d x = new Vector3d(0.8d, 0.0d, -0.6d).normalize();
        Vector3d z = new Vector3d(0.6d, 0.0d, 0.8d).normalize();
        Vector3d y = new Vector3d(x).cross(z).normalize();
        Matrix3d m = ArchitecturalPrimitiveSupport.createOrientation(x, y, z);
        Vector3d localX = m.transform(new Vector3d(1, 0, 0));
        Vector3d localY = m.transform(new Vector3d(0, 1, 0));
        Vector3d localZ = m.transform(new Vector3d(0, 0, 1));
        assertTrue(localX.distance(x) < 1.0e-9d, "local +X → xAxis");
        assertTrue(localY.distance(y) < 1.0e-9d, "local +Y → yAxis");
        assertTrue(localZ.distance(z) < 1.0e-9d, "local +Z → zAxis");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "front, 0.0",
        "back, 180.0",
        "left, -90.0",
        "right, 90.0",
        "yaw35, 35.0"
    })
    void windowArrayCutsThroughWallFacingWorldYaw(String label, double yawDegrees) {
        Matrix3d orientation = new Matrix3d().rotationY(Math.toRadians(yawDegrees));
        boolean oriented = Math.abs(yawDegrees) > 1.0e-9d;
        BoxGeometryData host = new BoxGeometryData(
            new Vector3d(0.0d, 2.0d, 0.0d),
            new Vector3d(6.0d, 2.0d, 0.3d),
            orientation,
            oriented
        );
        assertCutThroughFace(label, requireFace(host, "Front"));
    }

    private static void assertCutThroughFace(String label, BoxFaceData face) {
        CutPair cut = buildCut(face);
        assertEquals(Boolean.TRUE, cut.wallValid(), label + ": " + cut.wallError());
        assertEquals(Boolean.TRUE, cut.windowsValid(), label + ": " + cut.windowsError());
        assertEquals(2, cut.windowCount());

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", cut.wallGeom());
        difference.setInput("input_cutter", cut.openings());
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));

        BlockPosList solidWall = GeometryVoxelizer.voxelize(cut.wallGeom(), true);
        BlockPosList cutBlocks = GeometryVoxelizer.voxelize(
            assertInstanceOf(GeometryData.class, difference.getOutput("output_geometry")), true);
        Set<net.minecraft.util.math.BlockPos> cutSolid = ArchitecturalVoxelAssert.toSolidSet(cutBlocks);
        Set<net.minecraft.util.math.BlockPos> fullSolid = ArchitecturalVoxelAssert.toSolidSet(solidWall);

        assertTrue(fullSolid.size() > 0, label + " wall must voxelize");
        ArchitecturalVoxelAssert.assertFewerBlocksThan(cutSolid, fullSolid);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(cutSolid, cut.centers());

        Vector3d pierMid = new Vector3d(cut.centers().get(0).position())
            .add(cut.centers().get(1).position()).mul(0.5d);
        pierMid.fma(THICKNESS * 0.5d, face.getNormal());
        assertTrue(cutSolid.contains(ArchitecturalVoxelAssert.blockAt(pierMid)),
            label + " pier between windows should remain solid at " + ArchitecturalVoxelAssert.blockAt(pierMid));

        ArchitecturalVoxelAssert.assertOpeningCutsThroughThickness(
            cutSolid, cut.centers().get(0).position(), face.getNormal(), DEPTH / 2.0d);
    }

    private static CutPair buildCut(BoxFaceData face) {
        WallSlabProbe wall = new WallSlabProbe();
        wall.connectInput("input_wall_thickness", NodeDataType.DOUBLE);
        wall.setInput("input_face", face);
        wall.setInput("input_wall_thickness", THICKNESS);
        wall.processNode(null);

        WindowArrayProbe windows = new WindowArrayProbe();
        windows.connectInput("input_columns", NodeDataType.INTEGER);
        windows.connectInput("input_rows", NodeDataType.INTEGER);
        windows.connectInput("input_window_width", NodeDataType.DOUBLE);
        windows.connectInput("input_window_height", NodeDataType.DOUBLE);
        windows.connectInput("input_margin", NodeDataType.DOUBLE);
        windows.connectInput("input_depth", NodeDataType.DOUBLE);
        windows.setInput("input_face", face);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 2.0d);
        windows.setInput("input_window_height", 1.2d);
        windows.setInput("input_margin", 1.0d);
        windows.setInput("input_depth", DEPTH);
        windows.processNode(null);

        GeometryData wallGeom = assertInstanceOf(GeometryData.class, wall.getOutput("output_geometry"));
        GeometryData openings = assertInstanceOf(GeometryData.class, windows.getOutput("output_openings"));
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        return new CutPair(
            wallGeom,
            openings,
            centers,
            Boolean.TRUE.equals(wall.getOutput("output_valid")),
            Boolean.TRUE.equals(windows.getOutput("output_valid")),
            String.valueOf(wall.getOutput("output_error")),
            String.valueOf(windows.getOutput("output_error")),
            windows.getOutput("output_count") instanceof Number n ? n.intValue() : -1
        );
    }

    private record CutPair(
        GeometryData wallGeom,
        GeometryData openings,
        List<PointData> centers,
        boolean wallValid,
        boolean windowsValid,
        String wallError,
        String windowsError,
        int windowCount
    ) {
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

    private static BoxFaceData requireFace(BoxGeometryData box, String name) {
        return box.getFaces().stream()
            .filter(face -> name.equalsIgnoreCase(face.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing face " + name));
    }

    private static final class WallSlabProbe extends WallSlabNode {
        void connectInput(String portId, NodeDataType type) {
            WallFaceDirectionOpeningCutContractTest.connectInput(this, portId, type);
        }
    }

    private static final class WindowArrayProbe extends WindowArrayNode {
        void connectInput(String portId, NodeDataType type) {
            WallFaceDirectionOpeningCutContractTest.connectInput(this, portId, type);
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
