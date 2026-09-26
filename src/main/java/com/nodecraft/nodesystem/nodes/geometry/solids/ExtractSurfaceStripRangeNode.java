package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.extract_surface_strip_range",
    displayName = "Extract Surface Strip Range",
    description = "Extracts a contiguous section range from a surface strip as a smaller surface strip",
    category = "geometry.solids",
    order = 12
)
public class ExtractSurfaceStripRangeNode extends BaseNode {

    public enum RangeMode {
        INDEX,
        NORMALIZED
    }

    @NodeProperty(displayName = "Range Mode", category = "Range", order = 1)
    private RangeMode rangeMode = RangeMode.INDEX;

    @NodeProperty(displayName = "Default Start", category = "Range", order = 2)
    private double defaultStart = 0.0d;

    @NodeProperty(displayName = "Default End", category = "Range", order = 3)
    private double defaultEnd = 1.0d;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";

    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_POINTS_PER_SECTION_ID = "output_points_per_section";
    private static final String OUTPUT_START_SECTION_ID = "output_start_section";
    private static final String OUTPUT_END_SECTION_ID = "output_end_section";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ExtractSurfaceStripRangeNode() {
        super(UUID.randomUUID(), "geometry.solids.extract_surface_strip_range");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to extract from", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Start section index or normalized range value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "End section index or normalized range value", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Extracted surface strip", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of extracted sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_PER_SECTION_ID, "Points Per Section", "Number of points in each extracted section", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_START_SECTION_ID, "Start Section", "Resolved inclusive start section index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_END_SECTION_ID, "End Section", "Resolved inclusive end section index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a range was extracted", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts a contiguous section range from a surface strip as a smaller surface strip";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            writeEmptyOutputs();
            return;
        }

        int sourceSectionCount = surfaceStrip.getSectionCount();
        if (sourceSectionCount < 2) {
            writeEmptyOutputs();
            return;
        }

        double startValue = getInputDouble(INPUT_START_ID, defaultStart);
        double endValue = getInputDouble(INPUT_END_ID, defaultEnd);
        int startIndex = resolveSectionIndex(startValue, sourceSectionCount);
        int endIndex = resolveSectionIndex(endValue, sourceSectionCount);
        if (startIndex > endIndex) {
            int swap = startIndex;
            startIndex = endIndex;
            endIndex = swap;
        }

        if (endIndex - startIndex + 1 < 2) {
            writeEmptyOutputs();
            return;
        }

        List<List<Vector3d>> sourceSections = surfaceStrip.sections();
        List<Boolean> sourceClosedFlags = surfaceStrip.sectionClosedFlags();
        List<List<Vector3d>> extractedSections = new ArrayList<>(endIndex - startIndex + 1);
        List<Boolean> extractedClosedFlags = new ArrayList<>(endIndex - startIndex + 1);

        for (int sectionIndex = startIndex; sectionIndex <= endIndex; sectionIndex++) {
            extractedSections.add(sourceSections.get(sectionIndex));
            extractedClosedFlags.add(sourceClosedFlags.get(sectionIndex));
        }

        SurfaceStripData extracted = new SurfaceStripData(extractedSections, extractedClosedFlags);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, extracted);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, extracted.getSectionCount());
        outputValues.put(OUTPUT_POINTS_PER_SECTION_ID, extracted.getPointsPerSection());
        outputValues.put(OUTPUT_START_SECTION_ID, startIndex);
        outputValues.put(OUTPUT_END_SECTION_ID, endIndex);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public RangeMode getRangeMode() {
        return rangeMode;
    }

    public void setRangeMode(RangeMode rangeMode) {
        RangeMode resolved = rangeMode == null ? RangeMode.INDEX : rangeMode;
        if (this.rangeMode != resolved) {
            this.rangeMode = resolved;
            markDirty();
        }
    }

    public void setRangeModeString(String rangeMode) {
        if (rangeMode == null || rangeMode.isBlank()) {
            setRangeMode(RangeMode.INDEX);
            return;
        }
        try {
            setRangeMode(RangeMode.valueOf(rangeMode.trim().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            setRangeMode(RangeMode.INDEX);
        }
    }

    public double getDefaultStart() {
        return defaultStart;
    }

    public void setDefaultStart(double defaultStart) {
        if (Double.compare(this.defaultStart, defaultStart) != 0) {
            this.defaultStart = defaultStart;
            markDirty();
        }
    }

    public double getDefaultEnd() {
        return defaultEnd;
    }

    public void setDefaultEnd(double defaultEnd) {
        if (Double.compare(this.defaultEnd, defaultEnd) != 0) {
            this.defaultEnd = defaultEnd;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "rangeMode", rangeMode.name(),
            "defaultStart", defaultStart,
            "defaultEnd", defaultEnd
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("rangeMode") instanceof String value) {
            setRangeModeString(value);
        }
        if (map.get("defaultStart") instanceof Number value) {
            setDefaultStart(value.doubleValue());
        }
        if (map.get("defaultEnd") instanceof Number value) {
            setDefaultEnd(value.doubleValue());
        }
    }

    private int resolveSectionIndex(double value, int sectionCount) {
        int lastIndex = sectionCount - 1;
        int index = switch (rangeMode) {
            case INDEX -> (int) Math.round(value);
            case NORMALIZED -> (int) Math.round(clamp(value, 0.0d, 1.0d) * lastIndex);
        };
        return Math.max(0, Math.min(lastIndex, index));
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_POINTS_PER_SECTION_ID, 0);
        outputValues.put(OUTPUT_START_SECTION_ID, 0);
        outputValues.put(OUTPUT_END_SECTION_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
