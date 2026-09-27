package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

/**
 * Continuous AABB bounds for {@link GeometryData} (no voxelization / BlockPos flooring).
 * <p>
 * Boolops: Composite = AABB union; Difference = minuend only (conservative);
 * Intersection = AABB ∩ AABB (non-overlap → null).
 */
public final class GeometryBoundsResolver {

    private GeometryBoundsResolver() {
    }

    public static @Nullable BoundingBoxData resolve(@Nullable GeometryData geometry) {
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof CompositeGeometryData composite) {
            return resolveComposite(composite);
        }
        if (geometry instanceof DifferenceGeometryData difference) {
            // A - B cannot extend beyond A; keep continuous envelope on the minuend.
            return resolve(difference.getMinuend());
        }
        if (geometry instanceof IntersectionGeometryData intersection) {
            return BoundingBoxData.intersection(resolve(intersection.left()), resolve(intersection.right()));
        }
        if (geometry instanceof BoxGeometryData box) {
            return resolveBox(box);
        }
        if (geometry instanceof ConeGeometryData cone) {
            return resolveCone(cone);
        }
        if (geometry instanceof FrustumConeGeometryData frustum) {
            return resolveFrustum(frustum);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return resolveCylinder(cylinder);
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return resolveEllipsoid(ellipsoid);
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            return resolveHemisphere(hemisphere);
        }
        if (geometry instanceof OctahedronGeometryData octahedron) {
            return fromPoints(octahedron.getVertices());
        }
        if (geometry instanceof IcosahedronGeometryData icosahedron) {
            return fromPoints(icosahedron.getVertices());
        }
        if (geometry instanceof DodecahedronGeometryData dodecahedron) {
            return fromPoints(dodecahedron.getVertices());
        }
        if (geometry instanceof PrismGeometryData prism) {
            return resolvePrism(prism);
        }
        if (geometry instanceof SquarePyramidGeometryData pyramid) {
            return resolveSquarePyramid(pyramid);
        }
        if (geometry instanceof SphereData sphere) {
            return resolveSphere(sphere);
        }
        if (geometry instanceof SdfGeometryData sdf) {
            return BoundingBoxData.create(sdf.min(), sdf.max());
        }
        if (geometry instanceof TetrahedronGeometryData tetrahedron) {
            return fromPoints(tetrahedron.getVertices());
        }
        if (geometry instanceof TorusGeometryData torus) {
            return resolveTorus(torus);
        }
        return null;
    }

    private static @Nullable BoundingBoxData resolveComposite(CompositeGeometryData composite) {
        BoundingBoxData merged = null;
        for (GeometryData child : composite.getGeometries()) {
            merged = BoundingBoxData.union(merged, resolve(child));
        }
        return merged;
    }

    private static @Nullable BoundingBoxData resolveBox(BoxGeometryData box) {
        Vector3d center = box.getCenter();
        Vector3d half = box.getHalfExtents();
        if (!BoundingBoxData.isFinite(center) || !BoundingBoxData.isFinite(half)) {
            return null;
        }
        if (box.isOriented()) {
            return orientedCornerHull(center, half, box.getOrientationMatrix());
        }
        return BoundingBoxData.create(
            new Vector3d(center.x - half.x, center.y - half.y, center.z - half.z),
            new Vector3d(center.x + half.x, center.y + half.y, center.z + half.z)
        );
    }

    private static @Nullable BoundingBoxData orientedCornerHull(
        Vector3d center,
        Vector3d halfExtents,
        Matrix3d orientationMatrix
    ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    Vector3d corner = new Vector3d(
                        sx * halfExtents.x,
                        sy * halfExtents.y,
                        sz * halfExtents.z
                    );
                    orientationMatrix.transform(corner);
                    corner.add(center);
                    minX = Math.min(minX, corner.x);
                    minY = Math.min(minY, corner.y);
                    minZ = Math.min(minZ, corner.z);
                    maxX = Math.max(maxX, corner.x);
                    maxY = Math.max(maxY, corner.y);
                    maxZ = Math.max(maxZ, corner.z);
                }
            }
        }
        return BoundingBoxData.create(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ));
    }

    private static @Nullable BoundingBoxData resolveSphere(SphereData sphere) {
        Vector3d center = sphere.center();
        double radius = sphere.radius();
        if (!BoundingBoxData.isFinite(center) || !Double.isFinite(radius) || radius < 0.0d) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(center.x - radius, center.y - radius, center.z - radius),
            new Vector3d(center.x + radius, center.y + radius, center.z + radius)
        );
    }

    private static @Nullable BoundingBoxData resolveHemisphere(HemisphereGeometryData geometry) {
        Vector3d center = geometry.center();
        double radius = Math.max(0.0d, geometry.radius());
        if (!BoundingBoxData.isFinite(center) || !Double.isFinite(radius)) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(center.x - radius, center.y - radius, center.z - radius),
            new Vector3d(center.x + radius, center.y + radius, center.z + radius)
        );
    }

    private static @Nullable BoundingBoxData resolveTorus(TorusGeometryData geometry) {
        Vector3d center = geometry.center();
        double bound = geometry.majorRadius() + geometry.minorRadius();
        if (!BoundingBoxData.isFinite(center) || !Double.isFinite(bound) || bound < 0.0d) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(center.x - bound, center.y - bound, center.z - bound),
            new Vector3d(center.x + bound, center.y + bound, center.z + bound)
        );
    }

    private static @Nullable BoundingBoxData resolveCone(ConeGeometryData geometry) {
        Vector3d baseCenter = geometry.getBaseCenter();
        Vector3d apex = geometry.getApex();
        double radius = geometry.getBaseRadius();
        if (!BoundingBoxData.isFinite(baseCenter) || !BoundingBoxData.isFinite(apex) || !Double.isFinite(radius)) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(
                Math.min(baseCenter.x - radius, apex.x),
                Math.min(baseCenter.y - radius, apex.y),
                Math.min(baseCenter.z - radius, apex.z)
            ),
            new Vector3d(
                Math.max(baseCenter.x + radius, apex.x),
                Math.max(baseCenter.y + radius, apex.y),
                Math.max(baseCenter.z + radius, apex.z)
            )
        );
    }

    private static @Nullable BoundingBoxData resolveFrustum(FrustumConeGeometryData geometry) {
        Vector3d base = geometry.getBaseCenter();
        Vector3d top = geometry.getTopCenter();
        double br = geometry.getBaseRadius();
        double tr = geometry.getTopRadius();
        if (!BoundingBoxData.isFinite(base) || !BoundingBoxData.isFinite(top)
            || !Double.isFinite(br) || !Double.isFinite(tr)) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(
                Math.min(base.x - br, top.x - tr),
                Math.min(base.y - br, top.y - tr),
                Math.min(base.z - br, top.z - tr)
            ),
            new Vector3d(
                Math.max(base.x + br, top.x + tr),
                Math.max(base.y + br, top.y + tr),
                Math.max(base.z + br, top.z + tr)
            )
        );
    }

    private static @Nullable BoundingBoxData resolveCylinder(CylinderGeometryData geometry) {
        Vector3d start = geometry.getStart();
        Vector3d end = geometry.getEnd();
        double radius = Math.max(0.0d, geometry.getRadius());
        if (!BoundingBoxData.isFinite(start) || !BoundingBoxData.isFinite(end) || !Double.isFinite(radius)) {
            return null;
        }
        return BoundingBoxData.create(
            new Vector3d(
                Math.min(start.x, end.x) - radius,
                Math.min(start.y, end.y) - radius,
                Math.min(start.z, end.z) - radius
            ),
            new Vector3d(
                Math.max(start.x, end.x) + radius,
                Math.max(start.y, end.y) + radius,
                Math.max(start.z, end.z) + radius
            )
        );
    }

    private static @Nullable BoundingBoxData resolveEllipsoid(EllipsoidGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        Vector3d radii = geometry.getRadii();
        if (!BoundingBoxData.isFinite(center) || !BoundingBoxData.isFinite(radii)) {
            return null;
        }
        return orientedCornerHull(center, radii, geometry.getOrientationMatrix());
    }

    private static @Nullable BoundingBoxData resolvePrism(PrismGeometryData geometry) {
        List<Vector3d> points = new java.util.ArrayList<>(geometry.baseVertices());
        points.addAll(geometry.getTopVertices());
        return fromPoints(points);
    }

    private static @Nullable BoundingBoxData resolveSquarePyramid(SquarePyramidGeometryData geometry) {
        List<Vector3d> points = new java.util.ArrayList<>(geometry.getBaseVertices());
        points.add(geometry.getApex());
        return fromPoints(points);
    }

    private static @Nullable BoundingBoxData fromPoints(@Nullable Iterable<Vector3d> points) {
        if (points == null) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (Vector3d point : points) {
            if (point == null || !BoundingBoxData.isFinite(point)) {
                return null;
            }
            any = true;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        if (!any) {
            return null;
        }
        return BoundingBoxData.create(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ));
    }
}
