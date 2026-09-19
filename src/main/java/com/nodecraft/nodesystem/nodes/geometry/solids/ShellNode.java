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
    id = "geometry.solids.shell",
    displayName = "Shell Surface Strip",
    description = "Builds inner and outer offset shell layers from a surface strip and emits cap strips plus an optional geometry approximation",
    category = "geometry.solids",
    order = 8
)
public class ShellNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    public enum OffsetMode {
        OUTSIDE,
        INSIDE,
        CENTERED
    }

    @NodeProperty(displayName = "Default Thickness", category = "Shell", order = 1)
    private double defaultThickness = 1.0d;

    @NodeProperty(displayName = "Offset Mode", category = "Shell", order = 2)
    private OffsetMode offsetMode = OffsetMode.CENTERED;

    @NodeProperty(displayName = "Geometry Radius", category = "Geometry", order = 3)
    private double geometryRadius = 0.25d;

    @NodeProperty(displayName = "Longitudinal Steps", category = "Geometry", order = 4)
    private int longitudinalSteps = 4;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_OUTER_SURFACE_ID = "output_outer_surface";
    private static final String OUTPUT_INNER_SURFACE_ID = "output_inner_surface";
    private static final String OUTPUT_CAP_SURFACES_ID = "output_cap_surfaces";
    private static final String OUTPUT_ALL_SURFACES_ID = "output_all_surfaces";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_SURFACE_COUNT_ID = "output_surface_count";
    private static final String OUTPUT_THICKNESS_ID = "output_thickness";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ShellNode() {
        super(UUID.randomUUID(), "geometry.solids.shell");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to offset into a shell", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Shell thickness override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_OUTER_SURFACE_ID, "Outer Surface", "Outer offset shell surface", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_INNER_SURFACE_ID, "Inner Surface", "Inner offset shell surface", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_CAP_SURFACES_ID, "Cap Surfaces", "Start and end cap strips closing the shell", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SURFACES_ID, "All Surfaces", "Outer, inner, and cap surface strips", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Cylinder-sampled shell geometry approximation", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region covering the shell", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of sections in the shell strip", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_COUNT_ID, "Surface Count", "Number of generated shell surfaces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_THICKNESS_ID, "Thickness", "Resolved shell thickness", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when shell layers were generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds inner and outer offset shell layers from a surface strip and emits cap strips plus an optional geometry approximation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            writeEmptyOutputs();
            return;
        }

        double thickness = Math.max(0.0d, getInputDouble(INPUT_THICKNESS_ID, defaultThickness));
        if (thickness <= EPSILON) {
            writeEmptyOutputs();
            return;
        }

        SurfaceShellBuilder.ShellResult shell = SurfaceShellBuilder.buildShell(
            surfaceStrip,
            thickness,
            toBuilderMode(offsetMode)
        );
        if (shell == null) {
            writeEmptyOutputs();
            return;
        }

        List<Object> allSurfaces = new ArrayList<>(shell.allSurfaces().size());
        allSurfaces.addAll(shell.allSurfaces());
        GeometryData geometry = SurfaceShellBuilder.buildGeometry(shell.allSurfaces(), longitudinalSteps, geometryRadius);
        RegionData region = SurfaceShellBuilder.createBoundingRegion(shell.allSurfaces());

        outputValues.put(OUTPUT_OUTER_SURFACE_ID, shell.outerSurface());
        outputValues.put(OUTPUT_INNER_SURFACE_ID, shell.innerSurface());
        outputValues.put(OUTPUT_CAP_SURFACES_ID, shell.capSurfaces());
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.copyOf(allSurfaces));
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, shell.sectionCount());
        outputValues.put(OUTPUT_SURFACE_COUNT_ID, shell.allSurfaces().size());
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

    public OffsetMode getOffsetMode() {
        return offsetMode;
    }

    public void setOffsetMode(OffsetMode offsetMode) {
        OffsetMode resolved = offsetMode == null ? OffsetMode.CENTERED : offsetMode;
        if (this.offsetMode != resolved) {
            this.offsetMode = resolved;
            markDirty();
        }
    }

    public void setOffsetModeString(String offsetMode) {
        if (offsetMode == null || offsetMode.isBlank()) {
            setOffsetMode(OffsetMode.CENTERED);
            return;
        }
        try {
            setOffsetMode(OffsetMode.valueOf(offsetMode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setOffsetMode(OffsetMode.CENTERED);
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
        if (map.get("geometryRadius") instanceof Number value) {
            setGeometryRadius(value.doubleValue());
        }
        if (map.get("longitudinalSteps") instanceof Number value) {
            setLongitudinalSteps(value.intValue());
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_OUTER_SURFACE_ID, null);
        outputValues.put(OUTPUT_INNER_SURFACE_ID, null);
        outputValues.put(OUTPUT_CAP_SURFACES_ID, List.of());
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.of());
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_SURFACE_COUNT_ID, 0);
        outputValues.put(OUTPUT_THICKNESS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private SurfaceShellBuilder.OffsetMode toBuilderMode(OffsetMode mode) {
        return switch (mode) {
            case OUTSIDE -> SurfaceShellBuilder.OffsetMode.OUTSIDE;
            case INSIDE -> SurfaceShellBuilder.OffsetMode.INSIDE;
            case CENTERED -> SurfaceShellBuilder.OffsetMode.CENTERED;
        };
    }
}
