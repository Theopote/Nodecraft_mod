package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

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
        if (geometry instanceof CompositeGeometryData composite) {
            List<GeometryData> mirrored = new ArrayList<>(composite.size());
            for (GeometryData child : composite.getGeometries()) {
                GeometryData m = mirror(child, plane);
                if (m != null) {
                    mirrored.add(m);
                }
            }
            return mirrored.isEmpty() ? null : new CompositeGeometryData(mirrored);
        }
        if (geometry instanceof IntersectionGeometryData intersection) {
            GeometryData left = mirror(intersection.getLeft(), plane);
            GeometryData right = mirror(intersection.getRight(), plane);
            if (left == null || right == null) {
                return null;
            }
            return new IntersectionGeometryData(left, right);
        }
        if (geometry instanceof DifferenceGeometryData difference) {
            GeometryData minuend = mirror(difference.getMinuend(), plane);
            GeometryData subtrahend = mirror(difference.getSubtrahend(), plane);
            if (minuend == null || subtrahend == null) {
                return null;
            }
            return new DifferenceGeometryData(minuend, subtrahend);
        }
        if (geometry instanceof SphereData sphere) {
            return new SphereData(mirrorPoint(sphere.getCenter(), plane), sphere.getRadius());
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return new CylinderGeometryData(
                mirrorPoint(cylinder.getStart(), plane),
                mirrorPoint(cylinder.getEnd(), plane),
                cylinder.getRadius()
            );
        }
        if (geometry instanceof ConeGeometryData cone) {
            return new ConeGeometryData(
                mirrorPoint(cone.getBaseCenter(), plane),
                mirrorPoint(cone.getApex(), plane),
                cone.getBaseRadius()
            );
        }
        if (geometry instanceof FrustumConeGeometryData frustum) {
            return new FrustumConeGeometryData(
                mirrorPoint(frustum.getBaseCenter(), plane),
                mirrorPoint(frustum.getTopCenter(), plane),
                frustum.getBaseRadius(),
                frustum.getTopRadius()
            );
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            return new EllipsoidGeometryData(
                mirrorPoint(ellipsoid.getCenter(), plane),
                ellipsoid.getRadii()
            );
        }
        if (geometry instanceof BoxGeometryData box) {
            Vector3d center = mirrorPoint(box.getCenter(), plane);
            Matrix3d r = reflectionMatrix3(plane.getNormal());
            Matrix3d rm = new Matrix3d(r).mul(box.getOrientationMatrix());
            return new BoxGeometryData(center, box.getHalfExtents(), rm, box.isOriented());
        }
        if (geometry instanceof PrismGeometryData prism) {
            List<Vector3d> base = prism.getBaseVertices();
            List<Vector3d> mirroredBase = new ArrayList<>(base.size());
            for (Vector3d v : base) {
                mirroredBase.add(mirrorPoint(v, plane));
            }
            Vector3d extrusion = mirrorDirection(prism.getExtrusionVector(), plane);
            return new PrismGeometryData(mirroredBase, extrusion);
        }
        if (geometry instanceof TorusGeometryData torus) {
            return new TorusGeometryData(
                mirrorPoint(torus.getCenter(), plane),
                mirrorDirection(torus.getAxis(), plane),
                torus.getMajorRadius(),
                torus.getMinorRadius()
            );
        }
        if (geometry instanceof SquarePyramidGeometryData pyramid) {
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
        if (geometry instanceof OctahedronGeometryData oct) {
            return new OctahedronGeometryData(mirrorPoint(oct.getCenter(), plane), oct.getVertexRadius());
        }
        if (geometry instanceof TetrahedronGeometryData tet) {
            return new TetrahedronGeometryData(mirrorPoint(tet.getCenter(), plane), tet.getEdgeLength());
        }
        return null;
    }

    public static Vector3d mirrorPoint(Vector3d point, PlaneData plane) {
        Vector3d normal = plane.getNormal();
        double distance = plane.signedDistanceTo(point);
        Vector3d displacement = new Vector3d(normal).mul(2.0d * distance);
        return new Vector3d(point).sub(displacement);
    }

    public static Vector3d mirrorDirection(Vector3d direction, PlaneData plane) {
        Vector3d n = plane.getNormal();
        double s = n.dot(direction);
        return new Vector3d(direction).sub(new Vector3d(n).mul(2.0d * s));
    }

    private static Matrix3d reflectionMatrix3(Vector3d normal) {
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
