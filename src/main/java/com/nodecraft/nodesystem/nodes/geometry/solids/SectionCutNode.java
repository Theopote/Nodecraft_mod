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
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.section_cut",
    displayName = "Voxel Section",
    description = "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.",
    category = "geometry.solids",
    order = 18
)
public class SectionCutNode extends AbstractSolidNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_PLANES_ID = "input_planes";
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

    public SectionCutNode() {
        super(UUID.randomUUID(), "geometry.solids.section_cut");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to section", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Section plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_PLANES_ID, "Planes", "Optional list of section planes. When connected, each plane outputs one section branch", NodeDataType.PLANE_LIST, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary traced section profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Primary traced section boundary path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles for all planes", NodeDataType.POLYGON_PROFILE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_ID, "Boundaries", "Resolved section boundary paths for all planes", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Section profiles keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_TREE_ID, "Boundaries Tree", "Section boundary paths keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting the section slab", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_TREE_ID, "Slice Blocks Tree", "Slice blocks keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_TREE_ID, "Slice Points Tree", "Projected section sample points keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when section profile is resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.";
    }

    @Override
    public String getDisplayName() {
        return "Voxel Section";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            invalidate("Geometry is missing or invalid");
            return;
        }

        List<PlaneData> planes = resolvePlanes();
        if (planes.isEmpty()) {
            invalidate("At least one section plane is required");
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

        writeResults(voxelResult.blocks(), planes, thicknessObj);
    }

    private List<PlaneData> resolvePlanes() {
        if (SurfaceInputUtils.isConnected(this, INPUT_PLANES_ID)) {
            List<PlaneData> planes = SurfaceInputUtils.resolveStrictPlaneList(inputValues.get(INPUT_PLANES_ID));
            return planes == null ? List.of() : planes;
        }
        PlaneData plane = resolvePlane(INPUT_PLANE_ID, PlaneData.XY_PLANE);
        return plane == null ? List.of() : List.of(plane);
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
            invalidate("No section contours were traced");
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
        putEmptyListOutputs(OUTPUT_PROFILES_ID, OUTPUT_BOUNDARIES_ID, OUTPUT_SLICE_POINTS_ID);
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, DataTreeData.empty());
        markInvalid(error);
    }
}
