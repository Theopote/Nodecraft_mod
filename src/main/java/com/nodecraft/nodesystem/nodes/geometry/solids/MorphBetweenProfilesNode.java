package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.morph_profiles",
    displayName = "Morph Between Profiles",
    description = "Interpolates between two compatible polygon profiles using parameter t in [0,1].",
    category = "geometry.solids",
    order = 12
)
public class MorphBetweenProfilesNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Flip Profiles", category = "Correspondence", order = 20,
        description = "Reverse vertex order on both profiles")
    private boolean flipProfiles = false;

    @NodeProperty(displayName = "Seam Offset", category = "Correspondence", order = 21,
        description = "Integer cyclic vertex shift applied after Flip")
    private int seamOffset = 0;

    @NodeProperty(displayName = "Match Seam", category = "Correspondence", order = 22,
        description = "INDEX keeps vertex indices; AUTO_SEAM is an explicit cyclic min-distance shift")
    private MatchSeamMode matchSeamMode = MatchSeamMode.INDEX;
    private static final String INPUT_SOURCE_PROFILE_ID = "input_source_profile";
    private static final String INPUT_TARGET_PROFILE_ID = "input_target_profile";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_POINTS_ID = "output_points";

    public MorphBetweenProfilesNode() {
        super(UUID.randomUUID(), "geometry.solids.morph_profiles");
        addInputPort(new BasePort(INPUT_SOURCE_PROFILE_ID, "Source Profile", "Source polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_TARGET_PROFILE_ID, "Target Profile", "Target polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter in [0,1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Interpolated polygon profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Boundary path of interpolated profile", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed interpolated points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when profile morph succeeds", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
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
            invalidate("Source and target profiles are required");
            return;
        }

        Double t = resolveNormalizedU(INPUT_T_ID, 0.5d);
        if (t == null) {
            invalidate("T is connected but invalid (must be finite and in [0, 1])");
            return;
        }

        List<Vector3d> aUnique = SectionCorrespondence.apply(
            a.getUniquePoints(), flipProfiles, seamOffset, null, MatchSeamMode.INDEX);
        List<Vector3d> bUnique = SectionCorrespondence.apply(
            b.getUniquePoints(), flipProfiles, seamOffset, aUnique, matchSeamMode);
        if (aUnique.size() < 3 || aUnique.size() != bUnique.size()) {
            invalidate("Profiles must have matching vertex counts of at least 3");
            return;
        }

        List<Vector3d> closed = new ArrayList<>(aUnique.size() + 1);
        for (int i = 0; i < aUnique.size(); i++) {
            closed.add(new Vector3d(aUnique.get(i)).lerp(bUnique.get(i), t));
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
            invalidate("Interpolated profile is degenerate");
            return;
        }

        PathData boundary = PathUtils.toPathData(closed);
        if (boundary == null) {
            invalidate("Interpolated boundary path is invalid");
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, profile);
        outputValues.put(OUTPUT_BOUNDARY_ID, boundary);
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(closed));
        markSuccess();
    }

    public boolean isFlipProfiles() {
        return flipProfiles;
    }

    public void setFlipProfiles(boolean flipProfiles) {
        markDirtyIfChanged(this.flipProfiles, flipProfiles);
        this.flipProfiles = flipProfiles;
    }

    public int getSeamOffset() {
        return seamOffset;
    }

    public void setSeamOffset(int seamOffset) {
        markDirtyIfChanged(this.seamOffset, seamOffset);
        this.seamOffset = seamOffset;
    }

    public MatchSeamMode getMatchSeamMode() {
        return matchSeamMode;
    }

    public void setMatchSeamMode(MatchSeamMode matchSeamMode) {
        MatchSeamMode resolved = matchSeamMode == null ? MatchSeamMode.INDEX : matchSeamMode;
        markDirtyIfChanged(this.matchSeamMode, resolved);
        this.matchSeamMode = resolved;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("flipProfiles", flipProfiles);
        state.put("seamOffset", seamOffset);
        state.put("matchSeamMode", matchSeamMode.key());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("flipProfiles") instanceof Boolean value) {
            setFlipProfiles(value);
        }
        if (map.get("seamOffset") instanceof Number value) {
            setSeamOffset(value.intValue());
        }
        if (map.get("matchSeamMode") instanceof String value) {
            if (MatchSeamMode.KEYS.contains(value.toLowerCase())) {
                setMatchSeamMode(MatchSeamMode.fromKey(value));
            }
        }
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        markInvalid(error);
    }
}
