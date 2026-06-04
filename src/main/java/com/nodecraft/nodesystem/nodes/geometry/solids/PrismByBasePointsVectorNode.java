package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.extrude_profile_from_points",
    displayName = "Prism By Base Points Vector",
    description = "Constructs prism geometry from an ordered base polygon and an extrusion vector",
    category = "geometry.solids",
    order = 10
)
public class PrismByBasePointsVectorNode extends BaseNode {

    private static final String INPUT_BASE_POINTS_ID = "input_base_points";
    private static final String INPUT_EXTRUSION_VECTOR_ID = "input_extrusion_vector";

    private static final String OUTPUT_PRISM_ID = "output_prism";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_SIDE_COUNT_ID = "output_side_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PrismByBasePointsVectorNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude_profile_from_points");

        addInputPort(new BasePort(INPUT_BASE_POINTS_ID, "Base Points", "Ordered base polygon points", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_EXTRUSION_VECTOR_ID, "Extrusion Vector", "Prism extrusion vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PRISM_ID, "Prism", "Constructed prism geometry", NodeDataType.PRISM_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Side strip surface between base and top polygons", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Resolved base polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Resolved top polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Prism extrusion length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Side Count", "Number of prism side faces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a prism could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs prism geometry from an ordered base polygon and an extrusion vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object basePointsObj = inputValues.get(INPUT_BASE_POINTS_ID);
        Vector3d extrusionVector = SolidNodeUtils.resolveDirection(inputValues.get(INPUT_EXTRUSION_VECTOR_ID));

        if (!(basePointsObj instanceof List<?> basePointsInput) || extrusionVector == null) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> basePoints = SolidNodeUtils.resolvePointList(basePointsInput);
        double height = extrusionVector.length();
        if (basePoints.size() < 3 || height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> topPoints = new ArrayList<>(basePoints.size());
        for (Vector3d basePoint : basePoints) {
            topPoints.add(new Vector3d(basePoint).add(extrusionVector));
        }

        PrismGeometryData prism = new PrismGeometryData(basePoints, extrusionVector);
        SurfaceStripData surfaceStrip = new SurfaceStripData(
            List.of(List.copyOf(basePoints), List.copyOf(topPoints)),
            List.of(true, true)
        );

        outputValues.put(OUTPUT_PRISM_ID, prism);
        outputValues.put(OUTPUT_GEOMETRY_ID, prism);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.copyOf(basePoints));
        outputValues.put(OUTPUT_TOP_POINTS_ID, List.copyOf(topPoints));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, basePoints.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_PRISM_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TOP_POINTS_ID, List.of());
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

}
