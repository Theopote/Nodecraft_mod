package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared floor / beam-grid construction for Floor Slab, Beam Grid, and convenience composites.
 */
final class FloorStructureSupport {

    private FloorStructureSupport() {
    }

    static BoxGeometryData createSlab(ArchitecturalPrimitiveSupport.FaceFrame frame, double slabThickness) {
        Vector3d center = new Vector3d(frame.center()).fma(slabThickness / 2.0d, frame.zAxis());
        Vector3d halfExtents = new Vector3d(frame.width() / 2.0d, frame.height() / 2.0d, slabThickness / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(
            center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    static BoxFaceData topFace(ArchitecturalPrimitiveSupport.FaceFrame frame, double slabThickness) {
        return planarFace("top", frame, slabThickness, frame.zAxis());
    }

    static BoxFaceData bottomFace(ArchitecturalPrimitiveSupport.FaceFrame frame) {
        return planarFace("bottom", frame, 0.0d, new Vector3d(frame.zAxis()).negate());
    }

    static BeamGridResult buildBeamGrid(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        int beamColumns,
        int beamRows,
        double beamWidth,
        double beamDepth,
        double beamDrop,
        double margin,
        double slabThickness
    ) {
        double usableWidth = frame.width() - 2.0d * margin;
        double usableHeight = frame.height() - 2.0d * margin;
        if (usableWidth < beamWidth || usableHeight < beamWidth) {
            return BeamGridResult.empty();
        }

        double spacingX = beamColumns > 1 ? (usableWidth - beamColumns * beamWidth) / (beamColumns - 1) : 0.0d;
        double spacingY = beamRows > 1 ? (usableHeight - beamRows * beamWidth) / (beamRows - 1) : 0.0d;
        if (spacingX < -1.0e-9d || spacingY < -1.0e-9d) {
            return BeamGridResult.empty();
        }

        double startX = -frame.width() / 2.0d + margin + beamWidth / 2.0d;
        double startY = -frame.height() / 2.0d + margin + beamWidth / 2.0d;
        Vector3d beamCenterOffset = new Vector3d(frame.zAxis())
            .mul(-(slabThickness / 2.0d + beamDrop + beamDepth / 2.0d));

        List<GeometryData> beams = new ArrayList<>(beamColumns + beamRows);
        List<FrameData> frames = new ArrayList<>(beamColumns + beamRows);
        List<PointData> centers = new ArrayList<>(beamColumns + beamRows);
        List<PathData> centerLines = new ArrayList<>(beamColumns + beamRows);

        for (int column = 0; column < beamColumns; column++) {
            double offsetX = startX + column * (beamWidth + spacingX);
            Vector3d center = new Vector3d(frame.center())
                .fma(offsetX, frame.xAxis())
                .add(beamCenterOffset);
            Vector3d halfExtents = new Vector3d(beamWidth / 2.0d, frame.height() / 2.0d - margin, beamDepth / 2.0d);
            beams.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            frames.add(new FrameData(center, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            centers.add(new PointData(center));
            Vector3d a = new Vector3d(center).fma(-(frame.height() / 2.0d - margin), frame.yAxis());
            Vector3d b = new Vector3d(center).fma(frame.height() / 2.0d - margin, frame.yAxis());
            centerLines.add(linePath(a, b));
        }

        for (int row = 0; row < beamRows; row++) {
            double offsetY = startY + row * (beamWidth + spacingY);
            Vector3d center = new Vector3d(frame.center())
                .fma(offsetY, frame.yAxis())
                .add(beamCenterOffset);
            Vector3d halfExtents = new Vector3d(frame.width() / 2.0d - margin, beamWidth / 2.0d, beamDepth / 2.0d);
            beams.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            frames.add(new FrameData(center, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            centers.add(new PointData(center));
            Vector3d a = new Vector3d(center).fma(-(frame.width() / 2.0d - margin), frame.xAxis());
            Vector3d b = new Vector3d(center).fma(frame.width() / 2.0d - margin, frame.xAxis());
            centerLines.add(linePath(a, b));
        }

        return new BeamGridResult(
            List.copyOf(beams),
            List.copyOf(frames),
            List.copyOf(centers),
            List.copyOf(centerLines)
        );
    }

    private static PathData linePath(Vector3d a, Vector3d b) {
        return PathData.fromLine(new LineData(
            new Vec3d(a.x, a.y, a.z),
            new Vec3d(b.x, b.y, b.z)
        ));
    }

    private static BoxFaceData planarFace(
        String name,
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double depthAlongNormal,
        Vector3d outwardNormal
    ) {
        Vector3d center = new Vector3d(frame.center()).fma(depthAlongNormal, frame.zAxis());
        Vector3d hx = new Vector3d(frame.xAxis()).mul(frame.width() / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(frame.height() / 2.0d);
        List<Vector3d> corners = List.of(
            new Vector3d(center).sub(hx).sub(hy),
            new Vector3d(center).add(hx).sub(hy),
            new Vector3d(center).add(hx).add(hy),
            new Vector3d(center).sub(hx).add(hy)
        );
        return new BoxFaceData(0, name, List.of(0, 1, 2, 3), corners, center, outwardNormal);
    }

    record BeamGridResult(
        List<GeometryData> beams,
        List<FrameData> frames,
        List<PointData> centers,
        List<PathData> centerLines
    ) {
        static BeamGridResult empty() {
            return new BeamGridResult(List.of(), List.of(), List.of(), List.of());
        }
    }
}
