package com.nodecraft.nodesystem.nodes.spatial.modeling;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.modeling.surface_strip_to_geometry",
    displayName = "Surface Strip To Geometry",
    description = "Bridges a surface strip into reusable geometry by sampling section edges and rails as cylinders",
    category = "spatial.modeling"
)
public class SurfaceStripToGeometryNode extends BaseNode {

    @NodeProperty(displayName = "Radius", category = "Geometry", order = 1)
    private double radius = 0.35d;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Sampling", order = 2)
    private int longitudinalSteps = 4;

    @NodeProperty(displayName = "Include Section Edges", category = "Sampling", order = 3)
    private boolean includeSectionEdges = true;

    @NodeProperty(displayName = "Include Rails", category = "Sampling", order = 4)
    private boolean includeRails = true;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SurfaceStripToGeometryNode() {
        super(UUID.randomUUID(), "spatial.modeling.surface_strip_to_geometry");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to convert into geometry", NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry approximation of the surface strip", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the generated geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated geometry segment count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Bridges a surface strip into reusable geometry by sampling section edges and rails as cylinders";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        GeometryData geometry = null;
        RegionData region = null;
        int count = 0;

        if (surfaceStripObj instanceof SurfaceStripData surfaceStrip) {
            geometry = SurfaceStripBridge.toGeometry(surfaceStrip, longitudinalSteps, includeSectionEdges, includeRails, radius);
            count = SurfaceStripBridge.estimateGeometrySegmentCount(surfaceStrip, longitudinalSteps, includeSectionEdges, includeRails);
            if (geometry != null) {
                region = GeometryVoxelizer.createBoundingRegion(geometry);
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, geometry != null);
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

    public boolean isIncludeSectionEdges() {
        return includeSectionEdges;
    }

    public void setIncludeSectionEdges(boolean includeSectionEdges) {
        if (this.includeSectionEdges != includeSectionEdges) {
            this.includeSectionEdges = includeSectionEdges;
            markDirty();
        }
    }

    public boolean isIncludeRails() {
        return includeRails;
    }

    public void setIncludeRails(boolean includeRails) {
        if (this.includeRails != includeRails) {
            this.includeRails = includeRails;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("radius", radius);
        state.put("longitudinalSteps", longitudinalSteps);
        state.put("includeSectionEdges", includeSectionEdges);
        state.put("includeRails", includeRails);
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
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
        if (map.get("includeSectionEdges") instanceof Boolean value) {
            setIncludeSectionEdges(value);
        }
        if (map.get("includeRails") instanceof Boolean value) {
            setIncludeRails(value);
        }
    }
}
