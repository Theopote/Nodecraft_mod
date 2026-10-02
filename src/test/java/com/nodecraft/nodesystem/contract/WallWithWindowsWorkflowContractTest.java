package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.contract.support.ArchitecturalVoxelAssert;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowArrayNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WindowFrameNode;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallWithWindowsWorkflowContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void differenceCutLeavesOpeningCentersEmpty() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(4.0d, 1.5d, 0.25d),
            new Vector3d(4.0d, 1.5d, 0.25d)
        );
        BoxFaceData face = requireFace(box, "Front");

        WallWithOpeningsNode wall = new WallWithOpeningsNode();
        wall.setInput("input_face", face);
        wall.setInput("input_columns", 2);
        wall.setInput("input_rows", 1);
        wall.setInput("input_wall_thickness", 0.4d);
        wall.setInput("input_opening_width", 1.2d);
        wall.setInput("input_opening_height", 1.4d);
        wall.setInput("input_margin", 0.4d);
        wall.processNode(null);
        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));

        WindowArrayNode windows = new WindowArrayNode();
        windows.setInput("input_face", face);
        windows.setInput("input_columns", 2);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.2d);
        windows.setInput("input_window_height", 1.4d);
        windows.setInput("input_margin", 0.4d);
        windows.setInput("input_depth", 0.5d);
        windows.processNode(null);
        assertEquals(Boolean.TRUE, windows.getOutput("output_valid"));

        BaseNode difference = (BaseNode) registry.createNodeInstance("geometry.boolean.difference");
        difference.setInput("input_base", wall.getOutput("output_geometry"));
        difference.setInput("input_cutter", windows.getOutput("output_openings"));
        difference.processNode(null);
        assertEquals(Boolean.TRUE, difference.getOutput("output_valid"));

        BlockPosList cutBlocks = GeometryVoxelizer.voxelize(
            assertInstanceOf(GeometryData.class, difference.getOutput("output_geometry")), true);
        @SuppressWarnings("unchecked")
        List<PointData> centers = (List<PointData>) windows.getOutput("output_centers");
        Set<net.minecraft.util.math.BlockPos> solid = ArchitecturalVoxelAssert.toSolidSet(cutBlocks);
        ArchitecturalVoxelAssert.assertOpeningCentersEmpty(solid, centers);
    }

    @Test
    void placeOnFramesAddsFrameVoxelsBeyondCutWall() {
        BoxGeometryData box = new BoxGeometryData(
            new Vector3d(4.0d, 1.5d, 0.25d),
            new Vector3d(4.0d, 1.5d, 0.25d)
        );
        BoxFaceData face = requireFace(box, "Front");

        WindowArrayNode windows = new WindowArrayNode();
        windows.setInput("input_face", face);
        windows.setInput("input_columns", 1);
        windows.setInput("input_rows", 1);
        windows.setInput("input_window_width", 1.2d);
        windows.setInput("input_window_height", 1.4d);
        windows.setInput("input_depth", 0.5d);
        windows.processNode(null);

        WindowFrameProbe frame = new WindowFrameProbe();
        frame.connectInput("input_frame_width", NodeDataType.DOUBLE);
        frame.connectInput("input_frame_height", NodeDataType.DOUBLE);
        frame.connectInput("input_frame_thickness", NodeDataType.DOUBLE);
        frame.connectInput("input_depth", NodeDataType.DOUBLE);
        frame.setInput("input_frame_width", 1.2d);
        frame.setInput("input_frame_height", 1.4d);
        frame.setInput("input_frame_thickness", 0.1d);
        frame.setInput("input_depth", 0.15d);
        frame.processNode(null);

        PlaceFramesProbe place = new PlaceFramesProbe();
        place.connectInput("input_frames", NodeDataType.FRAME_LIST);
        place.setInput("input_geometry", frame.getOutput("output_geometry"));
        place.setInput("input_frames", windows.getOutput("output_frames"));
        place.processNode(null);
        assertEquals(Boolean.TRUE, place.getOutput("output_valid"));

        assertEquals(1, place.getOutput("output_count"));
        assertInstanceOf(GeometryData.class, place.getOutput("output_geometry"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        output.connectTo(input);
        target.getInput(inputPortId);
    }

    private static final class WindowFrameProbe extends WindowFrameNode {
        void connectInput(String portId, NodeDataType outputType) {
            WallWithWindowsWorkflowContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PlaceFramesProbe extends PlaceGeometryOnFramesNode {
        void connectInput(String portId, NodeDataType outputType) {
            WallWithWindowsWorkflowContractTest.connectInput(this, portId, outputType);
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
