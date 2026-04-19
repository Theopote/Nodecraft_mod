package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SurfaceStripBridge;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
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

        List<List<Vector3d>> sourceSections = surfaceStrip.getSections();
        List<Boolean> closedFlags = surfaceStrip.getSectionClosedFlags();
        if (sourceSections.size() < 2 || sourceSections.getFirst().size() < 2) {
            writeEmptyOutputs();
            return;
        }

        double outerDistance = resolveOuterDistance(thickness);
        double innerDistance = resolveInnerDistance(thickness);

        List<List<Vector3d>> outerSections = new ArrayList<>(sourceSections.size());
        List<List<Vector3d>> innerSections = new ArrayList<>(sourceSections.size());

        for (int sectionIndex = 0; sectionIndex < sourceSections.size(); sectionIndex++) {
            List<Vector3d> sourceSection = sourceSections.get(sectionIndex);
            List<Vector3d> outerSection = new ArrayList<>(sourceSection.size());
            List<Vector3d> innerSection = new ArrayList<>(sourceSection.size());

            for (int pointIndex = 0; pointIndex < sourceSection.size(); pointIndex++) {
                Vector3d point = sourceSection.get(pointIndex);
                Vector3d normal = computeShellNormal(sourceSections, closedFlags, sectionIndex, pointIndex);

                outerSection.add(new Vector3d(point).fma(outerDistance, normal));
                innerSection.add(new Vector3d(point).fma(-innerDistance, normal));
            }

            outerSections.add(List.copyOf(outerSection));
            innerSections.add(List.copyOf(innerSection));
        }

        SurfaceStripData outerSurface = new SurfaceStripData(outerSections, closedFlags);
        SurfaceStripData innerSurface = new SurfaceStripData(innerSections, closedFlags);

        List<SurfaceStripData> capSurfaces = createCapSurfaces(outerSections, innerSections, closedFlags);
        List<Object> allSurfaces = new ArrayList<>(2 + capSurfaces.size());
        allSurfaces.add(outerSurface);
        allSurfaces.add(innerSurface);
        allSurfaces.addAll(capSurfaces);

        GeometryData geometry = buildShellGeometry(allSurfaces);
        RegionData region = createBoundingRegion(outerSections, innerSections);

        outputValues.put(OUTPUT_OUTER_SURFACE_ID, outerSurface);
        outputValues.put(OUTPUT_INNER_SURFACE_ID, innerSurface);
        outputValues.put(OUTPUT_CAP_SURFACES_ID, List.copyOf(capSurfaces));
        outputValues.put(OUTPUT_ALL_SURFACES_ID, List.copyOf(allSurfaces));
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, sourceSections.size());
        outputValues.put(OUTPUT_SURFACE_COUNT_ID, allSurfaces.size());
        outputValues.put(OUTPUT_THICKNESS_ID, thickness);
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

    private double resolveOuterDistance(double thickness) {
        return switch (offsetMode) {
            case OUTSIDE -> thickness;
            case INSIDE -> 0.0d;
            case CENTERED -> thickness * 0.5d;
        };
    }

    private double resolveInnerDistance(double thickness) {
        return switch (offsetMode) {
            case OUTSIDE -> 0.0d;
            case INSIDE -> thickness;
            case CENTERED -> thickness * 0.5d;
        };
    }

    private Vector3d computeShellNormal(List<List<Vector3d>> sections,
                                        List<Boolean> closedFlags,
                                        int sectionIndex,
                                        int pointIndex) {
        List<Vector3d> section = sections.get(sectionIndex);
        Vector3d point = section.get(pointIndex);

        Vector3d sectionTangent = computeSectionTangent(section, closedFlags.get(sectionIndex), pointIndex);
        Vector3d railTangent = computeRailTangent(sections, sectionIndex, pointIndex);

        Vector3d normal = new Vector3d(sectionTangent).cross(railTangent);
        if (normal.lengthSquared() <= EPSILON) {
            normal = fallbackNormal(sectionTangent, railTangent, point);
        }
        if (normal.lengthSquared() <= EPSILON) {
            normal = new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return normal.normalize();
    }

    private Vector3d computeSectionTangent(List<Vector3d> section, boolean closed, int pointIndex) {
        int size = section.size();
        Vector3d previous = section.get(clampSectionIndex(pointIndex - 1, size, closed));
        Vector3d next = section.get(clampSectionIndex(pointIndex + 1, size, closed));

        if (!closed) {
            if (pointIndex == 0) {
                previous = section.get(0);
            } else if (pointIndex == size - 1) {
                next = section.get(size - 1);
            }
        }

        Vector3d tangent = new Vector3d(next).sub(previous);
        if (tangent.lengthSquared() <= EPSILON) {
            if (pointIndex < size - 1) {
                tangent = new Vector3d(section.get(pointIndex + 1)).sub(section.get(pointIndex));
            } else if (pointIndex > 0) {
                tangent = new Vector3d(section.get(pointIndex)).sub(section.get(pointIndex - 1));
            }
        }
        return tangent.lengthSquared() <= EPSILON ? new Vector3d(1.0d, 0.0d, 0.0d) : tangent.normalize();
    }

    private int clampSectionIndex(int index, int size, boolean closed) {
        if (closed) {
            int resolved = index % size;
            return resolved < 0 ? resolved + size : resolved;
        }
        return Math.max(0, Math.min(size - 1, index));
    }

    private Vector3d computeRailTangent(List<List<Vector3d>> sections, int sectionIndex, int pointIndex) {
        Vector3d tangent;
        if (sectionIndex == 0) {
            tangent = new Vector3d(sections.get(1).get(pointIndex)).sub(sections.get(0).get(pointIndex));
        } else if (sectionIndex == sections.size() - 1) {
            tangent = new Vector3d(sections.get(sectionIndex).get(pointIndex)).sub(sections.get(sectionIndex - 1).get(pointIndex));
        } else {
            tangent = new Vector3d(sections.get(sectionIndex + 1).get(pointIndex)).sub(sections.get(sectionIndex - 1).get(pointIndex));
        }
        return tangent.lengthSquared() <= EPSILON ? new Vector3d(0.0d, 0.0d, 1.0d) : tangent.normalize();
    }

    private Vector3d fallbackNormal(Vector3d sectionTangent, Vector3d railTangent, Vector3d point) {
        Vector3d reference = Math.abs(railTangent.y) < 0.95d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d candidate = new Vector3d(sectionTangent).cross(reference);
        if (candidate.lengthSquared() <= EPSILON) {
            candidate = new Vector3d(railTangent).cross(reference);
        }
        if (candidate.lengthSquared() <= EPSILON) {
            candidate = new Vector3d(point).cross(reference);
        }
        return candidate;
    }

    private List<SurfaceStripData> createCapSurfaces(List<List<Vector3d>> outerSections,
                                                     List<List<Vector3d>> innerSections,
                                                     List<Boolean> closedFlags) {
        List<SurfaceStripData> caps = new ArrayList<>(2);
        if (outerSections.isEmpty()) {
            return caps;
        }

        boolean firstClosed = !closedFlags.isEmpty() && Boolean.TRUE.equals(closedFlags.getFirst());
        caps.add(new SurfaceStripData(
            List.of(outerSections.getFirst(), innerSections.getFirst()),
            List.of(firstClosed, firstClosed)
        ));

        boolean lastClosed = !closedFlags.isEmpty() && Boolean.TRUE.equals(closedFlags.getLast());
        caps.add(new SurfaceStripData(
            List.of(outerSections.getLast(), innerSections.getLast()),
            List.of(lastClosed, lastClosed)
        ));
        return caps;
    }

    private @Nullable GeometryData buildShellGeometry(List<Object> shellSurfaces) {
        if (geometryRadius <= EPSILON) {
            return null;
        }

        List<GeometryData> geometries = new ArrayList<>();
        for (Object shellSurface : shellSurfaces) {
            if (shellSurface instanceof SurfaceStripData surfaceStrip) {
                GeometryData geometry = SurfaceStripBridge.toGeometry(
                    surfaceStrip,
                    longitudinalSteps,
                    SurfaceStripBridge.BridgeMode.LATTICE,
                    geometryRadius
                );
                if (geometry != null) {
                    geometries.add(geometry);
                }
            }
        }

        if (geometries.isEmpty()) {
            return null;
        }
        return geometries.size() == 1 ? geometries.getFirst() : new CompositeGeometryData(geometries);
    }

    private @Nullable RegionData createBoundingRegion(List<List<Vector3d>> outerSections, List<List<Vector3d>> innerSections) {
        boolean hasPoint = false;
        double minX = 0.0d;
        double minY = 0.0d;
        double minZ = 0.0d;
        double maxX = 0.0d;
        double maxY = 0.0d;
        double maxZ = 0.0d;

        for (List<List<Vector3d>> sectionSet : List.of(outerSections, innerSections)) {
            for (List<Vector3d> section : sectionSet) {
                for (Vector3d point : section) {
                    if (!hasPoint) {
                        minX = maxX = point.x;
                        minY = maxY = point.y;
                        minZ = maxZ = point.z;
                        hasPoint = true;
                        continue;
                    }
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                    maxZ = Math.max(maxZ, point.z);
                }
            }
        }

        if (!hasPoint) {
            return null;
        }
        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
