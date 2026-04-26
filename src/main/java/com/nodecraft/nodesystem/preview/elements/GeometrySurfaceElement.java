package com.nodecraft.nodesystem.preview.elements;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.util.PlatonicSolidTables;
import com.nodecraft.nodesystem.preview.AbstractPreviewElement;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import com.nodecraft.nodesystem.preview.PreviewRenderer;
import com.nodecraft.nodesystem.preview.protocol.PreviewGeometryPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Analytic geometry preview element that renders semi-transparent surfaces and optional wireframes.
 */
public class GeometrySurfaceElement extends AbstractPreviewElement {

    private static final int MIN_QUALITY = 8;
    private static final int MAX_QUALITY = 64;

    private volatile List<Triangle> triangles = new ArrayList<>();
    private volatile List<Segment> segments = new ArrayList<>();

    private Vec3d boundsCenter = Vec3d.ZERO;
    private double boundsRadius = 0.0d;

    private Vector3f fillColor = new Vector3f(0.30f, 0.84f, 1.0f);
    private Vector3f lineColor;
    private boolean showFill = true;
    private boolean showOutline = true;
    private float lineWidth = 2.0f;
    private float lineAlphaScale = 0.95f;
    private volatile long lastVisibilityLogAtMs = 0L;

    public GeometrySurfaceElement(String id, String ownerNodeId, Object data, PreviewOptions options) {
        super(id, ownerNodeId, data, options);
        this.renderPriority = 16;

        if (options.color != null) {
            this.fillColor = new Vector3f(options.color);
        }
        if (options.tintColor != null) {
            this.lineColor = new Vector3f(options.tintColor);
        } else {
            this.lineColor = new Vector3f(
                Math.max(0.0f, fillColor.x() * 0.25f),
                Math.max(0.0f, fillColor.y() * 0.25f),
                Math.max(0.0f, fillColor.z() * 0.25f)
            );
        }
        if (options.showFill != null) {
            this.showFill = options.showFill;
        }
        if (options.showOutline != null) {
            this.showOutline = options.showOutline;
        }
        if (options.lineWidth != null) {
            this.lineWidth = Math.max(0.25f, options.lineWidth);
        }

        // AbstractPreviewElement invokes processData() inside super(...), but subclass field initializers run
        // afterwards and can reset mesh/bounds fields. Reprocess once here to keep final state consistent.
        processData(data);
    }

    @Override
    protected void processData(Object data) {
        List<GeometryData> geometries = new ArrayList<>();
        collectGeometry(data, geometries);

        MeshBuilder builder = new MeshBuilder();
        int quality = resolveQuality();

        for (GeometryData geometry : geometries) {
            appendGeometryMesh(builder, geometry, quality);
        }

        triangles = builder.triangles;
        segments = builder.segments;
        boundsCenter = builder.computeCenter();
        boundsRadius = builder.computeRadius(boundsCenter);
        NodeCraft.LOGGER.info(
            "GeometrySurfaceElement[{}] mesh built: triangles={}, segments={}, center=({}, {}, {}), radius={}",
            getOwnerNodeId(),
            triangles.size(),
            segments.size(),
            boundsCenter.x,
            boundsCenter.y,
            boundsCenter.z,
            boundsRadius
        );
    }

    private int resolveQuality() {
        int quality = 20;
        if (options != null && options.particleDensity != null) {
            quality = options.particleDensity;
        }
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, quality));
    }

    private void collectGeometry(Object data, List<GeometryData> target) {
        if (data == null) {
            return;
        }
        if (data instanceof PreviewGeometryPayload payload) {
            collectGeometry(payload.getGeometry(), target);
            return;
        }

        if (data instanceof GeometryData geometry) {
            collectSingleGeometry(geometry, target);
            return;
        }

        if (data instanceof List<?> list) {
            for (Object item : list) {
                collectGeometry(item, target);
            }
        }
    }

    private void collectSingleGeometry(GeometryData geometry, List<GeometryData> target) {
        if (geometry == null) {
            return;
        }
        if (geometry instanceof CompositeGeometryData composite) {
            for (GeometryData child : composite.getGeometries()) {
                collectSingleGeometry(child, target);
            }
            return;
        }
        target.add(geometry);
    }

    private void appendGeometryMesh(MeshBuilder builder, GeometryData geometry, int quality) {
        if (geometry instanceof SphereData sphere) {
            appendSphere(builder, sphere.getCenter(), sphere.getRadius(), quality);
        } else if (geometry instanceof EllipsoidGeometryData ellipsoid) {
            appendEllipsoid(builder, ellipsoid.getCenter(), ellipsoid.getRadii(), quality);
        } else if (geometry instanceof HemisphereGeometryData hemisphere) {
            appendHemisphere(builder, hemisphere, quality);
        } else if (geometry instanceof CylinderGeometryData cylinder) {
            appendCylinder(builder, cylinder, quality);
        } else if (geometry instanceof ConeGeometryData cone) {
            appendCone(builder, cone, quality);
        } else if (geometry instanceof FrustumConeGeometryData frustum) {
            appendFrustumCone(builder, frustum, quality);
        } else if (geometry instanceof TorusGeometryData torus) {
            appendTorus(builder, torus, quality);
        } else if (geometry instanceof BoxGeometryData box) {
            appendBox(builder, box);
        } else if (geometry instanceof PrismGeometryData prism) {
            appendPrism(builder, prism);
        } else if (geometry instanceof TetrahedronGeometryData tetrahedron) {
            appendTetrahedron(builder, tetrahedron);
        } else if (geometry instanceof OctahedronGeometryData octahedron) {
            appendOctahedron(builder, octahedron);
        } else if (geometry instanceof IcosahedronGeometryData icosahedron) {
            appendIndexedTriangleMesh(builder, icosahedron.getVertices(), PlatonicSolidTables.icosahedronTriangleIndices());
        } else if (geometry instanceof DodecahedronGeometryData dodecahedron) {
            appendIndexedTriangleMesh(builder, dodecahedron.getVertices(), PlatonicSolidTables.dodecahedronTriangleIndices());
        }
    }

    private void appendIndexedTriangleMesh(MeshBuilder builder, List<Vector3d> vertices, int[] triangleIndices) {
        if (vertices == null || triangleIndices == null) {
            return;
        }
        for (int i = 0; i + 2 < triangleIndices.length; i += 3) {
            int ia = triangleIndices[i];
            int ib = triangleIndices[i + 1];
            int ic = triangleIndices[i + 2];
            if (ia < 0 || ib < 0 || ic < 0 || ia >= vertices.size() || ib >= vertices.size() || ic >= vertices.size()) {
                continue;
            }
            Vec3d a = toVec3d(vertices.get(ia));
            Vec3d b = toVec3d(vertices.get(ib));
            Vec3d c = toVec3d(vertices.get(ic));
            builder.addTriangle(a, b, c);
            builder.addSegment(a, b);
            builder.addSegment(b, c);
            builder.addSegment(c, a);
        }
    }

    private void appendSphere(MeshBuilder builder, Vector3d center, double radius, int quality) {
        if (radius <= 1.0e-6d) {
            return;
        }
        int latSegments = quality;
        int lonSegments = quality * 2;

        for (int lat = 0; lat < latSegments; lat++) {
            double v0 = (double) lat / latSegments;
            double v1 = (double) (lat + 1) / latSegments;
            double phi0 = Math.PI * v0;
            double phi1 = Math.PI * v1;

            for (int lon = 0; lon < lonSegments; lon++) {
                double u0 = (double) lon / lonSegments;
                double u1 = (double) (lon + 1) / lonSegments;
                double theta0 = 2.0d * Math.PI * u0;
                double theta1 = 2.0d * Math.PI * u1;

                Vec3d p00 = sphericalPoint(center, radius, phi0, theta0);
                Vec3d p01 = sphericalPoint(center, radius, phi0, theta1);
                Vec3d p10 = sphericalPoint(center, radius, phi1, theta0);
                Vec3d p11 = sphericalPoint(center, radius, phi1, theta1);

                if (lat > 0) {
                    builder.addTriangle(p00, p10, p01);
                }
                if (lat < latSegments - 1) {
                    builder.addTriangle(p01, p10, p11);
                }

                builder.addSegment(p00, p01);
                builder.addSegment(p00, p10);
            }
        }
    }

    private void appendHemisphere(MeshBuilder builder, HemisphereGeometryData hemisphere, int quality) {
        Vector3d center = hemisphere.getCenter();
        Vector3d pole = hemisphere.getAxis();
        double radius = hemisphere.getRadius();
        if (radius <= 1.0e-6d) {
            return;
        }

        Vector3d basisU = orthogonalUnit(pole);
        Vector3d basisV = new Vector3d(pole).cross(basisU).normalize();

        int latSegments = quality;
        int lonSegments = quality * 2;

        for (int lat = 0; lat < latSegments; lat++) {
            double v0 = (double) lat / latSegments;
            double v1 = (double) (lat + 1) / latSegments;
            double phi0 = (Math.PI * 0.5d) * v0;
            double phi1 = (Math.PI * 0.5d) * v1;

            for (int lon = 0; lon < lonSegments; lon++) {
                double u0 = (double) lon / lonSegments;
                double u1 = (double) (lon + 1) / lonSegments;
                double theta0 = 2.0d * Math.PI * u0;
                double theta1 = 2.0d * Math.PI * u1;

                Vec3d p00 = orientedHemispherePoint(center, pole, basisU, basisV, radius, phi0, theta0);
                Vec3d p01 = orientedHemispherePoint(center, pole, basisU, basisV, radius, phi0, theta1);
                Vec3d p10 = orientedHemispherePoint(center, pole, basisU, basisV, radius, phi1, theta0);
                Vec3d p11 = orientedHemispherePoint(center, pole, basisU, basisV, radius, phi1, theta1);

                if (lat > 0) {
                    builder.addTriangle(p00, p10, p01);
                }
                if (lat < latSegments - 1) {
                    builder.addTriangle(p01, p10, p11);
                }

                builder.addSegment(p00, p01);
                builder.addSegment(p00, p10);
            }
        }

        Vec3d diskCenter = toVec3d(center);
        for (int lon = 0; lon < lonSegments; lon++) {
            double u0 = (double) lon / lonSegments;
            double u1 = (double) (lon + 1) / lonSegments;
            double theta0 = 2.0d * Math.PI * u0;
            double theta1 = 2.0d * Math.PI * u1;
            Vec3d p0 = orientedHemispherePoint(center, pole, basisU, basisV, radius, Math.PI * 0.5d, theta0);
            Vec3d p1 = orientedHemispherePoint(center, pole, basisU, basisV, radius, Math.PI * 0.5d, theta1);
            builder.addTriangle(diskCenter, p1, p0);
            builder.addSegment(p0, p1);
        }
    }

    private Vec3d orientedHemispherePoint(Vector3d center,
                                          Vector3d pole,
                                          Vector3d basisU,
                                          Vector3d basisV,
                                          double radius,
                                          double phi,
                                          double theta) {
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        Vector3d dir = new Vector3d(basisU).mul(sinPhi * Math.cos(theta))
            .add(new Vector3d(basisV).mul(sinPhi * Math.sin(theta)))
            .add(new Vector3d(pole).mul(cosPhi));
        return toVec3d(new Vector3d(center).add(dir.mul(radius)));
    }

    private void appendEllipsoid(MeshBuilder builder, Vector3d center, Vector3d radii, int quality) {
        if (radii.x <= 1.0e-6d || radii.y <= 1.0e-6d || radii.z <= 1.0e-6d) {
            return;
        }
        int latSegments = quality;
        int lonSegments = quality * 2;

        for (int lat = 0; lat < latSegments; lat++) {
            double v0 = (double) lat / latSegments;
            double v1 = (double) (lat + 1) / latSegments;
            double phi0 = Math.PI * v0;
            double phi1 = Math.PI * v1;

            for (int lon = 0; lon < lonSegments; lon++) {
                double u0 = (double) lon / lonSegments;
                double u1 = (double) (lon + 1) / lonSegments;
                double theta0 = 2.0d * Math.PI * u0;
                double theta1 = 2.0d * Math.PI * u1;

                Vec3d p00 = ellipsoidPoint(center, radii, phi0, theta0);
                Vec3d p01 = ellipsoidPoint(center, radii, phi0, theta1);
                Vec3d p10 = ellipsoidPoint(center, radii, phi1, theta0);
                Vec3d p11 = ellipsoidPoint(center, radii, phi1, theta1);

                if (lat > 0) {
                    builder.addTriangle(p00, p10, p01);
                }
                if (lat < latSegments - 1) {
                    builder.addTriangle(p01, p10, p11);
                }

                builder.addSegment(p00, p01);
                builder.addSegment(p00, p10);
            }
        }
    }

    private void appendCylinder(MeshBuilder builder, CylinderGeometryData cylinder, int quality) {
        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        double radius = cylinder.getRadius();
        Vector3d axis = new Vector3d(end).sub(start);
        double height = axis.length();
        if (height <= 1.0e-6d || radius <= 1.0e-6d) {
            return;
        }

        Vector3d axisDir = axis.normalize();
        Vector3d basisU = orthogonalUnit(axisDir);
        Vector3d basisV = new Vector3d(axisDir).cross(basisU).normalize();

        int segments = quality * 2;
        List<Vec3d> bottom = new ArrayList<>(segments);
        List<Vec3d> top = new ArrayList<>(segments);

        for (int i = 0; i < segments; i++) {
            double t = 2.0d * Math.PI * i / segments;
            double cs = Math.cos(t);
            double sn = Math.sin(t);
            Vector3d offset = new Vector3d(basisU).mul(radius * cs).add(new Vector3d(basisV).mul(radius * sn));
            bottom.add(toVec3d(new Vector3d(start).add(offset)));
            top.add(toVec3d(new Vector3d(end).add(offset)));
        }

        Vec3d bottomCenter = toVec3d(start);
        Vec3d topCenter = toVec3d(end);

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            Vec3d b0 = bottom.get(i);
            Vec3d b1 = bottom.get(next);
            Vec3d t0 = top.get(i);
            Vec3d t1 = top.get(next);

            builder.addTriangle(b0, t0, b1);
            builder.addTriangle(b1, t0, t1);
            builder.addTriangle(bottomCenter, b1, b0);
            builder.addTriangle(topCenter, t0, t1);

            builder.addSegment(b0, b1);
            builder.addSegment(t0, t1);
            builder.addSegment(b0, t0);
        }
    }

    private void appendFrustumCone(MeshBuilder builder, FrustumConeGeometryData frustum, int quality) {
        Vector3d start = frustum.getBaseCenter();
        Vector3d end = frustum.getTopCenter();
        double radiusBottom = frustum.getBaseRadius();
        double radiusTop = frustum.getTopRadius();
        Vector3d axis = new Vector3d(end).sub(start);
        double height = axis.length();
        if (height <= 1.0e-6d || (radiusBottom <= 1.0e-6d && radiusTop <= 1.0e-6d)) {
            return;
        }

        Vector3d axisDir = axis.normalize();
        Vector3d basisU = orthogonalUnit(axisDir);
        Vector3d basisV = new Vector3d(axisDir).cross(basisU).normalize();

        int segments = quality * 2;
        List<Vec3d> bottom = new ArrayList<>(segments);
        List<Vec3d> top = new ArrayList<>(segments);

        for (int i = 0; i < segments; i++) {
            double t = 2.0d * Math.PI * i / segments;
            double cs = Math.cos(t);
            double sn = Math.sin(t);
            Vector3d offset = new Vector3d(basisU).mul(radiusBottom * cs).add(new Vector3d(basisV).mul(radiusBottom * sn));
            bottom.add(toVec3d(new Vector3d(start).add(offset)));
            Vector3d offsetTop = new Vector3d(basisU).mul(radiusTop * cs).add(new Vector3d(basisV).mul(radiusTop * sn));
            top.add(toVec3d(new Vector3d(end).add(offsetTop)));
        }

        Vec3d bottomCenter = toVec3d(start);
        Vec3d topCenter = toVec3d(end);

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            Vec3d b0 = bottom.get(i);
            Vec3d b1 = bottom.get(next);
            Vec3d t0 = top.get(i);
            Vec3d t1 = top.get(next);

            builder.addTriangle(b0, t0, b1);
            builder.addTriangle(b1, t0, t1);
            if (radiusBottom > 1.0e-6d) {
                builder.addTriangle(bottomCenter, b1, b0);
            }
            if (radiusTop > 1.0e-6d) {
                builder.addTriangle(topCenter, t0, t1);
            }

            builder.addSegment(b0, b1);
            builder.addSegment(t0, t1);
            builder.addSegment(b0, t0);
        }
    }

    private void appendCone(MeshBuilder builder, ConeGeometryData cone, int quality) {
        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d apex = cone.getApex();
        double radius = cone.getBaseRadius();

        Vector3d axis = new Vector3d(apex).sub(baseCenter);
        double height = axis.length();
        if (height <= 1.0e-6d || radius <= 1.0e-6d) {
            return;
        }

        Vector3d axisDir = axis.normalize();
        Vector3d basisU = orthogonalUnit(axisDir);
        Vector3d basisV = new Vector3d(axisDir).cross(basisU).normalize();

        int segments = quality * 2;
        List<Vec3d> ring = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            double t = 2.0d * Math.PI * i / segments;
            double cs = Math.cos(t);
            double sn = Math.sin(t);
            Vector3d offset = new Vector3d(basisU).mul(radius * cs).add(new Vector3d(basisV).mul(radius * sn));
            ring.add(toVec3d(new Vector3d(baseCenter).add(offset)));
        }

        Vec3d apexVec = toVec3d(apex);
        Vec3d center = toVec3d(baseCenter);

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            Vec3d p0 = ring.get(i);
            Vec3d p1 = ring.get(next);

            builder.addTriangle(apexVec, p0, p1);
            builder.addTriangle(center, p1, p0);

            builder.addSegment(p0, p1);
            builder.addSegment(p0, apexVec);
        }
    }

    private void appendTorus(MeshBuilder builder, TorusGeometryData torus, int quality) {
        Vector3d center = torus.getCenter();
        Vector3d axis = torus.getAxis();
        double major = torus.getMajorRadius();
        double minor = torus.getMinorRadius();

        if (major <= 1.0e-6d || minor <= 1.0e-6d || axis.lengthSquared() <= 1.0e-12d) {
            return;
        }

        Vector3d axisDir = new Vector3d(axis).normalize();
        Vector3d basisU = orthogonalUnit(axisDir);
        Vector3d basisV = new Vector3d(axisDir).cross(basisU).normalize();

        int ringSegments = quality * 2;
        int tubeSegments = quality;

        for (int i = 0; i < ringSegments; i++) {
            int inext = (i + 1) % ringSegments;
            double u0 = 2.0d * Math.PI * i / ringSegments;
            double u1 = 2.0d * Math.PI * inext / ringSegments;

            for (int j = 0; j < tubeSegments; j++) {
                int jnext = (j + 1) % tubeSegments;
                double v0 = 2.0d * Math.PI * j / tubeSegments;
                double v1 = 2.0d * Math.PI * jnext / tubeSegments;

                Vec3d p00 = torusPoint(center, axisDir, basisU, basisV, major, minor, u0, v0);
                Vec3d p01 = torusPoint(center, axisDir, basisU, basisV, major, minor, u0, v1);
                Vec3d p10 = torusPoint(center, axisDir, basisU, basisV, major, minor, u1, v0);
                Vec3d p11 = torusPoint(center, axisDir, basisU, basisV, major, minor, u1, v1);

                builder.addTriangle(p00, p10, p01);
                builder.addTriangle(p01, p10, p11);

                builder.addSegment(p00, p01);
                builder.addSegment(p00, p10);
            }
        }
    }

    private void appendBox(MeshBuilder builder, BoxGeometryData box) {
        List<Vector3d> corners = box.getCorners();
        if (corners.size() != 8) {
            return;
        }

        // 6 faces, each split into two triangles.
        int[][] faces = {
            {0, 1, 2, 3},
            {4, 5, 6, 7},
            {0, 4, 5, 1},
            {3, 2, 6, 7},
            {0, 3, 7, 4},
            {1, 5, 6, 2}
        };

        for (int[] face : faces) {
            Vec3d p0 = toVec3d(corners.get(face[0]));
            Vec3d p1 = toVec3d(corners.get(face[1]));
            Vec3d p2 = toVec3d(corners.get(face[2]));
            Vec3d p3 = toVec3d(corners.get(face[3]));

            builder.addTriangle(p0, p1, p2);
            builder.addTriangle(p0, p2, p3);

            builder.addSegment(p0, p1);
            builder.addSegment(p1, p2);
            builder.addSegment(p2, p3);
            builder.addSegment(p3, p0);
        }
    }

    private void appendPrism(MeshBuilder builder, PrismGeometryData prism) {
        List<Vector3d> base = prism.getBaseVertices();
        List<Vector3d> top = prism.getTopVertices();
        int n = Math.min(base.size(), top.size());
        if (n < 3) {
            return;
        }

        Vec3d base0 = toVec3d(base.getFirst());
        Vec3d top0 = toVec3d(top.getFirst());

        for (int i = 1; i < n - 1; i++) {
            builder.addTriangle(base0, toVec3d(base.get(i + 1)), toVec3d(base.get(i)));
            builder.addTriangle(top0, toVec3d(top.get(i)), toVec3d(top.get(i + 1)));
        }

        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            Vec3d b0 = toVec3d(base.get(i));
            Vec3d b1 = toVec3d(base.get(next));
            Vec3d t0 = toVec3d(top.get(i));
            Vec3d t1 = toVec3d(top.get(next));

            builder.addTriangle(b0, t0, b1);
            builder.addTriangle(b1, t0, t1);

            builder.addSegment(b0, b1);
            builder.addSegment(t0, t1);
            builder.addSegment(b0, t0);
        }
    }

    private void appendTetrahedron(MeshBuilder builder, TetrahedronGeometryData tetrahedron) {
        List<Vector3d> vertices = tetrahedron.getVertices();
        if (vertices.size() != 4) {
            return;
        }

        int[][] faces = {
            {0, 1, 2},
            {0, 3, 1},
            {0, 2, 3},
            {1, 3, 2}
        };

        for (int[] face : faces) {
            builder.addTriangle(
                toVec3d(vertices.get(face[0])),
                toVec3d(vertices.get(face[1])),
                toVec3d(vertices.get(face[2]))
            );
        }

        int[][] edges = {
            {0, 1}, {0, 2}, {0, 3}, {1, 2}, {2, 3}, {3, 1}
        };
        for (int[] edge : edges) {
            builder.addSegment(toVec3d(vertices.get(edge[0])), toVec3d(vertices.get(edge[1])));
        }
    }

    private void appendOctahedron(MeshBuilder builder, OctahedronGeometryData octahedron) {
        List<Vector3d> vertices = octahedron.getVertices();
        if (vertices.size() != 6) {
            return;
        }

        int[][] faces = {
            {0, 2, 4}, {4, 2, 1}, {1, 2, 5}, {5, 2, 0},
            {0, 4, 3}, {4, 1, 3}, {1, 5, 3}, {5, 0, 3}
        };

        for (int[] face : faces) {
            builder.addTriangle(
                toVec3d(vertices.get(face[0])),
                toVec3d(vertices.get(face[1])),
                toVec3d(vertices.get(face[2]))
            );
        }

        int[][] edges = {
            {0, 2}, {2, 1}, {1, 3}, {3, 0},
            {4, 2}, {4, 1}, {4, 3}, {4, 0},
            {5, 2}, {5, 1}, {5, 3}, {5, 0}
        };
        for (int[] edge : edges) {
            builder.addSegment(toVec3d(vertices.get(edge[0])), toVec3d(vertices.get(edge[1])));
        }
    }

    @Override
    public void render(MatrixStack matrices, Camera camera, float partialTicks, float globalOpacity) {
        List<Triangle> trianglesSnapshot = triangles;
        List<Segment> segmentsSnapshot = segments;
        if (trianglesSnapshot.isEmpty() && segmentsSnapshot.isEmpty()) {
            return;
        }

        float alpha = opacity * globalOpacity;
        if (alpha <= 0.01f) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider provider = PreviewRenderer.getInstance().getActiveVertexConsumers();
        VertexConsumerProvider.Immediate immediate = null;
        boolean flushImmediately = false;
        if (provider == null) {
            immediate = client.getBufferBuilders().getEntityVertexConsumers();
            provider = immediate;
            flushImmediately = true;
        }
        Vec3d cameraPos = camera.getCameraPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (showFill && !trianglesSnapshot.isEmpty()) {
            VertexConsumer fillConsumer = provider.getBuffer(RenderLayers.debugFilledBox());
            for (Triangle triangle : trianglesSnapshot) {
                addTriangle(fillConsumer, matrix, triangle, cameraPos, fillColor, alpha);
            }
        }

        if (showOutline && !segmentsSnapshot.isEmpty()) {
            VertexConsumer lineConsumer = provider.getBuffer(RenderLayers.lines());
            for (Segment segment : segmentsSnapshot) {
                addSegment(lineConsumer, segment, cameraPos, lineColor, alpha * lineAlphaScale);
            }
        }

        if (flushImmediately && immediate != null) {
            immediate.draw();
        }
    }

    private void addTriangle(
        VertexConsumer consumer,
        Matrix4f matrix,
        Triangle triangle,
        Vec3d cameraPos,
        Vector3f color,
        float alpha
    ) {
        Vec3d a = triangle.a.subtract(cameraPos);
        Vec3d b = triangle.b.subtract(cameraPos);
        Vec3d c = triangle.c.subtract(cameraPos);

        // debugFilledBox layer is quad-oriented; write each triangle as a degenerate quad
        // so winding and primitive boundaries stay stable for interior/exterior views.
        emitDegenerateQuadForTriangle(consumer, matrix, a, b, c, color, alpha, triangle.normal);

        // Draw both winding orders to avoid back-face culling hiding fill surfaces.
        Vector3f opposite = new Vector3f(triangle.normal).mul(-1.0f);
        emitDegenerateQuadForTriangle(consumer, matrix, c, b, a, color, alpha, opposite);
    }

    private void emitDegenerateQuadForTriangle(
        VertexConsumer consumer,
        Matrix4f matrix,
        Vec3d a,
        Vec3d b,
        Vec3d c,
        Vector3f color,
        float alpha,
        Vector3f normal
    ) {
        fullBrightVertex(consumer, matrix, (float) a.x, (float) a.y, (float) a.z, color, alpha, normal);
        fullBrightVertex(consumer, matrix, (float) b.x, (float) b.y, (float) b.z, color, alpha, normal);
        fullBrightVertex(consumer, matrix, (float) c.x, (float) c.y, (float) c.z, color, alpha, normal);
        // Fourth vertex duplicates the third to form a degenerate quad.
        fullBrightVertex(consumer, matrix, (float) c.x, (float) c.y, (float) c.z, color, alpha, normal);
    }

    private void fullBrightVertex(
        VertexConsumer consumer,
        Matrix4f matrix,
        float x,
        float y,
        float z,
        Vector3f color,
        float alpha,
        Vector3f normal
    ) {
        consumer.vertex(matrix, x, y, z)
            .color(color.x(), color.y(), color.z(), alpha)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(normal.x, normal.y, normal.z);
    }

    private void addSegment(VertexConsumer consumer, Segment segment, Vec3d cameraPos, Vector3f color, float alpha) {
        Vec3d start = segment.start.subtract(cameraPos);
        Vec3d end = segment.end.subtract(cameraPos);

        Vector3f normal = new Vector3f((float) (end.x - start.x), (float) (end.y - start.y), (float) (end.z - start.z));
        if (normal.lengthSquared() <= 1.0e-12f) {
            normal.set(0.0f, 1.0f, 0.0f);
        } else {
            normal.normalize();
        }

        consumer.vertex((float) start.x, (float) start.y, (float) start.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(Math.max(0.25f, lineWidth));
        consumer.vertex((float) end.x, (float) end.y, (float) end.z)
            .color(color.x(), color.y(), color.z(), alpha)
            .normal(normal.x, normal.y, normal.z)
            .lineWidth(Math.max(0.25f, lineWidth));
    }

    @Override
    public boolean shouldRender(Camera camera) {
        if (isExpired()) {
            return false;
        }
        ensureBoundsForVisibility();
        double distance = camera.getCameraPos().distanceTo(boundsCenter);
        float maxDistance = PreviewRenderer.getInstance().getSettings().maxRenderDistance;
        boolean visibleByDistance = distance <= maxDistance + boundsRadius;
        long now = System.currentTimeMillis();
        if (!visibleByDistance && now - lastVisibilityLogAtMs > 2000L) {
            lastVisibilityLogAtMs = now;
            NodeCraft.LOGGER.info(
                "GeometrySurfaceElement[{}:{}] shouldRender=false: distance={}, maxDistance={}, radius={}, triangles={}, segments={}, cam=({}, {}, {}), center=({}, {}, {})",
                getOwnerNodeId(),
                getId(),
                distance,
                maxDistance,
                boundsRadius,
                triangles.size(),
                segments.size(),
                camera.getCameraPos().x,
                camera.getCameraPos().y,
                camera.getCameraPos().z,
                boundsCenter.x,
                boundsCenter.y,
                boundsCenter.z
            );
        }
        return visibleByDistance;
    }

    private void ensureBoundsForVisibility() {
        if (boundsRadius > 0.0d) {
            return;
        }

        List<Triangle> trianglesSnapshot = triangles;
        List<Segment> segmentsSnapshot = segments;
        if (trianglesSnapshot.isEmpty() && segmentsSnapshot.isEmpty()) {
            return;
        }

        MeshBuilder rebuild = new MeshBuilder();
        for (Triangle triangle : trianglesSnapshot) {
            rebuild.include(triangle.a);
            rebuild.include(triangle.b);
            rebuild.include(triangle.c);
        }
        for (Segment segment : segmentsSnapshot) {
            rebuild.include(segment.start);
            rebuild.include(segment.end);
        }

        Vec3d recomputedCenter = rebuild.computeCenter();
        double recomputedRadius = rebuild.computeRadius(recomputedCenter);
        if (recomputedRadius > 0.0d) {
            boundsCenter = recomputedCenter;
            boundsRadius = recomputedRadius;
            NodeCraft.LOGGER.info(
                "GeometrySurfaceElement[{}:{}] repaired bounds: center=({}, {}, {}), radius={}",
                getOwnerNodeId(),
                getId(),
                boundsCenter.x,
                boundsCenter.y,
                boundsCenter.z,
                boundsRadius
            );
        }
    }

    @Override
    public void cleanup() {
        triangles = new ArrayList<>();
        segments = new ArrayList<>();
        boundsCenter = Vec3d.ZERO;
        boundsRadius = 0.0d;
    }

    private Vec3d sphericalPoint(Vector3d center, double radius, double phi, double theta) {
        double sinPhi = Math.sin(phi);
        double x = center.x + radius * sinPhi * Math.cos(theta);
        double y = center.y + radius * Math.cos(phi);
        double z = center.z + radius * sinPhi * Math.sin(theta);
        return new Vec3d(x, y, z);
    }

    private Vec3d ellipsoidPoint(Vector3d center, Vector3d radii, double phi, double theta) {
        double sinPhi = Math.sin(phi);
        double x = center.x + radii.x * sinPhi * Math.cos(theta);
        double y = center.y + radii.y * Math.cos(phi);
        double z = center.z + radii.z * sinPhi * Math.sin(theta);
        return new Vec3d(x, y, z);
    }

    private Vec3d torusPoint(Vector3d center, Vector3d axis, Vector3d basisU, Vector3d basisV,
                             double majorRadius, double minorRadius, double u, double v) {
        double cu = Math.cos(u);
        double su = Math.sin(u);
        double cv = Math.cos(v);
        double sv = Math.sin(v);

        Vector3d ringDir = new Vector3d(basisU).mul(cu).add(new Vector3d(basisV).mul(su));
        Vector3d ringCenter = new Vector3d(center).add(new Vector3d(ringDir).mul(majorRadius));
        Vector3d tubeDir = new Vector3d(ringDir).mul(cv).add(new Vector3d(axis).mul(sv));
        return toVec3d(ringCenter.add(tubeDir.mul(minorRadius)));
    }

    private Vector3d orthogonalUnit(Vector3d axis) {
        Vector3d reference = Math.abs(axis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        return new Vector3d(reference).cross(axis).normalize();
    }

    private Vec3d toVec3d(Vector3d value) {
        return new Vec3d(value.x, value.y, value.z);
    }

    private static class MeshBuilder {
        private final List<Triangle> triangles = new ArrayList<>();
        private final List<Segment> segments = new ArrayList<>();

        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        void addTriangle(Vec3d a, Vec3d b, Vec3d c) {
            Vector3f normal = computeNormal(a, b, c);
            triangles.add(new Triangle(a, b, c, normal));
            include(a);
            include(b);
            include(c);
        }

        void addSegment(Vec3d start, Vec3d end) {
            segments.add(new Segment(start, end));
            include(start);
            include(end);
        }

        void include(Vec3d p) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }

        Vec3d computeCenter() {
            if (!hasBounds()) {
                return Vec3d.ZERO;
            }
            return new Vec3d((minX + maxX) * 0.5d, (minY + maxY) * 0.5d, (minZ + maxZ) * 0.5d);
        }

        double computeRadius(Vec3d center) {
            if (!hasBounds()) {
                return 0.0d;
            }
            double dx = Math.max(Math.abs(maxX - center.x), Math.abs(minX - center.x));
            double dy = Math.max(Math.abs(maxY - center.y), Math.abs(minY - center.y));
            double dz = Math.max(Math.abs(maxZ - center.z), Math.abs(minZ - center.z));
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private boolean hasBounds() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
        }

        private Vector3f computeNormal(Vec3d a, Vec3d b, Vec3d c) {
            Vector3d ab = new Vector3d(b.x - a.x, b.y - a.y, b.z - a.z);
            Vector3d ac = new Vector3d(c.x - a.x, c.y - a.y, c.z - a.z);
            Vector3d normal = ab.cross(ac, new Vector3d());
            if (normal.lengthSquared() <= 1.0e-12d) {
                return new Vector3f(0.0f, 1.0f, 0.0f);
            }
            normal.normalize();
            return new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private record Triangle(Vec3d a, Vec3d b, Vec3d c, Vector3f normal) {
    }

    private record Segment(Vec3d start, Vec3d end) {
    }
}
