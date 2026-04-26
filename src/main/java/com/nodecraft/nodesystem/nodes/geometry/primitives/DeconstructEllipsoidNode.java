package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_ellipsoid",
    displayName = "Deconstruct Ellipsoid",
    description = "Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry",
    category = "geometry.primitives",
    order = 15
)
public class DeconstructEllipsoidNode extends BaseNode {

    private static final String INPUT_ELLIPSOID_ID = "input_ellipsoid";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADII_ID = "output_radii";
    private static final String OUTPUT_DIAMETERS_ID = "output_diameters";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructEllipsoidNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_ellipsoid");

        addInputPort(new BasePort(INPUT_ELLIPSOID_ID, "Ellipsoid", "Ellipsoid geometry to deconstruct", NodeDataType.ELLIPSOID_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Ellipsoid center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADII_ID, "Radii", "Ellipsoid radii vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETERS_ID, "Diameters", "Ellipsoid diameters vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Ellipsoid volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Approximate ellipsoid surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when ellipsoid input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object ellipsoidObj = inputValues.get(INPUT_ELLIPSOID_ID);
        if (!(ellipsoidObj instanceof EllipsoidGeometryData ellipsoid)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d center = ellipsoid.getCenter();
        Vector3d radii = ellipsoid.getRadii();
        Vector3d diameters = new Vector3d(radii).mul(2.0d);
        double volume = (4.0d / 3.0d) * Math.PI * radii.x * radii.y * radii.z;
        double surfaceArea = approximateSurfaceArea(radii.x, radii.y, radii.z);
        RegionData region = GeometryVoxelizer.createBoundingRegion(ellipsoid);
        BoundingBoxData boundingBox = createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADII_ID, radii);
        outputValues.put(OUTPUT_DIAMETERS_ID, diameters);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADII_ID, null);
        outputValues.put(OUTPUT_DIAMETERS_ID, null);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
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

    private double approximateSurfaceArea(double a, double b, double c) {
        // Knud Thomsen approximation with p = 1.6075
        double p = 1.6075d;
        double ap = Math.pow(a * b, p);
        double bp = Math.pow(a * c, p);
        double cp = Math.pow(b * c, p);
        return 4.0d * Math.PI * Math.pow((ap + bp + cp) / 3.0d, 1.0d / p);
    }
}
