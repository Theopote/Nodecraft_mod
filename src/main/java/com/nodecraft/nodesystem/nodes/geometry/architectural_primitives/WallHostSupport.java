package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared host-wall slab geometry: footprint face grows along +outward normal
 * (0..thickness). Used by Wall Slab and Wall With Openings.
 */
final class WallHostSupport {

    private WallHostSupport() {
    }

    /**
     * Host slab occupies 0..thickness along +outward normal from the face plane.
     */
    static GeometryData createWallSlab(ArchitecturalPrimitiveSupport.FaceFrame frame, double wallThickness) {
        Vector3d center = new Vector3d(frame.center()).fma(wallThickness / 2.0d, frame.zAxis());
        Vector3d halfExtents = new Vector3d(frame.width() / 2.0d, frame.height() / 2.0d, wallThickness / 2.0d);
        BoxGeometryData box = ArchitecturalPrimitiveSupport.createOrientedBox(
            center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
        return GeometryOutputUtils.packGeometry(List.of(box));
    }

    static PathData edgePath(ArchitecturalPrimitiveSupport.FaceFrame frame, double heightOffset) {
        Vector3d left = new Vector3d(frame.center())
            .fma(-frame.width() / 2.0d, frame.xAxis())
            .fma(heightOffset, frame.yAxis());
        Vector3d right = new Vector3d(frame.center())
            .fma(frame.width() / 2.0d, frame.xAxis())
            .fma(heightOffset, frame.yAxis());
        return PathData.fromLine(new LineData(
            new Vec3d(left.x, left.y, left.z),
            new Vec3d(right.x, right.y, right.z)
        ));
    }

    static BoxFaceData planarFace(
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

    static BoxFaceData exteriorFace(ArchitecturalPrimitiveSupport.FaceFrame frame) {
        return planarFace("exterior", frame, 0.0d, new Vector3d(frame.zAxis()).negate());
    }

    static BoxFaceData interiorFace(ArchitecturalPrimitiveSupport.FaceFrame frame, double wallThickness) {
        return planarFace("interior", frame, wallThickness, frame.zAxis());
    }
}
