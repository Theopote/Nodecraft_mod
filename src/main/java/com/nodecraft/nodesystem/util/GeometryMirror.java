package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.MirroredSdfData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reflects analytic {@link GeometryData} values about a plane.
 */
public final class GeometryMirror {

    private GeometryMirror() {
    }

    public static GeometryData mirror(GeometryData geometry, PlaneData plane) {
        if (geometry == null || plane == null) {
            return null;
        }
        switch (geometry) {
            case CompositeGeometryData composite -> {
                List<GeometryData> mirrored = new ArrayList<>(composite.size());
                for (GeometryData child : composite.getGeometries()) {
                    GeometryData m = mirror(child, plane);
                    if (m != null) {
                        mirrored.add(m);
                    }
                }
                return mirrored.isEmpty() ? null : new CompositeGeometryData(mirrored);
            }
            case IntersectionGeometryData intersection -> {
                GeometryData left = mirror(intersection.left(), plane);
                GeometryData right = mirror(intersection.right(), plane);
                if (left == null || right == null) {
                    return null;
                }
                return new IntersectionGeometryData(left, right);
            }
            case DifferenceGeometryData difference -> {
                GeometryData minuend = mirror(difference.getMinuend(), plane);
                GeometryData subtrahend = mirror(difference.getSubtrahend(), plane);
                if (minuend == null || subtrahend == null) {
                    return null;
                }
                return new DifferenceGeometryData(minuend, subtrahend);
            }
            case SphereData sphere -> {
                return new SphereData(mirrorPoint(sphere.center(), plane), sphere.radius());
            }
            case CylinderGeometryData cylinder -> {
                return new CylinderGeometryData(
                        mirrorPoint(cylinder.getStart(), plane),
                        mirrorPoint(cylinder.getEnd(), plane),
                        cylinder.getRadius()
                );
            }
            case ConeGeometryData cone -> {
                return new ConeGeometryData(
                        mirrorPoint(cone.getBaseCenter(), plane),
                        mirrorPoint(cone.getApex(), plane),
                        cone.getBaseRadius()
                );
            }
            case FrustumConeGeometryData frustum -> {
                return new FrustumConeGeometryData(
                        mirrorPoint(frustum.getBaseCenter(), plane),
                        mirrorPoint(frustum.getTopCenter(), plane),
                        frustum.getBaseRadius(),
                        frustum.getTopRadius()
                );
            }
            case EllipsoidGeometryData ellipsoid -> {
                Matrix3d rm = new Matrix3d(reflectionMatrix3(plane.getNormal())).mul(ellipsoid.getOrientationMatrix());
                return new EllipsoidGeometryData(
                        mirrorPoint(ellipsoid.getCenter(), plane),
                        ellipsoid.getRadii(),
                        rm,
                        true
                );
            }
            case HemisphereGeometryData hemisphere -> {
                return new HemisphereGeometryData(
                        mirrorPoint(hemisphere.center(), plane),
                        mirrorDirection(hemisphere.axis(), plane),
                        hemisphere.radius()
                );
            }
            case BoxGeometryData box -> {
                Vector3d center = mirrorPoint(box.getCenter(), plane);
                Matrix3d r = reflectionMatrix3(plane.getNormal());
                Matrix3d rm = new Matrix3d(r).mul(box.getOrientationMatrix());
                return new BoxGeometryData(center, box.getHalfExtents(), rm, box.isOriented());
            }
            case PrismGeometryData prism -> {
                List<Vector3d> base = prism.baseVertices();
                List<Vector3d> mirroredBase = new ArrayList<>(base.size());
                for (Vector3d v : base) {
                    mirroredBase.add(mirrorPoint(v, plane));
                }
                Vector3d extrusion = mirrorDirection(prism.extrusionVector(), plane);
                return new PrismGeometryData(mirroredBase, extrusion);
            }
            case TorusGeometryData torus -> {
                return new TorusGeometryData(
                        mirrorPoint(torus.center(), plane),
                        mirrorDirection(torus.axis(), plane),
                        torus.majorRadius(),
                        torus.minorRadius()
                );
            }
            case SquarePyramidGeometryData pyramid -> {
                Vector3d baseCenter = mirrorPoint(pyramid.getBaseCenter(), plane);
                Vector3d xAxis = mirrorDirection(pyramid.getXAxis(), plane);
                Vector3d yRaw = mirrorDirection(pyramid.getYAxis(), plane);
                Vector3d nRaw = mirrorDirection(pyramid.getNormal(), plane);
                if (xAxis.lengthSquared() < 1.0e-18d || yRaw.lengthSquared() < 1.0e-18d || nRaw.lengthSquared() < 1.0e-18d) {
                    return null;
                }
                xAxis.normalize();
                Vector3d normal = new Vector3d(xAxis).cross(yRaw);
                if (normal.lengthSquared() < 1.0e-18d) {
                    normal.set(nRaw);
                }
                normal.normalize();
                Vector3d yAxis = new Vector3d(normal).cross(xAxis).normalize();
                Vector3d apex = mirrorPoint(pyramid.getApex(), plane);
                double height = new Vector3d(apex).sub(baseCenter).dot(normal);
                if (height < 1.0e-9d) {
                    normal.negate();
                    height = new Vector3d(apex).sub(baseCenter).dot(normal);
                }
                if (height < 1.0e-9d) {
                    return null;
                }
                return new SquarePyramidGeometryData(baseCenter, xAxis, yAxis, normal, pyramid.getBaseSize(), height);
            }
            case OctahedronGeometryData oct -> {
                Matrix3d rm = new Matrix3d(reflectionMatrix3(plane.getNormal())).mul(oct.getOrientationMatrix());
                return new OctahedronGeometryData(mirrorPoint(oct.getCenter(), plane), oct.getVertexRadius(), rm);
            }
            case TetrahedronGeometryData tet -> {
                Matrix3d rm = new Matrix3d(reflectionMatrix3(plane.getNormal())).mul(tet.getOrientationMatrix());
                return new TetrahedronGeometryData(mirrorPoint(tet.getCenter(), plane), tet.getEdgeLength(), rm);
            }
            case IcosahedronGeometryData ico -> {
                Matrix3d rm = new Matrix3d(reflectionMatrix3(plane.getNormal())).mul(ico.getOrientationMatrix());
                return new IcosahedronGeometryData(mirrorPoint(ico.getCenter(), plane), ico.getEdgeLength(), rm);
            }
            case DodecahedronGeometryData dod -> {
                Matrix3d rm = new Matrix3d(reflectionMatrix3(plane.getNormal())).mul(dod.getOrientationMatrix());
                return new DodecahedronGeometryData(mirrorPoint(dod.getCenter(), plane), dod.getEdgeLength(), rm);
            }
            case SdfGeometryData sdfGeom -> {
                SignedDistanceFieldData sdf = sdfGeom.sdf();
                if (sdf == null) {
                    return null;
                }
                SignedDistanceFieldData mirroredSdf = new MirroredSdfData(sdf, plane);
                Vector3d[] corners = getVector3ds(sdfGeom);
                Vector3d newMin = new Vector3d(Double.POSITIVE_INFINITY);
                Vector3d newMax = new Vector3d(Double.NEGATIVE_INFINITY);
                for (Vector3d corner : corners) {
                    Vector3d p = mirrorPoint(corner, plane);
                    newMin.min(p);
                    newMax.max(p);
                }
                return new SdfGeometryData(mirroredSdf, newMin, newMax, sdfGeom.isoValue());
            }
            default -> {
            }
        }
        return null;
    }

    private static Vector3d @NonNull [] getVector3ds(SdfGeometryData sdfGeom) {
        Vector3d min = sdfGeom.min();
        Vector3d max = sdfGeom.max();
        return new Vector3d[]{
                new Vector3d(min.x, min.y, min.z),
                new Vector3d(max.x, min.y, min.z),
                new Vector3d(min.x, max.y, min.z),
                new Vector3d(max.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z),
                new Vector3d(max.x, min.y, max.z),
                new Vector3d(min.x, max.y, max.z),
                new Vector3d(max.x, max.y, max.z)
        };
    }

    public static Vector3d mirrorPoint(Vector3d point, PlaneData plane) {
        Vector4d coefficients = plane.getPlane();
        Vector3d normal = new Vector3d(coefficients.x, coefficients.y, coefficients.z);
        double normalLengthSquared = normal.lengthSquared();
        if (normalLengthSquared <= 1.0e-12d) {
            return new Vector3d(point);
        }
        double signedValue = normal.dot(point) + coefficients.w;
        Vector3d displacement = new Vector3d(normal).mul(2.0d * signedValue / normalLengthSquared);
        return new Vector3d(point).sub(displacement);
    }

    public static Vector3d mirrorDirection(Vector3d direction, PlaneData plane) {
        Vector3d n = plane.getNormal();
        double normalLengthSquared = n.lengthSquared();
        if (normalLengthSquared <= 1.0e-12d) {
            return new Vector3d(direction);
        }
        double s = n.dot(direction) / normalLengthSquared;
        return new Vector3d(direction).sub(new Vector3d(n).mul(2.0d * s));
    }

    private static Matrix3d reflectionMatrix3(Vector3d normal) {
        if (normal == null || normal.lengthSquared() <= 1.0e-12d) {
            return new Matrix3d().identity();
        }
        Vector3d n = new Vector3d(normal).normalize();
        double nx = n.x;
        double ny = n.y;
        double nz = n.z;
        Matrix3d r = new Matrix3d();
        r.m00 = 1.0d - 2.0d * nx * nx;
        r.m01 = -2.0d * nx * ny;
        r.m02 = -2.0d * nx * nz;
        r.m10 = -2.0d * ny * nx;
        r.m11 = 1.0d - 2.0d * ny * ny;
        r.m12 = -2.0d * ny * nz;
        r.m20 = -2.0d * nz * nx;
        r.m21 = -2.0d * nz * ny;
        r.m22 = 1.0d - 2.0d * nz * nz;
        return r;
    }
}
