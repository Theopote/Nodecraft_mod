package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.nodes.geometry.solids.SectionContourUtils.SectionResult;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.contour",
    displayName = "Contour",
    description = "Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing.",
    category = "geometry.solids",
    order = 10
)
public class ContourNode extends BaseNode {
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ContourNode() {
        super(UUID.randomUUID(), "geometry.solids.contour");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry fallback", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry fallback", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry fallback", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry fallback", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BASE_PLANE_ID, "Base Plane", "First contour plane orientation and origin. Defaults to XY", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_START_DISTANCE_ID, "Start Distance", "Offset from the base plane along its normal for the first contour", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing", "Distance between adjacent contour planes", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of contour planes to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary traced contour profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Primary traced contour boundary", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "All traced contour profiles", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_ID, "Boundaries", "All traced contour boundaries", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Contour profiles keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_TREE_ID, "Boundaries Tree", "Contour boundary polylines keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting contour slabs", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_TREE_ID, "Slice Blocks Tree", "Slice blocks keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_TREE_ID, "Slice Points Tree", "Projected section sample points keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PLANES_ID, "Planes", "Generated contour planes", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTOUR_COUNT_ID, "Contour Count", "Number of traced contour boundaries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least one contour was resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates parallel section planes and traces voxel contour profiles from geometry at regular spacing.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = GeometryVoxelizer.resolveGeometry(
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID)
        );
        if (geometry == null) {
            writeInvalid();
            return;
        }

        PlaneData basePlane = inputValues.get(INPUT_BASE_PLANE_ID) instanceof PlaneData plane ? plane : PlaneData.XY_PLANE;
        Vector3d normal = basePlane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            writeInvalid();
            return;
        }
        normal.normalize();

        int count = GenerationLimits.clampPositiveCount(getInt(INPUT_COUNT_ID, 10));
        double spacing = getDouble(INPUT_SPACING_ID, 1.0d);
        if (!Double.isFinite(spacing) || Math.abs(spacing) <= 1.0e-12d) {
            writeInvalid();
            return;
        }
        double startDistance = getDouble(INPUT_START_DISTANCE_ID, 0.0d);
        double thickness = Math.max(0.1d, getDouble(INPUT_THICKNESS_ID, 1.0d));

        List<PlaneData> planes = buildPlanes(basePlane, normal, startDistance, spacing, count);
        writeResults(GeometryVoxelizer.voxelize(geometry, true), planes, thickness);
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
        List<PolylineData> boundaries = new ArrayList<>();
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
                boundaries.addAll(result.boundaries());
                for (int contourIndex = 0; contourIndex < result.boundaries().size(); contourIndex++) {
                    boundaryBranches.add(new DataTreeData.Branch(List.of(planeIndex, contourIndex), List.of(result.boundaries().get(contourIndex))));
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

        outputValues.put(OUTPUT_PROFILE_ID, firstValid != null ? firstValid.primaryProfile() : null);
        outputValues.put(OUTPUT_BOUNDARY_ID, firstValid != null ? firstValid.primaryBoundary() : null);
        outputValues.put(OUTPUT_PROFILES_ID, List.copyOf(profiles));
        outputValues.put(OUTPUT_BOUNDARIES_ID, List.copyOf(boundaries));
        outputValues.put(OUTPUT_PROFILES_TREE_ID, new DataTreeData(profileBranches));
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, new DataTreeData(boundaryBranches));
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, allSliceBlocks);
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.copyOf(allSlicePoints));
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, new DataTreeData(blockBranches));
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, new DataTreeData(pointBranches));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_CONTOUR_COUNT_ID, boundaries.size());
        outputValues.put(OUTPUT_VALID_ID, firstValid != null);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PROFILE_ID, null);
        outputValues.put(OUTPUT_BOUNDARY_ID, null);
        outputValues.put(OUTPUT_PROFILES_ID, List.of());
        outputValues.put(OUTPUT_BOUNDARIES_ID, List.of());
        outputValues.put(OUTPUT_PROFILES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_BOUNDARIES_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_SLICE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_PLANES_ID, List.of());
        outputValues.put(OUTPUT_CONTOUR_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private int getInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private double getDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
