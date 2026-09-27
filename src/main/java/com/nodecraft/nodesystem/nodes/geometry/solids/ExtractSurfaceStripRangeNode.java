package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
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
    description = "Extracts a contiguous normalized-U section range from a surface strip as a smaller surface strip",
    category = "geometry.solids",
    order = 15
)
public class ExtractSurfaceStripRangeNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Default Start", category = "Range", order = 1)
    private double defaultStart = 0.0d;

    @NodeProperty(displayName = "Default End", category = "Range", order = 2)
    private double defaultEnd = 1.0d;

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";

    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_POINTS_PER_SECTION_ID = "output_points_per_section";
    private static final String OUTPUT_START_SECTION_ID = "output_start_section";
    private static final String OUTPUT_END_SECTION_ID = "output_end_section";

    public ExtractSurfaceStripRangeNode() {
        super(UUID.randomUUID(), "geometry.solids.extract_surface_strip_range");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to extract from", NodeDataType.SURFACE_STRIP, this));
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Normalized start U in [0, 1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Normalized end U in [0, 1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Extracted surface strip", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of extracted sections", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_PER_SECTION_ID, "Points Per Section", "Number of points in each extracted section", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_START_SECTION_ID, "Start Section", "Resolved inclusive start section index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_END_SECTION_ID, "End Section", "Resolved inclusive end section index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a range was extracted", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
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

        int sourceSectionCount = surfaceStrip.getSectionCount();
        Double startU = resolveNormalizedU(INPUT_START_ID, defaultStart);
        if (startU == null) {
            invalidate("Start is connected but invalid (must be finite and in [0, 1])");
            return;
        }
        Double endU = resolveNormalizedU(INPUT_END_ID, defaultEnd);
        if (endU == null) {
            invalidate("End is connected but invalid (must be finite and in [0, 1])");
            return;
        }
        if (startU >= endU) {
            invalidate("Start must be less than end on normalized U");
            return;
        }

        int lastIndex = sourceSectionCount - 1;
        int startIndex = (int) Math.round(startU * lastIndex);
        int endIndex = (int) Math.round(endU * lastIndex);
        startIndex = Math.max(0, Math.min(lastIndex, startIndex));
        endIndex = Math.max(0, Math.min(lastIndex, endIndex));
        if (startIndex > endIndex) {
            int swap = startIndex;
            startIndex = endIndex;
            endIndex = swap;
        }
        if (endIndex - startIndex + 1 < 2) {
            invalidate("Extracted range must include at least two sections");
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

        SurfaceStripData extracted;
        try {
            extracted = new SurfaceStripData(extractedSections, extractedClosedFlags);
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Extracted surface strip is invalid" : ex.getMessage());
            return;
        }

        outputValues.put(OUTPUT_SURFACE_STRIP_ID, extracted);
        outputValues.put(OUTPUT_SECTION_COUNT_ID, extracted.getSectionCount());
        outputValues.put(OUTPUT_POINTS_PER_SECTION_ID, extracted.getPointsPerSection());
        outputValues.put(OUTPUT_START_SECTION_ID, startIndex);
        outputValues.put(OUTPUT_END_SECTION_ID, endIndex);
        markSuccess();
    }

    public double getDefaultStart() {
        return defaultStart;
    }

    public void setDefaultStart(double defaultStart) {
        markDirtyIfChanged(this.defaultStart, defaultStart);
        this.defaultStart = defaultStart;
    }

    public double getDefaultEnd() {
        return defaultEnd;
    }

    public void setDefaultEnd(double defaultEnd) {
        markDirtyIfChanged(this.defaultEnd, defaultEnd);
        this.defaultEnd = defaultEnd;
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of(
            "defaultStart", defaultStart,
            "defaultEnd", defaultEnd
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultStart") instanceof Number value) {
            setDefaultStart(value.doubleValue());
        }
        if (map.get("defaultEnd") instanceof Number value) {
            setDefaultEnd(value.doubleValue());
        }
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_SURFACE_STRIP_ID);
        putIntOutputs(0, OUTPUT_SECTION_COUNT_ID, OUTPUT_POINTS_PER_SECTION_ID,
            OUTPUT_START_SECTION_ID, OUTPUT_END_SECTION_ID);
        markInvalid(error);
    }
}
