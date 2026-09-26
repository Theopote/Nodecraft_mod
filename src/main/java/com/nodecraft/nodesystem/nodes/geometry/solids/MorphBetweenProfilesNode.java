package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.morph_profiles",
    displayName = "Morph Between Profiles",
    description = "Interpolates between two compatible polygon profiles using parameter t in [0,1].",
    category = "geometry.solids",
    order = 12
)
public class MorphBetweenProfilesNode extends BaseNode {
    private static final String INPUT_SOURCE_PROFILE_ID = "input_source_profile";
    private static final String INPUT_TARGET_PROFILE_ID = "input_target_profile";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MorphBetweenProfilesNode() {
        super(UUID.randomUUID(), "geometry.solids.morph_profiles");
        addInputPort(new BasePort(INPUT_SOURCE_PROFILE_ID, "Source Profile", "Source polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_TARGET_PROFILE_ID, "Target Profile", "Target polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter in [0,1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Interpolated polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Boundary polyline of interpolated profile", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed interpolated points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when profile morph succeeds", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Interpolates between two compatible polygon profiles using parameter t in [0,1].";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sourceObj = inputValues.get(INPUT_SOURCE_PROFILE_ID);
        Object targetObj = inputValues.get(INPUT_TARGET_PROFILE_ID);
        if (!(sourceObj instanceof PolygonProfileData a) || !(targetObj instanceof PolygonProfileData b)) {
            writeInvalid();
            return;
        }
        double t = clamp01(inputValues.get(INPUT_T_ID) instanceof Number n ? n.doubleValue() : 0.5d);

        List<Vector3d> aUnique = a.getUniquePoints();
        List<Vector3d> bUnique = b.getUniquePoints();
        if (aUnique.size() < 3 || aUnique.size() != bUnique.size()) {
            writeInvalid();
            return;
        }

        List<Vector3d> closed = new ArrayList<>(aUnique.size() + 1);
        for (int i = 0; i < aUnique.size(); i++) {
            Vector3d p = new Vector3d(aUnique.get(i)).lerp(bUnique.get(i), t);
            closed.add(p);
        }
        closed.add(new Vector3d(closed.getFirst()));

        Vector3d centerA = a.getCenter();
        Vector3d centerB = b.getCenter();
        Vector3d center = new Vector3d(centerA).lerp(centerB, t);
        Vector3d nA = a.plane().getNormal();
        Vector3d nB = b.plane().getNormal();
        Vector3d n = new Vector3d(nA).lerp(nB, t);
        if (n.lengthSquared() <= 1.0e-12d) {
            n = new Vector3d(nA);
        }
        if (n.lengthSquared() <= 1.0e-12d) {
            n = new Vector3d(0.0d, 1.0d, 0.0d);
        }
        n.normalize();

        PolygonProfileData profile;
        try {
            profile = new PolygonProfileData(closed, new PlaneData(center, n));
        } catch (IllegalArgumentException ex) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, profile.getBoundary());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(closed));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double clamp01(double value) {
        if (value < 0.0d) return 0.0d;
        return Math.min(value, 1.0d);
    }
}
