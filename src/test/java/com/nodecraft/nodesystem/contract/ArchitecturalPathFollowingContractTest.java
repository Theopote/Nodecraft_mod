package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.RailingNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.StaircaseNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallAlongPathNode;
import com.nodecraft.nodesystem.nodes.geometry.architectural_primitives.WallWithOpeningsNode;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Batch 13.1 Step 1: Railing / Stair follow true PATH geometry, not firstâlast chord.
 */
class ArchitecturalPathFollowingContractTest {

    private static final double TOL = 0.35d;

    @Test
    void railingFollowsLShapedPathNotChord() {
        RailingNode railing = new RailingNode();
        railing.setInput("input_path", lShapedPath());
        railing.setInput("input_post_count", 3);
        railing.setInput("input_rail_count", 1);
        railing.setInput("input_height", 1.2d);
        railing.setInput("input_offset", 0.0d);
        railing.processNode(null);

        assertEquals(Boolean.TRUE, railing.getOutput("output_valid"));
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, railing.getOutput("output_geometry"));

        // Evenly spaced posts on a 20-unit L path land at start, corner, end â?not chord midpoints.
        List<Vector3d> postBases = geometry.getGeometries().stream()
            .filter(CylinderGeometryData.class::isInstance)
            .map(CylinderGeometryData.class::cast)
            .filter(cylinder -> Math.abs(cylinder.getEnd().y - cylinder.getStart().y) > 0.5d)
            .map(CylinderGeometryData::getStart)
            .toList();

        assertEquals(3, postBases.size());
        assertTrue(near(postBases.get(0), 0.0d, 0.0d, 0.0d), "start post: " + postBases.get(0));
        assertTrue(near(postBases.get(1), 10.0d, 0.0d, 0.0d), "corner post: " + postBases.get(1));
        assertTrue(near(postBases.get(2), 10.0d, 0.0d, 10.0d), "end post: " + postBases.get(2));

        // Chord midpoint would be ~ (5, 0, 5); no post should sit there.
        assertTrue(postBases.stream().noneMatch(p -> near(p, 5.0d, 0.0d, 5.0d)));
    }

    @Test
    void wallWithOpeningsKeepsWallAndOpeningsSeparate() {
        WallWithOpeningsNode wall = new WallWithOpeningsNode();
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
        // Primary geometry must remain a single solid slab â?not wall+opening composite.
        assertFalse(wall.getOutput("output_geometry") instanceof CompositeGeometryData);
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

    @Test
    void straightStaircaseFollowsLShapedPath() {
        StaircaseNode stair = new StaircaseNode();
        stair.setInput("input_path", lShapedPath());
        stair.setInput("input_layout", "straight");
        stair.setInput("input_step_count", 10);
        stair.setInput("input_step_run", 2.0d);
        stair.setInput("input_step_rise", 0.2d);
        stair.setInput("input_width", 1.0d);
        stair.processNode(null);

        assertEquals(Boolean.TRUE, stair.getOutput("output_valid"));
        CompositeGeometryData geometry = assertInstanceOf(CompositeGeometryData.class, stair.getOutput("output_geometry"));
        assertEquals(10, geometry.getGeometries().size());

        List<Vector3d> centers = geometry.getGeometries().stream()
            .map(ArchitecturalPathFollowingContractTest::boxCenter)
            .toList();

        // First half of steps stay on the +X leg (z â?0); second half turn onto +Z (x â?10).
        assertTrue(centers.stream().limit(4).allMatch(c -> Math.abs(c.z) < TOL), "early steps on first leg: " + centers);
        assertTrue(centers.stream().skip(6).allMatch(c -> Math.abs(c.x - 10.0d) < TOL), "late steps on second leg: " + centers);

        // Chord-only stairs would keep xâz along the diagonal; path-following must leave that line.
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
        assertEquals(2, wall.getOutput("output_count"));
        @SuppressWarnings("unchecked")
        List<FrameData> frames = (List<FrameData>) wall.getOutput("output_frames");
        assertNotNull(frames);
        assertEquals(2, frames.size());
        assertTrue(near(frames.get(0).getOrigin(), 5.0d, 1.5d, 0.0d), "first segment mid: " + frames.get(0).getOrigin());
        assertTrue(near(frames.get(1).getOrigin(), 10.0d, 1.5d, 5.0d), "second segment mid: " + frames.get(1).getOrigin());
    }

    private static PolylineData lShapedPath() {
        return new PolylineData(List.of(
            new Vec3d(0.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 0.0d),
            new Vec3d(10.0d, 0.0d, 10.0d)
        ));
    }

    private static Vector3d boxCenter(GeometryData geometry) {
        BoxGeometryData box = assertInstanceOf(BoxGeometryData.class, geometry);
        return box.getCenter();
    }

    private static boolean near(Vector3d point, double x, double y, double z) {
        return point.distance(x, y, z) <= TOL;
    }
}
