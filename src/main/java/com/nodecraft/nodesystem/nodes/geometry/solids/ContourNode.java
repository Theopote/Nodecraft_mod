package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.solids.SectionContourUtils.SectionResult;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.contour",
    displayName = "Voxel Contours",
    description = "Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing.",
    category = "geometry.solids",
    order = 19
)
public class ContourNode extends AbstractSolidNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BASE_PLANE_ID = "input_base_plane";
    private static final String INPUT_START_DISTANCE_ID = "input_start_distance";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_PROFILE_ID = "output_profile";
    private static final String OUTPUT_BOUNDARY_ID = "output_boundary";
    private static final String OUTPUT_PROFILES_ID = "output_profiles";
    private static final String OUTPUT_BOUNDARIES_ID = "output_boundaries";
    private static final String OUTPUT_PROFILES_TREE_ID = "output_profiles_tree";
    private static final String OUTPUT_BOUNDARIES_TREE_ID = "output_boundaries_tree";
    private static final String OUTPUT_SLICE_BLOCKS_ID = "output_slice_blocks";
    private static final String OUTPUT_SLICE_POINTS_ID = "output_slice_points";
    private static final String OUTPUT_SLICE_BLOCKS_TREE_ID = "output_slice_blocks_tree";
    private static final String OUTPUT_SLICE_POINTS_TREE_ID = "output_slice_points_tree";
    private static final String OUTPUT_PLANES_ID = "output_planes";
    private static final String OUTPUT_CONTOUR_COUNT_ID = "output_contour_count";

    public ContourNode() {
        super(UUID.randomUUID(), "geometry.solids.contour");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to contour", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_PLANE_ID, "Base Plane", "First contour plane orientation and origin. Defaults to XY", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_START_DISTANCE_ID, "Start Distance", "Offset from the base plane along its normal for the first contour", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Distance between adjacent contour planes", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of contour planes to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary traced contour profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Primary traced contour boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "All traced contour profiles", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_ID, "Boundaries", "All traced contour boundary paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Contour profiles keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_TREE_ID, "Boundaries Tree", "Contour boundary paths keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting contour slabs", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_TREE_ID, "Slice Blocks Tree", "Slice blocks keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_TREE_ID, "Slice Points Tree", "Projected section sample points keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PLANES_ID, "Planes", "Generated contour planes", NodeDataType.PLANE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTOUR_COUNT_ID, "Contour Count", "Number of traced contour boundaries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one contour was resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing.";
    }

    @Override
    public String getDisplayName() {
        return "Voxel Contours";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            invalidate("Geometry is missing or invalid");
            return;
        }

        PlaneData basePlane = resolvePlane(INPUT_BASE_PLANE_ID, PlaneData.XY_PLANE);
        if (basePlane == null) {
            invalidate("Base plane is invalid");
            return;
        }
        Vector3d normal = basePlane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            invalidate("Base plane normal is zero-length");
            return;
        }
        normal.normalize();

        Integer countObj = resolvePositiveInteger(INPUT_COUNT_ID, 10);
        if (countObj == null) {
            invalidate("Count is connected but invalid (must be a positive integer)");
            return;
        }
        int count = countObj;

        Double spacingObj = resolveFiniteDouble(INPUT_SPACING_ID, 1.0d);
        if (spacingObj == null) {
            invalidate("Spacing is connected but invalid (must be finite)");
            return;
        }
        double spacing = spacingObj;
        if (Math.abs(spacing) <= 1.0e-12d) {
            invalidate("Spacing must be non-zero");
            return;
        }

        Double startDistanceObj = resolveFiniteDouble(INPUT_START_DISTANCE_ID, 0.0d);
        if (startDistanceObj == null) {
            invalidate("Start Distance is connected but invalid (must be finite)");
            return;
        }

        Double thicknessObj = resolvePositiveDouble(INPUT_THICKNESS_ID, 1.0d);
        if (thicknessObj == null) {
            invalidate("Thickness is connected but invalid (must be finite and > 0)");
            return;
        }

        GeometryVoxelizationResult voxelResult = GeometryVoxelizer.voxelizeStrict(geometry, true);
        if (!voxelResult.success()) {
            invalidate(voxelError(voxelResult));
            return;
        }

        List<PlaneData> planes = buildPlanes(basePlane, normal, startDistanceObj, spacing, count);
        writeResults(voxelResult.blocks(), planes, thicknessObj);
    }

    private List<PlaneData> buildPlanes(PlaneData basePlane, Vector3d normal, double startDistance, double spacing, int count) {
        List<PlaneData> planes = new ArrayList<>(count);
        Vector3d origin = basePlane.getPoint();
        for (int i = 0; i < count; i++) {
            double distance = startDistance + spacing * i;
            planes.add(new PlaneData(new Vector3d(origin).add(new Vector3d(normal).mul(distance)), normal));
        }
        return List.copyOf(planes);
    }

    private void writeResults(BlockPosList filled, List<PlaneData> planes, double thickness) {
        List<PolygonProfileData> profiles = new ArrayList<>();
        List<PathData> boundaries = new ArrayList<>();
        BlockPosList allSliceBlocks = new BlockPosList();
        List<Vector3d> allSlicePoints = new ArrayList<>();
        List<DataTreeData.Branch> profileBranches = new ArrayList<>();
        List<DataTreeData.Branch> boundaryBranches = new ArrayList<>();
        List<DataTreeData.Branch> blockBranches = new ArrayList<>();
        List<DataTreeData.Branch> pointBranches = new ArrayList<>();
        SectionResult firstValid = null;

        for (int planeIndex = 0; planeIndex < planes.size(); planeIndex++) {
            SectionResult result = SectionContourUtils.cutSection(filled, planes.get(planeIndex), thickness);
            if (!result.profiles().isEmpty()) {
                profiles.addAll(result.profiles());
                for (int contourIndex = 0; contourIndex < result.profiles().size(); contourIndex++) {
                    profileBranches.add(new DataTreeData.Branch(List.of(planeIndex, contourIndex), List.of(result.profiles().get(contourIndex))));
                }
            } else {
                profileBranches.add(new DataTreeData.Branch(List.of(planeIndex), List.of()));
            }
            if (!result.boundaries().isEmpty()) {
                for (PolylineData boundary : result.boundaries()) {
                    PathData path = pathFromPolyline(boundary);
                    if (path != null) {
                        boundaries.add(path);
                    }
                }
                for (int contourIndex = 0; contourIndex < result.boundaries().size(); contourIndex++) {
                    PathData path = pathFromPolyline(result.boundaries().get(contourIndex));
                    boundaryBranches.add(new DataTreeData.Branch(
                        List.of(planeIndex, contourIndex),
                        path == null ? List.of() : List.of(path)
                    ));
                }
            } else {
                boundaryBranches.add(new DataTreeData.Branch(List.of(planeIndex), List.of()));
            }
            allSliceBlocks.addAll(result.sliceBlocks().getPositions());
            allSlicePoints.addAll(result.projectedPoints());
            blockBranches.add(new DataTreeData.Branch(List.of(planeIndex), new ArrayList<>(result.sliceBlocks().getPositions())));
            pointBranches.add(new DataTreeData.Branch(List.of(planeIndex), new ArrayList<>(result.projectedPoints())));
            if (firstValid == null && result.valid()) {
                firstValid = result;
            }
        }

        if (firstValid == null) {
            invalidate("No contour profiles were traced");
            return;
        }

        outputValues.put(OUTPUT_PROFILE_ID, firstValid.primaryProfile());
        outputValues.put(OUTPUT_BOUNDARY_ID, pathFromPolyline(firstValid.primaryBoundary()));
        outputValues.put(OUTPUT_PROFILES_ID, List.copyOf(profiles));
        outputValues.put(OUTPUT_BOUNDARIES_ID, List.copyOf(boundaries));
        outputValues.put(OUTPUT_PROFILES_TREE_ID, new DataTreeData(profileBranches));
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, new DataTreeData(boundaryBranches));
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, allSliceBlocks);
        outputValues.put(OUTPUT_SLICE_POINTS_ID, SpatialValueResolver.toPointDataList(allSlicePoints));
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, new DataTreeData(blockBranches));
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, new DataTreeData(pointBranches));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_CONTOUR_COUNT_ID, boundaries.size());
        markSuccess();
    }

    private static String voxelError(GeometryVoxelizationResult result) {
        if (result.error() != null && !result.error().isBlank()) {
            return result.error();
        }
        VoxelizationStatus status = result.status();
        return switch (status) {
            case OVER_BUDGET -> "Voxelization exceeded budget";
            case UNSUPPORTED -> "Voxelization unsupported for geometry";
            case INVALID_BOUNDS -> "Voxelization bounds are invalid";
            case CHILD_FAILURE -> "Voxelization failed for geometry child";
            default -> "Voxelization failed";
        };
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PROFILE_ID, OUTPUT_BOUNDARY_ID);
        putEmptyListOutputs(OUTPUT_PROFILES_ID, OUTPUT_BOUNDARIES_ID, OUTPUT_SLICE_POINTS_ID, OUTPUT_PLANES_ID);
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, DataTreeData.empty());
        putIntOutputs(0, OUTPUT_CONTOUR_COUNT_ID);
        markInvalid(error);
    }
}
