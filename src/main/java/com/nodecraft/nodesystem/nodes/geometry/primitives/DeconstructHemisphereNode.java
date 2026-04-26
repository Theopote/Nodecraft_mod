package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_hemisphere",
    displayName = "Deconstruct Hemisphere",
    description = "Extracts center, axis, radius, bounds, and analytical values from hemisphere geometry",
    category = "geometry.primitives",
    order = 19
)
public class DeconstructHemisphereNode extends BaseNode {

    private static final String INPUT_HEMISPHERE_ID = "input_hemisphere";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_AXIS_ID = "output_axis";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_CURVED_AREA_ID = "output_curved_area";
    private static final String OUTPUT_FLAT_AREA_ID = "output_flat_area";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructHemisphereNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_hemisphere");

        addInputPort(new BasePort(INPUT_HEMISPHERE_ID, "Hemisphere", "Hemisphere geometry to deconstruct", NodeDataType.HEMISPHERE_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Sphere center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_ID, "Axis", "Unit axis into the dome", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CURVED_AREA_ID, "Curved Area", "Spherical cap area (2πR²)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FLAT_AREA_ID, "Flat Area", "Disk area (πR²)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Curved + flat", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Solid hemisphere volume (2/3 πR³)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when hemisphere input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, axis, radius, bounds, and analytical values from hemisphere geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object hemisphereObj = inputValues.get(INPUT_HEMISPHERE_ID);
        if (!(hemisphereObj instanceof HemisphereGeometryData hemisphere)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d center = hemisphere.getCenter();
        Vector3d axis = hemisphere.getAxis();
        double r = hemisphere.getRadius();
        double curved = 2.0d * Math.PI * r * r;
        double flat = Math.PI * r * r;
        double surface = curved + flat;
        double volume = (2.0d / 3.0d) * Math.PI * r * r * r;

        RegionData region = GeometryVoxelizer.createBoundingRegion(hemisphere);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_AXIS_ID, axis);
        outputValues.put(OUTPUT_RADIUS_ID, r);
        outputValues.put(OUTPUT_CURVED_AREA_ID, curved);
        outputValues.put(OUTPUT_FLAT_AREA_ID, flat);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surface);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_AXIS_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_CURVED_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_FLAT_AREA_ID, 0.0d);
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
