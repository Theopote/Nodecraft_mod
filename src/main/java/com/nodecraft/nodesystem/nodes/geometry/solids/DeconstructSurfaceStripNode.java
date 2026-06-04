package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.deconstruct_surface_strip",
    displayName = "Deconstruct Surface Strip",
    description = "Breaks a surface strip into section paths, flattened points, and rail segments",
    category = "geometry.solids",
    order = 11
)
public class DeconstructSurfaceStripNode extends BaseNode {

    private static final String INPUT_SURFACE_STRIP_ID = "input_surface_strip";

    private static final String OUTPUT_SECTION_PATHS_ID = "output_section_paths";
    private static final String OUTPUT_ALL_POINTS_ID = "output_all_points";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_SECTION_COUNT_ID = "output_section_count";
    private static final String OUTPUT_POINTS_PER_SECTION_ID = "output_points_per_section";
    private static final String OUTPUT_ALL_CLOSED_ID = "output_all_closed";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructSurfaceStripNode() {
        super(UUID.randomUUID(), "geometry.solids.deconstruct_surface_strip");

        addInputPort(new BasePort(INPUT_SURFACE_STRIP_ID, "Surface Strip", "Surface strip to deconstruct", NodeDataType.SURFACE_STRIP, this));

        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_ID, "Section Paths", "Polyline for each section", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ALL_POINTS_ID, "All Points", "Flattened ordered section points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "Line segments connecting corresponding points between sections", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_COUNT_ID, "Section Count", "Number of sections in the strip", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_PER_SECTION_ID, "Points Per Section", "Number of points stored in each section", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_CLOSED_ID, "All Closed", "True when every section is marked closed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a surface strip was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Breaks a surface strip into section paths, flattened points, and rail segments";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object surfaceStripObj = inputValues.get(INPUT_SURFACE_STRIP_ID);
        if (!(surfaceStripObj instanceof SurfaceStripData surfaceStrip)) {
            writeEmptyOutputs();
            return;
        }

        List<List<Vector3d>> sections = surfaceStrip.getSections();
        List<Boolean> closedFlags = surfaceStrip.getSectionClosedFlags();
        List<Object> sectionPaths = new ArrayList<>(sections.size());
        List<LineData> railSegments = new ArrayList<>();

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            sectionPaths.add(SolidNodeUtils.createPolyline(sections.get(sectionIndex), closedFlags.get(sectionIndex)));
        }

        for (int sectionIndex = 0; sectionIndex < sections.size() - 1; sectionIndex++) {
            List<Vector3d> current = sections.get(sectionIndex);
            List<Vector3d> next = sections.get(sectionIndex + 1);
            int pairCount = Math.min(current.size(), next.size());
            for (int pointIndex = 0; pointIndex < pairCount; pointIndex++) {
                Vector3d start = current.get(pointIndex);
                Vector3d end = next.get(pointIndex);
                railSegments.add(new LineData(
                    new Vec3d(start.x, start.y, start.z),
                    new Vec3d(end.x, end.y, end.z)
                ));
            }
        }

        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.copyOf(sectionPaths));
        outputValues.put(OUTPUT_ALL_POINTS_ID, surfaceStrip.getFlattenedPoints());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_SECTION_COUNT_ID, surfaceStrip.getSectionCount());
        outputValues.put(OUTPUT_POINTS_PER_SECTION_ID, surfaceStrip.getPointsPerSection());
        outputValues.put(OUTPUT_ALL_CLOSED_ID, surfaceStrip.areAllSectionsClosed());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SECTION_PATHS_ID, List.of());
        outputValues.put(OUTPUT_ALL_POINTS_ID, List.of());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_SECTION_COUNT_ID, 0);
        outputValues.put(OUTPUT_POINTS_PER_SECTION_ID, 0);
        outputValues.put(OUTPUT_ALL_CLOSED_ID, false);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

}
