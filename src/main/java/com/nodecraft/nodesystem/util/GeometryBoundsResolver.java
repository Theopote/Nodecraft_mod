package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CapsuleGeometryData;
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
        if (GeometryExpressionLimits.exceedsMax(geometry)) {
            return null;
        }
        return switch (geometry) {
            case CompositeGeometryData composite -> resolveComposite(composite);
            case DifferenceGeometryData difference ->
                // A - B cannot extend beyond A; keep continuous envelope on the minuend.
                    resolve(difference.getMinuend());
            case IntersectionGeometryData intersection ->
                    BoundingBoxData.intersection(resolve(intersection.left()), resolve(intersection.right()));
            case BoxGeometryData box -> resolveBox(box);
            case CapsuleGeometryData capsule -> resolveCapsule(capsule);
            case ConeGeometryData cone -> resolveCone(cone);
            case FrustumConeGeometryData frustum -> resolveFrustum(frustum);
            case CylinderGeometryData cylinder -> resolveCylinder(cylinder);
            case EllipsoidGeometryData ellipsoid -> resolveEllipsoid(ellipsoid);
            case HemisphereGeometryData hemisphere -> resolveHemisphere(hemisphere);
            case OctahedronGeometryData octahedron -> fromPoints(octahedron.getVertices());
            case IcosahedronGeometryData icosahedron -> fromPoints(icosahedron.getVertices());
            case DodecahedronGeometryData dodecahedron -> fromPoints(dodecahedron.getVertices());
            case PrismGeometryData prism -> resolvePrism(prism);
            case SquarePyramidGeometryData pyramid -> resolveSquarePyramid(pyramid);
            case SphereData sphere -> resolveSphere(sphere);
            case SdfGeometryData sdf -> BoundingBoxData.create(sdf.min(), sdf.max());
            case TetrahedronGeometryData tetrahedron -> fromPoints(tetrahedron.getVertices());
            case TorusGeometryData torus -> resolveTorus(torus);
            case null, default -> null;
        };
    }

    private static @Nullable BoundingBoxData resolveComposite(CompositeGeometryData composite) {
        BoundingBoxData merged = null;
        for (GeometryData child : composite.geometries()) {
            BoundingBoxData childBounds = resolve(child);
            if (childBounds == null) {
                // Transactional: any unresolvable child fails the whole composite.
                return null;
            }
            merged = merged == null ? childBounds : BoundingBoxData.union(merged, childBounds);
            if (merged == null) {
                return null;
            }
        }
        return merged;
    }

    private static @Nullable BoundingBoxData resolveCapsule(CapsuleGeometryData capsule) {
        BoundingBoxData merged = resolveCylinder(capsule.cylinder());
        merged = BoundingBoxData.union(merged, resolveHemisphere(capsule.startHemisphere()));
        return BoundingBoxData.union(merged, resolveHemisphere(capsule.endHemisphere()));
    }

    private static @Nullable BoundingBoxData resolveBox(BoxGeometryData box) {
        Vector3d center = box.getCenter();
        Vector3d half = box.getHalfExtents();
        if (!BoundingBoxData.isFinite(center) || !BoundingBoxData.isFinite(half)
            || half.x < 0.0d || half.y < 0.0d || half.z < 0.0d) {
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
        double radius = geometry.radius();
        if (!BoundingBoxData.isFinite(center) || !Double.isFinite(radius) || radius < 0.0d) {
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
        if (!BoundingBoxData.isFinite(baseCenter) || !BoundingBoxData.isFinite(apex)
            || !Double.isFinite(radius) || radius < 0.0d) {
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
            || !Double.isFinite(br) || !Double.isFinite(tr)
            || br < 0.0d || tr < 0.0d) {
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
        double radius = geometry.getRadius();
        if (!BoundingBoxData.isFinite(start) || !BoundingBoxData.isFinite(end)
            || !Double.isFinite(radius) || radius < 0.0d) {
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
        if (!BoundingBoxData.isFinite(center) || !BoundingBoxData.isFinite(radii)
            || radii.x < 0.0d || radii.y < 0.0d || radii.z < 0.0d) {
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
            if (!BoundingBoxData.isFinite(point)) {
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
