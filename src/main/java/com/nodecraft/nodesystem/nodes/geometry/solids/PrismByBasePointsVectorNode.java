package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.extrude_profile_from_points",
    displayName = "Prism By Points",
    description = "Constructs prism geometry from an ordered base polygon and an extrusion vector",
    category = "geometry.solids",
    order = 2
)
public class PrismByBasePointsVectorNode extends AbstractSolidNode {

    private static final String INPUT_BASE_POINTS_ID = "input_base_points";
    private static final String INPUT_EXTRUSION_VECTOR_ID = "input_extrusion_vector";

    private static final String OUTPUT_PRISM_ID = "output_prism";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_SIDE_COUNT_ID = "output_side_count";

    public PrismByBasePointsVectorNode() {
        super(UUID.randomUUID(), "geometry.solids.extrude_profile_from_points");

        addInputPort(new BasePort(INPUT_BASE_POINTS_ID, "Base Points", "Ordered base polygon points", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_EXTRUSION_VECTOR_ID, "Extrusion Vector", "Prism extrusion vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PRISM_ID, "Prism", "Constructed prism geometry", NodeDataType.PRISM_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Side strip surface between base and top polygons", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Resolved base polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Resolved top polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Prism extrusion length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Side Count", "Number of prism side faces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a prism could be constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Constructs prism geometry from an ordered base polygon and an extrusion vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> basePoints = SolidNodeUtils.resolveStrictPointList(inputValues.get(INPUT_BASE_POINTS_ID));
        Vector3d extrusionVector = resolveDirection(INPUT_EXTRUSION_VECTOR_ID);

        if (basePoints == null) {
            invalidate("Base point list is missing or contains non-PointData / non-finite entries");
            return;
        }
        if (basePoints.size() < 3) {
            invalidate("Base polygon requires at least three points");
            return;
        }
        if (extrusionVector == null || extrusionVector.lengthSquared() <= SolidNodeUtils.EPSILON * SolidNodeUtils.EPSILON) {
            invalidate("Extrusion vector is missing, invalid, or zero-length");
            return;
        }

        double height = extrusionVector.length();
        List<Vector3d> topPoints = new ArrayList<>(basePoints.size());
        for (Vector3d basePoint : basePoints) {
            topPoints.add(new Vector3d(basePoint).add(extrusionVector));
        }

        PrismGeometryData prism = new PrismGeometryData(basePoints, extrusionVector);
        SurfaceStripData surfaceStrip;
        try {
            surfaceStrip = new SurfaceStripData(
                List.of(List.copyOf(basePoints), List.copyOf(topPoints)),
                List.of(true, true)
            );
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Side surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(surfaceStrip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_PRISM_ID, prism);
        outputValues.put(OUTPUT_GEOMETRY_ID, prism);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_BASE_POINTS_ID, SpatialValueResolver.toPointDataList(basePoints));
        outputValues.put(OUTPUT_TOP_POINTS_ID, SpatialValueResolver.toPointDataList(topPoints));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, basePoints.size());
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PRISM_ID, OUTPUT_GEOMETRY_ID, OUTPUT_SURFACE_STRIP_ID);
        putEmptyListOutputs(OUTPUT_BASE_POINTS_ID, OUTPUT_TOP_POINTS_ID);
        putDoubleOutputs(0.0d, OUTPUT_HEIGHT_ID);
        putIntOutputs(0, OUTPUT_SIDE_COUNT_ID);
        markInvalid(error);
    }
}
