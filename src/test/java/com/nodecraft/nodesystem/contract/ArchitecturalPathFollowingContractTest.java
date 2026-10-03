package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.contract.support.ArchitecturalGeometryAssert;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13.1 Step 1: Railing / Stair follow true PATH geometry, not first→last chord.
 */
class ArchitecturalPathFollowingContractTest {

    private static final double TOL = 0.35d;

    @Test
    void railingFollowsLShapedPathNotChord() {
        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.setInput("input_path", lShapedPath());
        railing.setInput("input_post_count", 3);
        railing.setInput("input_rail_count", 1);
        railing.setInput("input_height", 1.2d);
        railing.setInput("input_offset", 0.0d);
        railing.processNode(null);

        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"));
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, railing.getOutput("output_geometry"));

        List<Vector3d> postBases = geometry.geometries().stream()
            .filter(CylinderGeometryData.class::isInstance)
            .map(CylinderGeometryData.class::cast)
            .filter(cylinder -> Math.abs(cylinder.getEnd().y - cylinder.getStart().y) > 0.5d)
            .map(CylinderGeometryData::getStart)
            .toList();

        assertEquals(3, postBases.size());
        assertTrue(near(postBases.get(0), 0.0d, 0.0d, 0.0d), "start post: " + postBases.get(0));
        assertTrue(near(postBases.get(1), 10.0d, 0.0d, 0.0d), "corner post: " + postBases.get(1));
        assertTrue(near(postBases.get(2), 10.0d, 0.0d, 10.0d), "end post: " + postBases.get(2));
        assertTrue(postBases.stream().noneMatch(p -> near(p, 5.0d, 0.0d, 5.0d)));
    }

    @Test
    void wallWithOpeningsKeepsWallAndOpeningsSeparate() {
        WallOpeningsProbe wall = new WallOpeningsProbe();
        wall.connectInput("input_columns", NodeDataType.INTEGER);
        wall.connectInput("input_rows", NodeDataType.INTEGER);
        wall.setInput("input_face", verticalFace());
        wall.setInput("input_columns", 2);
        wall.setInput("input_rows", 1);
        wall.setInput("input_wall_thickness", 0.4d);
        wall.setInput("input_opening_width", 1.0d);
        wall.setInput("input_opening_height", 1.5d);
        wall.setInput("input_margin", 0.5d);
        wall.processNode(null);

        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));
        assertInstanceOf(BoxGeometryData.class, wall.getOutput("output_geometry"));
        CompositeGeometryData openings = assertInstanceOf(CompositeGeometryData.class, wall.getOutput("output_openings"));
        assertEquals(2, openings.size());
        assertEquals(2, wall.getOutput("output_count"));
        assertNotNull(wall.getOutput("output_top_edge"));
        assertNotNull(wall.getOutput("output_exterior_face"));
        assertFalse(wall.getOutput("output_geometry") instanceof CompositeGeometryData);
    }

    @Test
    void openPathRailingFailsWhenPostCountBelowTwo() {
        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.setInput("input_path", shortPath(10.0d));
        railing.setInput("input_post_count", 1);
        railing.setInput("input_rail_count", 1);
        railing.processNode(null);

        assertEquals(Boolean.FALSE, railing.getOutput("output_valid"));
        String error = (String) railing.getOutput("output_error");
        assertTrue(error.toLowerCase().contains("post count"), "error=" + error);
    }

    @Test
    void closedPathRailingFailsWhenPostCountBelowThree() {
        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.setInput("input_path", closedRectPath());
        railing.setInput("input_post_count", 2);
        railing.setInput("input_rail_count", 1);
        railing.processNode(null);

        assertEquals(Boolean.FALSE, railing.getOutput("output_valid"));
        String error = (String) railing.getOutput("output_error");
        assertTrue(error.toLowerCase().contains("post count"), "error=" + error);
    }

    @Test
    void straightStairFailsWhenPathTooShort() {
        StaircaseProbe stair = new StaircaseProbe();
        stair.connectInput("input_layout", NodeDataType.STRING);
        stair.connectInput("input_step_count", NodeDataType.INTEGER);
        stair.connectInput("input_step_run", NodeDataType.DOUBLE);
        stair.connectInput("input_step_rise", NodeDataType.DOUBLE);
        stair.connectInput("input_width", NodeDataType.DOUBLE);
        stair.setInput("input_path", shortPath(6.0d));
        stair.setInput("input_layout", "straight");
        stair.setInput("input_step_count", 20);
        stair.setInput("input_step_run", 0.5d);
        stair.setInput("input_step_rise", 0.2d);
        stair.setInput("input_width", 1.0d);
        stair.processNode(null);

        assertEquals(Boolean.FALSE, stair.getOutput("output_valid"));
        String error = (String) stair.getOutput("output_error");
        assertTrue(error.toLowerCase().contains("too short"), "error=" + error);
    }

    @Test
    void straightStairFailsWhenLandingExceedsPath() {
        StaircaseProbe stair = new StaircaseProbe();
        stair.connectInput("input_layout", NodeDataType.STRING);
        stair.connectInput("input_step_count", NodeDataType.INTEGER);
        stair.connectInput("input_step_run", NodeDataType.DOUBLE);
        stair.connectInput("input_step_rise", NodeDataType.DOUBLE);
        stair.connectInput("input_width", NodeDataType.DOUBLE);
        stair.connectInput("input_landing_length", NodeDataType.DOUBLE);
        // Steps alone fit (10 × 0.5 = 5), but steps + landing = 8 exceeds path length 6.
        stair.setInput("input_path", shortPath(6.0d));
        stair.setInput("input_layout", "straight");
        stair.setInput("input_step_count", 10);
        stair.setInput("input_step_run", 0.5d);
        stair.setInput("input_step_rise", 0.2d);
        stair.setInput("input_width", 1.0d);
        stair.setInput("input_landing_length", 3.0d);
        stair.processNode(null);

        assertEquals(Boolean.FALSE, stair.getOutput("output_valid"));
        String error = (String) stair.getOutput("output_error");
        assertTrue(error.toLowerCase().contains("too short"), "error=" + error);
        assertTrue(error.toLowerCase().contains("landing"), "error=" + error);
    }

    @Test
    void railingDefaultPostsAreWorldVerticalOnSlope() {
        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.setInput("input_path", slopedPath());
        railing.setInput("input_post_count", 2);
        railing.setInput("input_rail_count", 1);
        railing.setInput("input_height", 1.2d);
        railing.processNode(null);

        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"),
            String.valueOf(railing.getOutput("output_error")));
        List<CylinderGeometryData> posts = postsFrom(railing.getOutput("output_geometry"));
        assertEquals(2, posts.size());
        for (CylinderGeometryData post : posts) {
            Vector3d axis = new Vector3d(post.getEnd()).sub(post.getStart()).normalize();
            assertTrue(Math.abs(axis.y) > 0.99d, "world-vertical post axis: " + axis);
            assertTrue(Math.abs(axis.x) < 0.1d && Math.abs(axis.z) < 0.1d, "post axis: " + axis);
        }
    }

    @Test
    void railingPathNormalPostsTiltWithSlope() {
        RailingProbe railing = new RailingProbe();
        railing.connectInput("input_post_count", NodeDataType.INTEGER);
        railing.connectInput("input_rail_count", NodeDataType.INTEGER);
        railing.connectInput("input_post_up", NodeDataType.STRING);
        railing.setInput("input_path", slopedPath());
        railing.setInput("input_post_count", 2);
        railing.setInput("input_rail_count", 1);
        railing.setInput("input_height", 1.2d);
        railing.setInput("input_post_up", "path_normal");
        railing.processNode(null);

        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"),
            String.valueOf(railing.getOutput("output_error")));
        List<CylinderGeometryData> posts = postsFrom(railing.getOutput("output_geometry"));
        assertEquals(2, posts.size());
        Vector3d axis = new Vector3d(posts.getFirst().getEnd()).sub(posts.getFirst().getStart()).normalize();
        assertTrue(Math.abs(axis.y) < 0.99d, "path-normal post should tilt: " + axis);
        assertTrue(Math.abs(axis.x) > 0.2d, "path-normal post should lean along slope: " + axis);
    }

    @Test
    void straightStaircaseCornerStepsStayContinuous() {
        StaircaseProbe stair = new StaircaseProbe();
        stair.connectInput("input_layout", NodeDataType.STRING);
        stair.connectInput("input_step_count", NodeDataType.INTEGER);
        stair.connectInput("input_step_run", NodeDataType.DOUBLE);
        stair.connectInput("input_step_rise", NodeDataType.DOUBLE);
        stair.connectInput("input_width", NodeDataType.DOUBLE);
        stair.setInput("input_path", lShapedPath());
        stair.setInput("input_layout", "straight");
        stair.setInput("input_step_count", 10);
        stair.setInput("input_step_run", 2.0d);
        stair.setInput("input_step_rise", 0.2d);
        stair.setInput("input_width", 1.0d);
        stair.processNode(null);

        assertEquals(Boolean.TRUE, stair.getOutput("output_valid"));
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, stair.getOutput("output_geometry"));
        List<BoxGeometryData> steps = geometry.geometries().stream()
            .map(BoxGeometryData.class::cast)
            .toList();
        assertEquals(10, steps.size());

        BoxGeometryData beforeCorner = steps.get(4);
        BoxGeometryData afterCorner = steps.get(5);
        assertTrue(near(beforeCorner.getCenter(), 9.0d, 0.9d, 0.0d),
            "step before corner: " + beforeCorner.getCenter());
        assertTrue(near(afterCorner.getCenter(), 10.0d, 1.1d, 1.0d),
            "step after corner: " + afterCorner.getCenter());
        ArchitecturalGeometryAssert.assertCornerStepContinuity(beforeCorner, afterCorner, 0.05d, 0.85d);
    }

    @Test
    void straightStaircaseFollowsLShapedPath() {
        StaircaseProbe stair = new StaircaseProbe();
        stair.connectInput("input_layout", NodeDataType.STRING);
        stair.connectInput("input_step_count", NodeDataType.INTEGER);
        stair.connectInput("input_step_run", NodeDataType.DOUBLE);
        stair.connectInput("input_step_rise", NodeDataType.DOUBLE);
        stair.connectInput("input_width", NodeDataType.DOUBLE);
        stair.setInput("input_path", lShapedPath());
        stair.setInput("input_layout", "straight");
        stair.setInput("input_step_count", 10);
        stair.setInput("input_step_run", 2.0d);
        stair.setInput("input_step_rise", 0.2d);
        stair.setInput("input_width", 1.0d);
        stair.processNode(null);

        assertEquals(Boolean.TRUE, stair.getOutput("output_valid"));
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, stair.getOutput("output_geometry"));
        assertEquals(10, geometry.geometries().size());

        List<Vector3d> centers = geometry.geometries().stream()
            .map(ArchitecturalPathFollowingContractTest::boxCenter)
            .toList();

        assertTrue(centers.stream().limit(4).allMatch(c -> Math.abs(c.z) < TOL), "early steps on first leg: " + centers);
        assertTrue(centers.stream().skip(6).allMatch(c -> Math.abs(c.x - 10.0d) < TOL), "late steps on second leg: " + centers);
        assertTrue(centers.stream().anyMatch(c -> Math.abs(c.x - c.z) > 2.0d),
            "expected L deviation from chord diagonal: " + centers);
    }

    @Test
    void wallAlongPathFollowsLShapedPolyline() {
        WallAlongPathNode wall = new WallAlongPathNode();
        wall.setInput("input_path", lShapedPath());
        wall.setInput("input_height", 3.0d);
        wall.setInput("input_thickness", 0.4d);
        wall.processNode(null);

        assertEquals(Boolean.TRUE, wall.getOutput("output_valid"));
        int pieceCount = (Integer) wall.getOutput("output_count");
        assertTrue(pieceCount >= 1, "joined wall extrusion piece count");
        @SuppressWarnings("unchecked")
        List<FrameData> frames = (List<FrameData>) wall.getOutput("output_frames");
        assertNotNull(frames);
        assertEquals(2, frames.size());
        assertTrue(near(frames.get(0).getOrigin(), 5.0d, 1.5d, 0.0d), "first segment mid: " + frames.get(0).getOrigin());
        assertTrue(near(frames.get(1).getOrigin(), 10.0d, 1.5d, 5.0d), "second segment mid: " + frames.get(1).getOrigin());
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static BoxFaceData verticalFace() {
        List<Vector3d> corners = List.of(
            new Vector3d(0.0d, 0.0d, 0.0d),
            new Vector3d(8.0d, 0.0d, 0.0d),
            new Vector3d(8.0d, 4.0d, 0.0d),
            new Vector3d(0.0d, 4.0d, 0.0d)
        );
        return new BoxFaceData(0, "front", List.of(0, 1, 2, 3), corners,
            new Vector3d(4.0d, 2.0d, 0.0d), new Vector3d(0.0d, 0.0d, 1.0d));
    }

    private static PathData shortPath(double length) {
        return PathData.fromLine(new com.nodecraft.nodesystem.datatypes.LineData(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(length, 0.0d, 0.0d)
        ));
    }

    private static PathData slopedPath() {
        return PathData.fromLine(new com.nodecraft.nodesystem.datatypes.LineData(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 5.0d, 0.0d)
        ));
    }

    private static List<CylinderGeometryData> postsFrom(Object geometryOutput) {
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, geometryOutput);
        return geometry.geometries().stream()
            .filter(CylinderGeometryData.class::isInstance)
            .map(CylinderGeometryData.class::cast)
            // Posts are height-length (~1.2); rails follow the long slope (~11).
            .filter(cylinder -> cylinder.getStart().distance(cylinder.getEnd()) < 2.5d)
            .toList();
    }

    private static PolylineData lShapedPath() {
        return new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 10.0d)
        ));
    }

    private static PolylineData closedRectPath() {
        return new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 8.0d),
            new Vec3d(0.0d, 0.0d, 0.0d)
        ));
    }

    private static Vector3d boxCenter(GeometryData geometry) {
        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, geometry);
        return box.getCenter();
    }

    private static boolean near(Vector3d point, double x, double y, double z) {
        return point.distance(x, y, z) <= TOL;
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

    private static final class RailingProbe extends RailingNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPathFollowingContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class WallOpeningsProbe extends WallWithOpeningsNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPathFollowingContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class StaircaseProbe extends StaircaseNode {
        void connectInput(String portId, NodeDataType outputType) {
            ArchitecturalPathFollowingContractTest.connectInput(this, portId, outputType);
        }
    }
}
