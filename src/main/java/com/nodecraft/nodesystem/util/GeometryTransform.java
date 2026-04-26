package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.DodecahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.IcosahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a similarity transform (translation, Euler XYZ rotation in degrees, uniform scale) to
 * analytic {@link GeometryData}, using the same world mapping as {@link TransformedSdfData}:
 * {@code world = translation + rotation * (scale * local)}.
 */
public final class GeometryTransform {

    private static final double EPS = 1.0e-9d;

    private GeometryTransform() {
    }

    /**
     * Immutable transform parameters shared across recursive geometry transforms.
     */
    public static final class Spec {
        private final Vector3d translation;
        private final double rotationXDeg;
        private final double rotationYDeg;
        private final double rotationZDeg;
        private final double scale;
        private final Matrix3d rotation;

        public Spec(Vector3d translation, double rotationXDeg, double rotationYDeg, double rotationZDeg, double scale) {
            this.translation = translation == null ? new Vector3d() : new Vector3d(translation);
            this.rotationXDeg = rotationXDeg;
            this.rotationYDeg = rotationYDeg;
            this.rotationZDeg = rotationZDeg;
            this.scale = Math.max(EPS, Math.abs(scale));
            this.rotation = new Matrix3d().rotateXYZ(
                Math.toRadians(rotationXDeg),
                Math.toRadians(rotationYDeg),
                Math.toRadians(rotationZDeg)
            );
        }

        public Vector3d translation() {
            return new Vector3d(translation);
        }

        public double rotationXDeg() {
            return rotationXDeg;
        }

        public double rotationYDeg() {
            return rotationYDeg;
        }

        public double rotationZDeg() {
            return rotationZDeg;
        }

        public double scale() {
            return scale;
        }

        Matrix3d rotationMatrix() {
            return new Matrix3d(rotation);
        }
    }

    public static GeometryData transform(
        GeometryData geometry,
        Vector3d translation,
        double rotationXDeg,
        double rotationYDeg,
        double rotationZDeg,
        double scale
    ) {
        if (geometry == null) {
            return null;
        }
        return transform0(geometry, new Spec(translation, rotationXDeg, rotationYDeg, rotationZDeg, scale));
    }

    private static GeometryData transform0(GeometryData geometry, Spec spec) {
        Vector3d t = spec.translation();
        Matrix3d r = spec.rotationMatrix();
        double s = spec.scale();

        if (geometry instanceof CompositeGeometryData composite) {
            List<GeometryData> out = new ArrayList<>(composite.size());
            for (GeometryData child : composite.getGeometries()) {
                GeometryData transformed = transform0(child, spec);
                if (transformed != null) {
                    out.add(transformed);
                }
            }
            return out.isEmpty() ? null : new CompositeGeometryData(out);
        }
        if (geometry instanceof IntersectionGeometryData intersection) {
            GeometryData left = transform0(intersection.getLeft(), spec);
            GeometryData right = transform0(intersection.getRight(), spec);
            if (left == null || right == null) {
                return null;
            }
            return new IntersectionGeometryData(left, right);
        }
        if (geometry instanceof DifferenceGeometryData difference) {
            GeometryData minuend = transform0(difference.getMinuend(), spec);
            GeometryData subtrahend = transform0(difference.getSubtrahend(), spec);
            if (minuend == null || subtrahend == null) {
                return null;
            }
            return new DifferenceGeometryData(minuend, subtrahend);
        }
        if (geometry instanceof SphereData sphere) {
            return new SphereData(transformPoint(sphere.getCenter(), t, r, s), sphere.getRadius() * s);
        }
        if (geometry instanceof CylinderGeometryData cylinder) {
            return new CylinderGeometryData(
                transformPoint(cylinder.getStart(), t, r, s),
                transformPoint(cylinder.getEnd(), t, r, s),
                cylinder.getRadius() * s
            );
        }
        if (geometry instanceof ConeGeometryData cone) {
            return new ConeGeometryData(
                transformPoint(cone.getBaseCenter(), t, r, s),
                transformPoint(cone.getApex(), t, r, s),
                cone.getBaseRadius() * s
            );
        }
        if (geometry instanceof FrustumConeGeometryData frustum) {
            return new FrustumConeGeometryData(
                transformPoint(frustum.getBaseCenter(), t, r, s),
                transformPoint(frustum.getTopCenter(), t, r, s),
                frustum.getBaseRadius() * s,
                frustum.getTopRadius() * s
            );
        }
        if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            Vector3d radii = ellipsoid.getRadii();
            return new EllipsoidGeometryData(
                transformPoint(ellipsoid.getCenter(), t, r, s),
                new Vector3d(radii.x * s, radii.y * s, radii.z * s)
            );
        }
        if (geometry instanceof HemisphereGeometryData hemisphere) {
            Vector3d ax = rotateUnitDirection(r, hemisphere.getAxis());
            if (ax == null) {
                return null;
            }
            return new HemisphereGeometryData(
                transformPoint(hemisphere.getCenter(), t, r, s),
                ax,
                hemisphere.getRadius() * s
            );
        }
        if (geometry instanceof BoxGeometryData box) {
            Vector3d center = transformPoint(box.getCenter(), t, r, s);
            Matrix3d newOrientation = new Matrix3d(r).mul(box.getOrientationMatrix());
            Vector3d half = box.getHalfExtents();
            return new BoxGeometryData(
                center,
                new Vector3d(half.x * s, half.y * s, half.z * s),
                newOrientation,
                box.isOriented()
            );
        }
        if (geometry instanceof PrismGeometryData prism) {
            List<Vector3d> base = prism.getBaseVertices();
            List<Vector3d> outBase = new ArrayList<>(base.size());
            for (Vector3d v : base) {
                outBase.add(transformPoint(v, t, r, s));
            }
            Vector3d extrusion = transformVector(prism.getExtrusionVector(), r, s);
            return new PrismGeometryData(outBase, extrusion);
        }
        if (geometry instanceof TorusGeometryData torus) {
            Vector3d axis = rotateUnitDirection(r, torus.getAxis());
            if (axis == null) {
                return null;
            }
            return new TorusGeometryData(
                transformPoint(torus.getCenter(), t, r, s),
                axis,
                torus.getMajorRadius() * s,
                torus.getMinorRadius() * s
            );
        }
        if (geometry instanceof SquarePyramidGeometryData pyramid) {
            Vector3d baseCenter = transformPoint(pyramid.getBaseCenter(), t, r, s);
            Vector3d xAxis = rotateUnitDirection(r, pyramid.getXAxis());
            Vector3d yRaw = rotateUnitDirection(r, pyramid.getYAxis());
            Vector3d nRaw = rotateUnitDirection(r, pyramid.getNormal());
            if (xAxis == null || yRaw == null || nRaw == null) {
                return null;
            }
            Vector3d normal = new Vector3d(xAxis).cross(yRaw);
            if (normal.lengthSquared() < 1.0e-18d) {
                normal.set(nRaw);
            }
            normal.normalize();
            Vector3d yAxis = new Vector3d(normal).cross(xAxis).normalize();
            Vector3d apex = transformPoint(pyramid.getApex(), t, r, s);
            double height = new Vector3d(apex).sub(baseCenter).dot(normal);
            if (height < 1.0e-9d) {
                normal.negate();
                height = new Vector3d(apex).sub(baseCenter).dot(normal);
            }
            if (height < 1.0e-9d) {
                return null;
            }
            return new SquarePyramidGeometryData(
                baseCenter,
                xAxis,
                yAxis,
                normal,
                pyramid.getBaseSize() * s,
                height
            );
        }
        if (geometry instanceof OctahedronGeometryData oct) {
            Matrix3d rLocal = new Matrix3d(r).mul(oct.getOrientationMatrix());
            return new OctahedronGeometryData(transformPoint(oct.getCenter(), t, r, s), oct.getVertexRadius() * s, rLocal);
        }
        if (geometry instanceof TetrahedronGeometryData tet) {
            Matrix3d rLocal = new Matrix3d(r).mul(tet.getOrientationMatrix());
            return new TetrahedronGeometryData(transformPoint(tet.getCenter(), t, r, s), tet.getEdgeLength() * s, rLocal);
        }
        if (geometry instanceof IcosahedronGeometryData ico) {
            Matrix3d rLocal = new Matrix3d(r).mul(ico.getOrientationMatrix());
            return new IcosahedronGeometryData(transformPoint(ico.getCenter(), t, r, s), ico.getEdgeLength() * s, rLocal);
        }
        if (geometry instanceof DodecahedronGeometryData dod) {
            Matrix3d rLocal = new Matrix3d(r).mul(dod.getOrientationMatrix());
            return new DodecahedronGeometryData(transformPoint(dod.getCenter(), t, r, s), dod.getEdgeLength() * s, rLocal);
        }
        if (geometry instanceof SdfGeometryData sdfGeom) {
            SignedDistanceFieldData sdf = sdfGeom.getSdf();
            if (sdf == null) {
                return null;
            }
            SignedDistanceFieldData wrapped = new TransformedSdfData(
                sdf,
                spec.translation(),
                spec.rotationXDeg(),
                spec.rotationYDeg(),
                spec.rotationZDeg(),
                spec.scale()
            );
            Vector3d min = sdfGeom.getMin();
            Vector3d max = sdfGeom.getMax();
            Vector3d[] corners = {
                new Vector3d(min.x, min.y, min.z),
                new Vector3d(max.x, min.y, min.z),
                new Vector3d(min.x, max.y, min.z),
                new Vector3d(max.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z),
                new Vector3d(max.x, min.y, max.z),
                new Vector3d(min.x, max.y, max.z),
                new Vector3d(max.x, max.y, max.z)
            };
            Vector3d newMin = new Vector3d(Double.POSITIVE_INFINITY);
            Vector3d newMax = new Vector3d(Double.NEGATIVE_INFINITY);
            for (Vector3d corner : corners) {
                Vector3d p = transformPoint(corner, t, r, s);
                newMin.min(p);
                newMax.max(p);
            }
            return new SdfGeometryData(wrapped, newMin, newMax, sdfGeom.getIsoValue());
        }
        return null;
    }

    private static Vector3d transformPoint(Vector3d point, Vector3d translation, Matrix3d rotation, double scale) {
        Vector3d v = new Vector3d(point).mul(scale);
        rotation.transform(v);
        v.add(translation);
        return v;
    }

    private static Vector3d transformVector(Vector3d vector, Matrix3d rotation, double scale) {
        return rotation.transform(new Vector3d(vector).mul(scale), new Vector3d());
    }

    private static Vector3d rotateUnitDirection(Matrix3d rotation, Vector3d direction) {
        Vector3d d = rotation.transform(new Vector3d(direction), new Vector3d());
        if (d.lengthSquared() < 1.0e-18d) {
            return null;
        }
        d.normalize();
        return d;
    }
}
