package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SurfaceShellBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.thicken_surface",
    displayName = "Thicken Surface",
    description = "Thickens a surface strip into two offset layers with optional cap strips",
    category = "geometry.solids",
    order = 14
)
public class ThickenSurfaceNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Default Thickness", category = "Thickness", order = 1)
    private double defaultThickness = 1.0d;

    @NodeProperty(displayName = "Offset Mode", category = "Thickness", order = 2)
    private SurfaceShellBuilder.OffsetMode offsetMode = SurfaceShellBuilder.OffsetMode.CENTERED;

    @NodeProperty(displayName = "End Caps", category = "Thickness", order = 3,
        description = "When enabled, emit end-cap strips that close the thickened shell at the first and last sections")
    private boolean includeCaps = true;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_FRONT_SURFACE_ID = "output_front_surface";
    private static final String OUTPUT_BACK_SURFACE_ID = "output_back_surface";
    private static final String OUTPUT_SIDE_CAPS_ID = "output_side_caps";
    private static final String OUTPUT_ALL_SURFACES_ID = "output_all_surfaces";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_LAYER_COUNT_ID = "output_layer_count";
    private static final String OUTPUT_THICKNESS_ID = "output_thickness";

    public ThickenSurfaceNode() {
        super(UUID.randomUUID(), "geometry.solids.thicken_surface");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to thicken", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness override for the thickened strip", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRONT_SURFACE_ID, "Front Surface", "Primary offset surface layer", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_BACK_SURFACE_ID, "Back Surface", "Secondary offset surface layer", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_CAPS_ID, "End Caps", "End-cap strips closing the thickened shell at first and last sections", NodeDataType.SURFACE_STRIP_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SURFACES_ID, "All Surfaces", "All generated thickened strip surfaces", NodeDataType.SURFACE_STRIP_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the thickened strip", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_LAYER_COUNT_ID, "Layer Count", "Generated surface layer count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_THICKNESS_ID, "Thickness", "Resolved thickening distance", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the strip was thickened", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Thickens a surface strip into two offset layers with optional cap strips";
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

        Double thicknessObj = resolvePositiveDouble(INPUT_THICKNESS_ID, defaultThickness);
        if (thicknessObj == null) {
            invalidate("Thickness is connected but invalid (must be finite and > 0)");
            return;
        }
        double thickness = thicknessObj;

        SurfaceShellBuilder.ShellResult shell = SurfaceShellBuilder.buildShell(surfaceStrip, thickness, offsetMode);
        if (shell == null) {
            invalidate("Surface strip could not be thickened");
            return;
        }

        List<SurfaceStripData> allSurfaces = new ArrayList<>(
            includeCaps ? shell.allSurfaces() : List.of(shell.outerSurface(), shell.innerSurface())
        );
        RegionData region = SurfaceShellBuilder.createBoundingRegion(allSurfaces);

        outputValues.put(OUTPUT_FRONT_SURFACE_ID, shell.outerSurface());
        outputValues.put(OUTPUT_BACK_SURFACE_ID, shell.innerSurface());
        outputValues.put(OUTPUT_SIDE_CAPS_ID, includeCaps ? shell.capSurfaces() : List.of());
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.copyOf(allSurfaces));
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_LAYER_COUNT_ID, allSurfaces.size());
        outputValues.put(OUTPUT_THICKNESS_ID, shell.thickness());
        markSuccess();
    }

    public double getDefaultThickness() {
        return defaultThickness;
    }

    public void setDefaultThickness(double defaultThickness) {
        if (!Double.isFinite(defaultThickness) || defaultThickness <= 0.0d) {
            return;
        }
        markDirtyIfChanged(this.defaultThickness, defaultThickness);
        this.defaultThickness = defaultThickness;
    }

    public SurfaceShellBuilder.OffsetMode getOffsetMode() {
        return offsetMode;
    }

    public void setOffsetMode(SurfaceShellBuilder.OffsetMode offsetMode) {
        if (offsetMode == null) {
            return;
        }
        markDirtyIfChanged(this.offsetMode, offsetMode);
        this.offsetMode = offsetMode;
    }

    public void setOffsetModeString(String offsetMode) {
        if (offsetMode == null || offsetMode.isBlank()) {
            return;
        }
        try {
            setOffsetMode(SurfaceShellBuilder.OffsetMode.valueOf(offsetMode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            // Unknown keys stay at the current OffsetMode; no silent CENTERED default.
        }
    }

    public boolean isIncludeCaps() {
        return includeCaps;
    }

    public void setIncludeCaps(boolean includeCaps) {
        markDirtyIfChanged(this.includeCaps, includeCaps);
        this.includeCaps = includeCaps;
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "defaultThickness", defaultThickness,
            "offsetMode", offsetMode.name(),
            "includeCaps", includeCaps
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultThickness") instanceof Number value) {
            setDefaultThickness(value.doubleValue());
        }
        if (map.get("offsetMode") instanceof String value) {
            setOffsetModeString(value);
        }
        if (map.get("includeCaps") instanceof Boolean value) {
            setIncludeCaps(value);
        }
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_FRONT_SURFACE_ID, OUTPUT_BACK_SURFACE_ID, OUTPUT_REGION_ID);
        putEmptyListOutputs(OUTPUT_SIDE_CAPS_ID, OUTPUT_ALL_SURFACES_ID);
        putIntOutputs(0, OUTPUT_LAYER_COUNT_ID);
        putDoubleOutputs(0.0d, OUTPUT_THICKNESS_ID);
        markInvalid(error);
    }
}
