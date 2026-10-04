package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.profiles.triangulate_2d",
    displayName = "Profile Triangulate 2D",
    description = "Triangulates a planar polygon profile into triangle profiles using ear clipping",
    category = "geometry.profiles",
    order = 20
)
public class ProfileTriangulate2DNode extends AbstractProfileNode {
    private static final double EPS = 1.0e-9d;

    private static final String INPUT_PROFILE_ID = "input_profile";

    private static final String OUTPUT_TRIANGLES_ID = "output_triangles";
    private static final String OUTPUT_TRIANGLE_COUNT_ID = "output_triangle_count";

    public ProfileTriangulate2DNode() {
        super(UUID.randomUUID(), "geometry.profiles.triangulate_2d");
        addInputPort(new BasePort(INPUT_PROFILE_ID, "Profile", "Input polygon profile to triangulate", NodeDataType.POLYGON_PROFILE, this));

        addOutputPort(new BasePort(OUTPUT_TRIANGLES_ID, "Triangles", "Triangle polygon profiles", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TRIANGLE_COUNT_ID, "Triangle Count", "Number of triangles produced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when triangulation succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Triangulates a planar polygon profile into triangle profiles using ear clipping";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PolygonProfileData profile = resolveStrictProfile(INPUT_PROFILE_ID);
        if (profile == null) {
            writeFailure("Valid polygon profile is required");
            return;
        }

        List<Vector3d> unique3d = profile.getUniquePoints();
        if (unique3d.size() < 3) {
            writeFailure("Profile requires at least 3 vertices");
            return;
        }
        if (!GenerationLimits.isWithinProfileTriangulationWork(unique3d.size())) {
            writeFailure("Triangulation workload exceeds limit (" + GenerationLimits.MAX_PROFILE_TRIANGULATION_WORK + ")");
            return;
        }

        PlaneData plane = profile.plane();
        PlaneProjectionUtils.PlaneProjectionContext ctx =
            PlaneProjectionUtils.PlaneProjectionContext.from(plane, unique3d.getFirst());
        List<Vector2d> pts2d = new ArrayList<>(unique3d.size());
        for (Vector3d p : unique3d) {
            Vector2d uv = ctx.toLocal(p);
            if (!Double.isFinite(uv.x) || !Double.isFinite(uv.y)) {
                writeFailure("Profile contains non-finite projected coordinates");
                return;
            }
            pts2d.add(uv);
        }

        if (signedArea(pts2d) < 0.0d) {
            reverseInPlace(pts2d);
            reverseInPlace(unique3d);
        }

        List<Integer> index = new ArrayList<>(pts2d.size());
        for (int i = 0; i < pts2d.size(); i++) {
            index.add(i);
        }

        List<PolygonProfileData> triangles = new ArrayList<>();
        int guard = pts2d.size() * pts2d.size();
        while (index.size() > 3 && guard-- > 0) {
            boolean earFound = false;
            for (int i = 0; i < index.size(); i++) {
                int i0 = index.get((i - 1 + index.size()) % index.size());
                int i1 = index.get(i);
                int i2 = index.get((i + 1) % index.size());
                Vector2d a = pts2d.get(i0);
                Vector2d b = pts2d.get(i1);
                Vector2d c = pts2d.get(i2);

                if (!isConvex(a, b, c)) {
                    continue;
                }
                if (containsAnyPointInsideTriangle(pts2d, index, i0, i1, i2, a, b, c)) {
                    continue;
                }

                triangles.add(toTriangle(unique3d.get(i0), unique3d.get(i1), unique3d.get(i2), plane));
                index.remove(i);
                earFound = true;
                break;
            }
            if (!earFound) {
                writeFailure("Triangulation failed (non-simple or degenerate polygon)");
                return;
            }
        }

        if (index.size() == 3) {
            triangles.add(toTriangle(
                unique3d.get(index.get(0)),
                unique3d.get(index.get(1)),
                unique3d.get(index.get(2)),
                plane
            ));
        }

        if (triangles.isEmpty()) {
            writeFailure("Triangulation produced no triangles");
            return;
        }

        String budgetError = ProfilePlanarOps.validateOutputBudget(triangles);
        if (budgetError != null) {
            writeFailure(budgetError);
            return;
        }

        outputValues.put(OUTPUT_TRIANGLES_ID, new ArrayList<>(triangles));
        outputValues.put(OUTPUT_TRIANGLE_COUNT_ID, triangles.size());
        markSuccess();
    }

    private PolygonProfileData toTriangle(Vector3d a, Vector3d b, Vector3d c, PlaneData plane) {
        List<Vector3d> closed = List.of(
            new Vector3d(a),
            new Vector3d(b),
            new Vector3d(c),
            new Vector3d(a)
        );
        return new PolygonProfileData(closed, plane);
    }

    private boolean isConvex(Vector2d a, Vector2d b, Vector2d c) {
        return cross(a, b, c) > EPS;
    }

    private boolean containsAnyPointInsideTriangle(List<Vector2d> points, List<Integer> index,
                                                   int i0, int i1, int i2,
                                                   Vector2d a, Vector2d b, Vector2d c) {
        for (int idx : index) {
            if (idx == i0 || idx == i1 || idx == i2) {
                continue;
            }
            if (pointInTriangle(points.get(idx), a, b, c)) {
                return true;
            }
        }
        return false;
    }

    private boolean pointInTriangle(Vector2d p, Vector2d a, Vector2d b, Vector2d c) {
        double c1 = cross(p, a, b);
        double c2 = cross(p, b, c);
        double c3 = cross(p, c, a);
        boolean hasNeg = c1 < -EPS || c2 < -EPS || c3 < -EPS;
        boolean hasPos = c1 > EPS || c2 > EPS || c3 > EPS;
        return !(hasNeg && hasPos);
    }

    private double signedArea(List<Vector2d> pts) {
        double area = 0.0d;
        for (int i = 0; i < pts.size(); i++) {
            Vector2d a = pts.get(i);
            Vector2d b = pts.get((i + 1) % pts.size());
            area += a.x * b.y - b.x * a.y;
        }
        return area * 0.5d;
    }

    private double cross(Vector2d a, Vector2d b, Vector2d c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private <T> void reverseInPlace(List<T> list) {
        for (int i = 0, j = list.size() - 1; i < j; i++, j--) {
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private void writeFailure(String error) {
        putEmptyListOutputs(OUTPUT_TRIANGLES_ID);
        putIntOutputs(0, OUTPUT_TRIANGLE_COUNT_ID);
        markInvalid(error);
    }
}
