package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
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
public class OffsetSurfaceStripNode extends AbstractSolidNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Default Distance", category = "Offset", order = 1)
    private double defaultDistance = 1.0d;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";

    public OffsetSurfaceStripNode() {
        super(UUID.randomUUID(), "geometry.solids.offset_surface_strip");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to offset", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Single offset surface strip", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the offset strip", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Resolved signed offset distance", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of sections in the offset strip", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an offset surface was generated", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Offsets a surface strip by a signed distance and outputs a single offset surface";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            invalidate("Surface strip is missing");
            return;
        }

        String stripError = validateSurfaceStrip(surfaceStrip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        Double distance = resolveFiniteDouble(INPUT_DISTANCE_ID, defaultDistance);
        if (distance == null) {
            invalidate("Distance is connected but invalid (must be finite)");
            return;
        }

        if (Math.abs(distance) <= EPSILON) {
            outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
            outputValues.put(OUTPUT_REGION_ID, SurfaceShellBuilder.createBoundingRegion(List.of(surfaceStrip)));
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_SECTION_COUNT_ID, surfaceStrip.getSectionCount());
            markSuccess();
            return;
        }

        SurfaceShellBuilder.ShellResult shell = SurfaceShellBuilder.buildShell(
            surfaceStrip,
            Math.abs(distance),
            distance > 0.0d ? SurfaceShellBuilder.OffsetMode.OUTSIDE : SurfaceShellBuilder.OffsetMode.INSIDE
        );
        if (shell == null) {
            invalidate("Offset surface could not be generated");
            return;
        }

        SurfaceStripData offsetSurface = distance > 0.0d ? shell.outerSurface() : shell.innerSurface();
        String offsetError = validateSurfaceStrip(offsetSurface);
        if (offsetError != null) {
            invalidate(offsetError);
            return;
        }

        outputValues.put(OUTPUT_SURFACE_STRIP_ID, offsetSurface);
        outputValues.put(OUTPUT_REGION_ID, SurfaceShellBuilder.createBoundingRegion(List.of(offsetSurface)));
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, offsetSurface.getSectionCount());
        markSuccess();
    }

    public double getDefaultDistance() {
        return defaultDistance;
    }

    public void setDefaultDistance(double defaultDistance) {
        if (!Double.isFinite(defaultDistance)) {
            return;
        }
        if (Double.compare(this.defaultDistance, defaultDistance) != 0) {
            this.defaultDistance = defaultDistance;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of("defaultDistance", defaultDistance);
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultDistance") instanceof Number value) {
            setDefaultDistance(value.doubleValue());
        }
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_SURFACE_STRIP_ID, OUTPUT_REGION_ID);
        putDoubleOutputs(0.0d, OUTPUT_DISTANCE_ID);
        putIntOutputs(0, OUTPUT_SECTION_COUNT_ID);
        markInvalid(error);
    }
}
