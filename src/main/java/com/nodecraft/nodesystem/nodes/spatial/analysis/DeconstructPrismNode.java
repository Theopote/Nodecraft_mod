package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.deconstruct_prism",
    displayName = "Deconstruct Prism",
    description = "Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry",
    category = "spatial.analysis"
)
public class DeconstructPrismNode extends BaseNode {

    private static final String INPUT_PRISM_ID = "input_prism";

    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TOP_POINTS_ID = "output_top_points";
    private static final String OUTPUT_EXTRUSION_VECTOR_ID = "output_extrusion_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_SIDE_COUNT_ID = "output_side_count";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructPrismNode() {
        super(UUID.randomUUID(), "spatial.analysis.deconstruct_prism");

        addInputPort(new BasePort(INPUT_PRISM_ID, "Prism", "Prism geometry to deconstruct", NodeDataType.PRISM_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Base polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TOP_POINTS_ID, "Top Points", "Top polygon points", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EXTRUSION_VECTOR_ID, "Extrusion Vector", "Prism extrusion vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Prism extrusion length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SIDE_COUNT_ID, "Side Count", "Number of prism side faces", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Side surface strip between base and top polygons", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when prism input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts base polygon, top polygon, extrusion, side surface strip, and bounds from prism geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object prismObj = inputValues.get(INPUT_PRISM_ID);
        if (!(prismObj instanceof PrismGeometryData prism)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> basePoints = prism.getBaseVertices();
        List<Vector3d> topPoints = prism.getTopVertices();
        Vector3d extrusionVector = prism.getExtrusionVector();
        double height = prism.getHeight();
        int sideCount = prism.getSideCount();
        RegionData region = GeometryVoxelizer.createBoundingRegion(prism);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_BASE_POINTS_ID, basePoints);
        outputValues.put(OUTPUT_TOP_POINTS_ID, topPoints);
        outputValues.put(OUTPUT_EXTRUSION_VECTOR_ID, extrusionVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, sideCount);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, prism.getSideSurfaceStrip());
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TOP_POINTS_ID, List.of());
        outputValues.put(OUTPUT_EXTRUSION_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_SIDE_COUNT_ID, 0);
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private BoundingBoxData createBoundingBox(RegionData region) {
        if (region == null || !region.isComplete()) {
            return null;
        }
        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return null;
        }
        return new BoundingBoxData(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );
    }
}
