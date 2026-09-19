package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SurfaceShellBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.offset_surface_strip",
    displayName = "Offset Surface Strip",
    description = "Offsets a surface strip by a signed distance and outputs a single offset surface",
    category = "geometry.solids",
    order = 13
)
public class OffsetSurfaceStripNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Distance", category = "Offset", order = 1)
    private double defaultDistance = 1.0d;

    @NodeProperty(displayName = "Geometry Radius", category = "Geometry", order = 2)
    private double geometryRadius = 0.25d;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Geometry", order = 3)
    private int longitudinalSteps = 4;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetSurfaceStripNode() {
        super(UUID.randomUUID(), "geometry.solids.offset_surface_strip");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to offset", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Single offset surface strip", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Cylinder-sampled approximation of the offset strip", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the offset strip", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Resolved signed offset distance", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of sections in the offset strip", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an offset surface was generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a surface strip by a signed distance and outputs a single offset surface";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            writeEmptyOutputs();
            return;
        }

        double distance = getInputDouble(INPUT_DISTANCE_ID, defaultDistance);
        if (Math.abs(distance) <= EPSILON) {
            outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
            outputValues.put(OUTPUT_GEOMETRY_ID, SurfaceShellBuilder.buildGeometry(List.of(surfaceStrip), longitudinalSteps, geometryRadius));
            outputValues.put(OUTPUT_REGION_ID, SurfaceShellBuilder.createBoundingRegion(List.of(surfaceStrip)));
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_SECTION_COUNT_ID, surfaceStrip.getSectionCount());
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }

        SurfaceShellBuilder.ShellResult shell = SurfaceShellBuilder.buildShell(
            surfaceStrip,
            Math.abs(distance),
            distance > 0.0d ? SurfaceShellBuilder.OffsetMode.OUTSIDE : SurfaceShellBuilder.OffsetMode.INSIDE
        );
        if (shell == null) {
            writeEmptyOutputs();
            return;
        }

        SurfaceStripData offsetSurface = distance > 0.0d ? shell.outerSurface() : shell.innerSurface();
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, offsetSurface);
        outputValues.put(OUTPUT_GEOMETRY_ID, SurfaceShellBuilder.buildGeometry(List.of(offsetSurface), longitudinalSteps, geometryRadius));
        outputValues.put(OUTPUT_REGION_ID, SurfaceShellBuilder.createBoundingRegion(List.of(offsetSurface)));
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, offsetSurface.getSectionCount());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultDistance() {
        return defaultDistance;
    }

    public void setDefaultDistance(double defaultDistance) {
        if (Double.compare(this.defaultDistance, defaultDistance) != 0) {
            this.defaultDistance = defaultDistance;
            markDirty();
        }
    }

    public double getGeometryRadius() {
        return geometryRadius;
    }

    public void setGeometryRadius(double geometryRadius) {
        double resolved = Math.max(0.0d, geometryRadius);
        if (Double.compare(this.geometryRadius, resolved) != 0) {
            this.geometryRadius = resolved;
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
        return java.util.Map.of(
            "defaultDistance", defaultDistance,
            "geometryRadius", geometryRadius,
            "longitudinalSteps", longitudinalSteps
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultDistance") instanceof Number value) {
            setDefaultDistance(value.doubleValue());
        }
        if (map.get("geometryRadius") instanceof Number value) {
            setGeometryRadius(value.doubleValue());
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, 0.0d);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
