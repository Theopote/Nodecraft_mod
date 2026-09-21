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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.thicken_surface",
    displayName = "Thicken Surface",
    description = "Thickens a surface strip into two offset layers with optional cap strips and a reusable geometry approximation",
    category = "geometry.solids",
    order = 9
)
public class ThickenSurfaceNode extends BaseNode {

    @NodeProperty(displayName = "Default Thickness", category = "Thickness", order = 1)
    private double defaultThickness = 1.0d;

    @NodeProperty(displayName = "Offset Mode", category = "Thickness", order = 2)
    private SurfaceShellBuilder.OffsetMode offsetMode = SurfaceShellBuilder.OffsetMode.CENTERED;

    @NodeProperty(displayName = "Include Caps", category = "Thickness", order = 3)
    private boolean includeCaps = true;

    @NodeProperty(displayName = "Geometry Radius", category = "Geometry", order = 4)
    private double geometryRadius = 0.25d;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Geometry", order = 5)
    private int longitudinalSteps = 4;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_FRONT_SURFACE_ID = "output_front_surface";
    private static final String OUTPUT_BACK_SURFACE_ID = "output_back_surface";
    private static final String OUTPUT_SIDE_CAPS_ID = "output_side_caps";
    private static final String OUTPUT_ALL_SURFACES_ID = "output_all_surfaces";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_LAYER_COUNT_ID = "output_layer_count";
    private static final String OUTPUT_THICKNESS_ID = "output_thickness";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ThickenSurfaceNode() {
        super(UUID.randomUUID(), "geometry.solids.thicken_surface");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to thicken", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness override for the thickened strip", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FRONT_SURFACE_ID, "Front Surface", "Primary offset surface layer", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_BACK_SURFACE_ID, "Back Surface", "Secondary offset surface layer", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_CAPS_ID, "Side Caps", "Cap surfaces closing the thickened strip ends", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SURFACES_ID, "All Surfaces", "All generated thickened strip surfaces", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Lattice Geometry",
            "Cylinder-lattice approximation of thickened strip (not a filled solid)", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the thickened strip", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_LAYER_COUNT_ID, "Layer Count", "Generated surface layer count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_THICKNESS_ID, "Thickness", "Resolved thickening distance", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the strip was thickened", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Thickens a surface strip into two offset layers with optional cap strips and a reusable geometry approximation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            writeEmptyOutputs();
            return;
        }

        double thickness = Math.max(0.0d, getInputDouble(INPUT_THICKNESS_ID, defaultThickness));
        SurfaceShellBuilder.ShellResult shell = SurfaceShellBuilder.buildShell(surfaceStrip, thickness, offsetMode);
        if (shell == null) {
            writeEmptyOutputs();
            return;
        }

        List<SurfaceStripData> allSurfaces = new ArrayList<>(includeCaps ? shell.allSurfaces() : List.of(shell.outerSurface(), shell.innerSurface()));
        GeometryData geometry = SurfaceShellBuilder.buildGeometry(allSurfaces, longitudinalSteps, geometryRadius);
        RegionData region = SurfaceShellBuilder.createBoundingRegion(allSurfaces);

        outputValues.put(OUTPUT_FRONT_SURFACE_ID, shell.outerSurface());
        outputValues.put(OUTPUT_BACK_SURFACE_ID, shell.innerSurface());
        outputValues.put(OUTPUT_SIDE_CAPS_ID, includeCaps ? shell.capSurfaces() : List.of());
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.copyOf(allSurfaces));
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_LAYER_COUNT_ID, allSurfaces.size());
        outputValues.put(OUTPUT_THICKNESS_ID, shell.thickness());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public double getDefaultThickness() {
        return defaultThickness;
    }

    public void setDefaultThickness(double defaultThickness) {
        double resolved = Math.max(0.0d, defaultThickness);
        if (Double.compare(this.defaultThickness, resolved) != 0) {
            this.defaultThickness = resolved;
            markDirty();
        }
    }

    public SurfaceShellBuilder.OffsetMode getOffsetMode() {
        return offsetMode;
    }

    public void setOffsetMode(SurfaceShellBuilder.OffsetMode offsetMode) {
        SurfaceShellBuilder.OffsetMode resolved = offsetMode == null ? SurfaceShellBuilder.OffsetMode.CENTERED : offsetMode;
        if (this.offsetMode != resolved) {
            this.offsetMode = resolved;
            markDirty();
        }
    }

    public void setOffsetModeString(String offsetMode) {
        if (offsetMode == null || offsetMode.isBlank()) {
            setOffsetMode(SurfaceShellBuilder.OffsetMode.CENTERED);
            return;
        }
        try {
            setOffsetMode(SurfaceShellBuilder.OffsetMode.valueOf(offsetMode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setOffsetMode(SurfaceShellBuilder.OffsetMode.CENTERED);
        }
    }

    public boolean isIncludeCaps() {
        return includeCaps;
    }

    public void setIncludeCaps(boolean includeCaps) {
        if (this.includeCaps != includeCaps) {
            this.includeCaps = includeCaps;
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
            "defaultThickness", defaultThickness,
            "offsetMode", offsetMode.name(),
            "includeCaps", includeCaps,
            "geometryRadius", geometryRadius,
            "longitudinalSteps", longitudinalSteps
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
        if (map.get("geometryRadius") instanceof Number value) {
            setGeometryRadius(value.doubleValue());
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_FRONT_SURFACE_ID, null);
        outputValues.put(OUTPUT_BACK_SURFACE_ID, null);
        outputValues.put(OUTPUT_SIDE_CAPS_ID, List.of());
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.of());
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_LAYER_COUNT_ID, 0);
        outputValues.put(OUTPUT_THICKNESS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
