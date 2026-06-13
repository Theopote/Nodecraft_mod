package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.surface_strip_to_geometry",
    displayName = "Surface Strip To Geometry",
    description = "Explicitly converts a surface strip into reusable geometry by sampling section edges and rails as cylinders",
    category = "geometry.solids",
    order = 7
)
public class SurfaceStripToGeometryNode extends BaseNode {

    @NodeProperty(displayName = "Radius", category = "Geometry", order = 1)
    private double radius = 0.35d;

    @NodeProperty(displayName = "Mode", category = "Sampling", order = 2)
    private SurfaceStripBridge.BridgeMode mode = SurfaceStripBridge.BridgeMode.LATTICE;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Sampling", order = 3)
    private int longitudinalSteps = 4;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_SURFACE_STRIP_TREE_ID = "input_surface_strip_tree";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_GEOMETRY_TREE_ID = "output_geometry_tree";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SurfaceStripToGeometryNode() {
        super(UUID.randomUUID(), "geometry.solids.surface_strip_to_geometry");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to convert into geometry", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_SURFACE_STRIP_TREE_ID, "Surface Strip Tree", "Optional tree of surface strips to convert per branch", NodeDataType.DATA_TREE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry approximation of the surface strip", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_TREE_ID, "Geometry Tree", "Generated geometry grouped by source surface strip tree branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the generated geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated geometry segment count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Explicitly converts a surface strip into reusable geometry by sampling section edges and rails as cylinders";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        Object surfaceStripTreeObj = inputValues.get(INPUT_SURFACE_STRIP_TREE_ID);
        GeometryData geometry = null;
        DataTreeData geometryTree = DataTreeData.empty();
        RegionData region = null;
        int count = 0;

        if (surfaceStripTreeObj instanceof DataTreeData surfaceStripTree && surfaceStripTree.getBranchCount() > 0) {
            List<DataTreeData.Branch> geometryBranches = new ArrayList<>();
            List<GeometryData> geometries = new ArrayList<>();
            for (DataTreeData.Branch branch : surfaceStripTree.getBranches()) {
                List<GeometryData> branchGeometries = new ArrayList<>();
                for (Object item : branch.items()) {
                    if (item instanceof SurfaceStripData surfaceStrip) {
                        GeometryData branchGeometry = SurfaceStripBridge.toGeometry(surfaceStrip, longitudinalSteps, mode, radius);
                        if (branchGeometry != null) {
                            branchGeometries.add(branchGeometry);
                            geometries.add(branchGeometry);
                            count += SurfaceStripBridge.estimateGeometrySegmentCount(surfaceStrip, longitudinalSteps, mode);
                        }
                    }
                }
                if (!branchGeometries.isEmpty()) {
                    geometryBranches.add(new DataTreeData.Branch(branch.path(), new ArrayList<>(branchGeometries)));
                }
            }
            geometryTree = new DataTreeData(geometryBranches);
            geometry = geometries.isEmpty() ? null : new CompositeGeometryData(geometries);
            if (geometry != null) {
                region = GeometryVoxelizer.createBoundingRegion(geometry);
            }
        } else if (surfaceStripObj instanceof SurfaceStripData surfaceStrip) {
            geometry = SurfaceStripBridge.toGeometry(surfaceStrip, longitudinalSteps, mode, radius);
            count = SurfaceStripBridge.estimateGeometrySegmentCount(surfaceStrip, longitudinalSteps, mode);
            if (geometry != null) {
                geometryTree = new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), List.of(geometry))));
                region = GeometryVoxelizer.createBoundingRegion(geometry);
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_GEOMETRY_TREE_ID, geometryTree);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, geometry != null);
    }

    public SurfaceStripBridge.BridgeMode getMode() {
        return mode;
    }

    public void setMode(SurfaceStripBridge.BridgeMode mode) {
        SurfaceStripBridge.BridgeMode resolved = mode == null ? SurfaceStripBridge.BridgeMode.LATTICE : mode;
        if (this.mode != resolved) {
            this.mode = resolved;
            markDirty();
        }
    }

    public void setModeString(String mode) {
        if (mode == null || mode.isBlank()) {
            setMode(SurfaceStripBridge.BridgeMode.LATTICE);
            return;
        }
        try {
            setMode(SurfaceStripBridge.BridgeMode.valueOf(mode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setMode(SurfaceStripBridge.BridgeMode.LATTICE);
        }
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        double resolved = Math.max(0.0d, radius);
        if (Double.compare(this.radius, resolved) != 0) {
            this.radius = resolved;
            markDirty();
        }
    }

    public int getLongitudinalSteps() {
        return longitudinalSteps;
    }

    public void setLongitudinalSteps(int longitudinalSteps) {
        int resolved = Math.max(1, longitudinalSteps);
        if (this.longitudinalSteps != resolved) {
            this.longitudinalSteps = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("radius", radius);
        state.put("mode", mode.name());
        state.put("longitudinalSteps", longitudinalSteps);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("radius") instanceof Number value) {
            setRadius(value.doubleValue());
        }
        if (map.get("mode") instanceof String value) {
            setModeString(value);
        } else {
            boolean hasSections = !(map.get("includeSectionEdges") instanceof Boolean value) || value;
            boolean hasRails = !(map.get("includeRails") instanceof Boolean value) || value;
            if (hasSections && hasRails) {
                setMode(SurfaceStripBridge.BridgeMode.LATTICE);
            } else if (hasSections) {
                setMode(SurfaceStripBridge.BridgeMode.SECTIONS_ONLY);
            } else if (hasRails) {
                setMode(SurfaceStripBridge.BridgeMode.RAILS_ONLY);
            }
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
    }
}
