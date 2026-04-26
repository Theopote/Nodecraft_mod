package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_frustum_cone",
    displayName = "Deconstruct Frustum Cone",
    description = "Extracts axis, heights, radii, bounds, and analytical values from frustum cone geometry",
    category = "geometry.primitives",
    order = 14
)
public class DeconstructFrustumConeNode extends BaseNode {

    private static final String INPUT_FRUSTUM_ID = "input_frustum";

    private static final String OUTPUT_BASE_CENTER_ID = "output_base_center";
    private static final String OUTPUT_TOP_CENTER_ID = "output_top_center";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_BASE_RADIUS_ID = "output_base_radius";
    private static final String OUTPUT_TOP_RADIUS_ID = "output_top_radius";
    private static final String OUTPUT_SLANT_HEIGHT_ID = "output_slant_height";
    private static final String OUTPUT_BASE_AREA_ID = "output_base_area";
    private static final String OUTPUT_TOP_AREA_ID = "output_top_area";
    private static final String OUTPUT_LATERAL_AREA_ID = "output_lateral_area";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructFrustumConeNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_frustum_cone");

        addInputPort(new BasePort(INPUT_FRUSTUM_ID, "Frustum", "Frustum cone geometry to deconstruct", NodeDataType.FRUSTUM_CONE_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BASE_CENTER_ID, "Base Center", "Base face center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_TOP_CENTER_ID, "Top Center", "Top face center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Axis from base to top", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Distance between face centers", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_RADIUS_ID, "Base Radius", "Base face radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOP_RADIUS_ID, "Top Radius", "Top face radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SLANT_HEIGHT_ID, "Slant Height", "Slant length along side wall", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_AREA_ID, "Base Area", "Base disk area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOP_AREA_ID, "Top Area", "Top disk area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LATERAL_AREA_ID, "Lateral Area", "Side surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Total surface area including caps", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Interior volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when frustum input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts axis, heights, radii, bounds, and analytical values from frustum cone geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object frustumObj = inputValues.get(INPUT_FRUSTUM_ID);
        if (!(frustumObj instanceof FrustumConeGeometryData frustum)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d baseCenter = frustum.getBaseCenter();
        Vector3d topCenter = frustum.getTopCenter();
        Vector3d axisVector = frustum.getAxisVector();
        double height = frustum.getHeight();
        double rb = frustum.getBaseRadius();
        double rt = frustum.getTopRadius();

        double dr = rb - rt;
        double slant = Math.sqrt(height * height + dr * dr);
        double baseArea = Math.PI * rb * rb;
        double topArea = Math.PI * rt * rt;
        double lateralArea = Math.PI * (rb + rt) * slant;
        double surfaceArea = lateralArea + baseArea + topArea;
        double volume = (Math.PI * height / 3.0d) * (rb * rb + rb * rt + rt * rt);

        RegionData region = GeometryVoxelizer.createBoundingRegion(frustum);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_BASE_CENTER_ID, baseCenter);
        outputValues.put(OUTPUT_TOP_CENTER_ID, topCenter);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_BASE_RADIUS_ID, rb);
        outputValues.put(OUTPUT_TOP_RADIUS_ID, rt);
        outputValues.put(OUTPUT_SLANT_HEIGHT_ID, slant);
        outputValues.put(OUTPUT_BASE_AREA_ID, baseArea);
        outputValues.put(OUTPUT_TOP_AREA_ID, topArea);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, lateralArea);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_BASE_CENTER_ID, null);
        outputValues.put(OUTPUT_TOP_CENTER_ID, null);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_BASE_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_TOP_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_SLANT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_BASE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_TOP_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
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
