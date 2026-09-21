package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.solids.SectionContourUtils.SectionResult;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.section_cut",
    displayName = "Section Cut",
    description = "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.",
    category = "geometry.solids",
    order = 10
)
public class SectionCutNode extends BaseNode {
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
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
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SectionCutNode() {
        super(UUID.randomUUID(), "geometry.solids.section_cut");
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry fallback", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry fallback", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry fallback", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry fallback", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Section plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_PLANES_ID, "Planes", "Optional list of section planes. When connected, each plane outputs one section branch", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Slice thickness in world units", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PROFILE_ID, "Profile", "Primary traced section profile", NodeDataType.POLYGON_PROFILE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARY_ID, "Boundary", "Primary traced section boundary polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_ID, "Profiles", "Resolved section profiles for all planes", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_ID, "Boundaries", "Resolved section boundary polylines for all planes", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PROFILES_TREE_ID, "Profiles Tree", "Section profiles keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDARIES_TREE_ID, "Boundaries Tree", "Section boundary polylines keyed by plane and contour index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_ID, "Slice Blocks", "Voxel blocks intersecting the section slab", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_ID, "Slice Points", "Projected section sample points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_BLOCKS_TREE_ID, "Slice Blocks Tree", "Slice blocks keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SLICE_POINTS_TREE_ID, "Slice Points Tree", "Projected section sample points keyed by plane index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when section profile is resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Cuts geometry by one or more planes and traces voxel slice contours as section profiles, boundaries, blocks, and tree-grouped data.";
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

        List<PlaneData> planes = resolvePlanes(inputValues.get(INPUT_PLANES_ID), inputValues.get(INPUT_PLANE_ID));
        writeResults(GeometryVoxelizer.voxelize(geometry, true), planes, Math.max(0.1d, getDouble(INPUT_THICKNESS_ID, 1.0d)));
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
        outputValues.put(OUTPUT_SLICE_POINTS_ID, SpatialValueResolver.toPointDataList(allSlicePoints));
        outputValues.put(OUTPUT_SLICE_BLOCKS_TREE_ID, new DataTreeData(blockBranches));
        outputValues.put(OUTPUT_SLICE_POINTS_TREE_ID, new DataTreeData(pointBranches));
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
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<PlaneData> resolvePlanes(@Nullable Object planesObj, @Nullable Object planeObj) {
        if (planesObj instanceof List<?> list) {
            List<PlaneData> planes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof PlaneData plane) {
                    planes.add(plane);
                }
            }
            if (!planes.isEmpty()) {
                return List.copyOf(planes);
            }
        }
        return List.of(planeObj instanceof PlaneData plane ? plane : PlaneData.XY_PLANE);
    }

    private double getDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
