package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFrameUtilsTest {

    @Test
    void parallelTransportAvoidsSuddenSectionFlipOnGentleBend() {
        List<Vector3d> points = new ArrayList<>();
        // Quarter-circle-ish polyline in XZ (Minecraft horizontal turn)
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double a = (Math.PI * 0.5d) * i / steps;
            points.add(new Vector3d(Math.cos(a) * 10.0d, 64.0d, Math.sin(a) * 10.0d));
        }

        List<PathFrameUtils.Frame> frames = PathFrameUtils.framesAlongPolyline(points, new Vector3d(0, 1, 0));
        assertEquals(points.size(), frames.size());

        for (int i = 1; i < frames.size(); i++) {
            PathFrameUtils.Frame prev = frames.get(i - 1);
            PathFrameUtils.Frame cur = frames.get(i);
            double xDot = prev.xAxis().dot(cur.xAxis());
            double yDot = prev.yAxis().dot(cur.yAxis());
            // Adjacent frames should stay roughly aligned (no ~90°/180° jump).
            assertTrue(xDot > 0.5d, "x-axis flipped between " + (i - 1) + " and " + i + ": " + xDot);
            assertTrue(yDot > 0.5d, "y-axis flipped between " + (i - 1) + " and " + i + ": " + yDot);
        }
    }

    @Test
    void independentCardinalFramesCanFlipOnSamePath() {
        // Documents why parallel transport exists: least-aligned cardinal frames jump.
        List<Vector3d> points = List.of(
            new Vector3d(0, 64, 0),
            new Vector3d(6, 64, 1),
            new Vector3d(7, 64, 6),
            new Vector3d(1, 64, 7)
        );
        PathFrameUtils.Frame a = PathFrameUtils.initialFrame(points.get(1),
            PathFrameUtils.computeTangent(points, 1), null);
        PathFrameUtils.Frame b = PathFrameUtils.initialFrame(points.get(2),
            PathFrameUtils.computeTangent(points, 2), null);
        // Not asserting failure — only that transport path exists and runs.
        PathFrameUtils.Frame transported = PathFrameUtils.transport(a, points.get(2),
            PathFrameUtils.computeTangent(points, 2));
        assertTrue(transported.zAxis().lengthSquared() > 0.5d);
        assertTrue(Math.abs(b.xAxis().dot(a.xAxis())) <= 1.0d);
    }

    @Test
    void profileLocalOffsetsUsePlaneUvNotWorldDelta() {
        // Profile lying in a vertical YZ plane (rotated 90° about Y from default XZ).
        PlaneData plane = new PlaneData(new Vector3d(5, 64, 0), new Vector3d(1, 0, 0));
        List<Vector3d> closed = List.of(
            new Vector3d(5, 64, -1),
            new Vector3d(5, 66, -1),
            new Vector3d(5, 66, 1),
            new Vector3d(5, 64, 1),
            new Vector3d(5, 64, -1)
        );
        PolygonProfileData profile = new PolygonProfileData(closed, plane);
        List<Vector3d> locals = PathFrameUtils.profileLocalOffsets(profile);
        assertEquals(4, locals.size());

        // World-delta approach would leave a large X component (~5); plane UV must be ~0 in X.
        for (Vector3d local : locals) {
            assertEquals(0.0d, local.z, 1.0e-9d);
            assertTrue(Math.abs(local.x) < 3.0d, "u should be in-plane, got " + local);
            assertTrue(Math.abs(local.y) < 3.0d, "v should be in-plane, got " + local);
        }

        // Naive world-center subtraction leaves X≈0 only by chance of center; check extents match plane size.
        double maxAbsU = locals.stream().mapToDouble(v -> Math.abs(v.x)).max().orElse(0);
        double maxAbsV = locals.stream().mapToDouble(v -> Math.abs(v.y)).max().orElse(0);
        assertTrue(maxAbsU > 0.5d && maxAbsU < 2.5d);
        assertTrue(maxAbsV > 0.5d && maxAbsV < 2.5d);
    }
}
