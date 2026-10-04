package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_prism",
    displayName = "Deconstruct Prism",
    description = "Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry",
    category = "geometry.primitives",
    order = 24
)
public class DeconstructPrismNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_PRISM_ID = "input_prism";

    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_EXTRUSION_VECTOR_ID = "output_extrusion_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_SIDE_COUNT_ID = "output_side_count";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructPrismNode() {
        super("geometry.primitives.deconstruct_prism");

        addInputPort(new BasePort(INPUT_PRISM_ID, "Prism", "Prism geometry to deconstruct", NodeDataType.PRISM_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Base polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Top polygon points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EXTRUSION_VECTOR_ID, "Extrusion Vector", "Prism extrusion vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Prism extrusion length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Side Count", "Number of prism side faces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Side surface strip between base and top polygons", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object prismObj = inputValues.get(INPUT_PRISM_ID);
        if (!(prismObj instanceof PrismGeometryData prism)) {
            writeEmptyOutputs("Valid prism geometry is required");
            return;
        }

        List<Vector3d> basePoints = prism.baseVertices();
        List<Vector3d> topPoints = prism.getTopVertices();
        Vector3d extrusionVector = prism.extrusionVector();
        double height = prism.getHeight();
        int sideCount = prism.getSideCount();
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(prism);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_BASE_POINTS_ID, SpatialValueResolver.toPointDataList(basePoints));
        outputValues.put(OUTPUT_TOP_POINTS_ID, SpatialValueResolver.toPointDataList(topPoints));
        outputValues.put(OUTPUT_EXTRUSION_VECTOR_ID, VectorUtils.toVectorPort(extrusionVector));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, sideCount);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, prism.getSideSurfaceStrip());
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putEmptyListOutputs(OUTPUT_BASE_POINTS_ID, OUTPUT_TOP_POINTS_ID);
        putNullOutputs(OUTPUT_EXTRUSION_VECTOR_ID, OUTPUT_SURFACE_STRIP_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID);
        putDoubleOutputs(0.0d, OUTPUT_HEIGHT_ID);
        putIntOutputs(0, OUTPUT_SIDE_COUNT_ID);
        markInvalid(reason);
    }
}
