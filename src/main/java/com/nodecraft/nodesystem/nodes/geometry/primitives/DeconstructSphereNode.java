package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.deconstruct_sphere",
    displayName = "Deconstruct Sphere",
    description = "Extracts center, radius, diameter, bounds, area, and volume from sphere geometry",
    category = "geometry.primitives",
    order = 11
)
public class DeconstructSphereNode extends BaseNode {

    private static final String INPUT_SPHERE_ID = "input_sphere";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_DIAMETER_ID = "output_diameter";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructSphereNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_sphere");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to deconstruct", NodeDataType.SPHERE, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Sphere center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Sphere diameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Analytical sphere surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Analytical sphere volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sphere input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts center, radius, diameter, bounds, area, and volume from sphere geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        if (!(sphereObj instanceof SphereData sphere)) {
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
            outputValues.put(OUTPUT_DIAMETER_ID, 0.0d);
            outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
            outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
            outputValues.put(OUTPUT_REGION_ID, null);
            outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d center = sphere.getCenter();
        double radius = sphere.getRadius();
        double diameter = radius * 2.0d;
        double surfaceArea = 4.0d * Math.PI * radius * radius;
        double volume = (4.0d / 3.0d) * Math.PI * radius * radius * radius;
        RegionData region = GeometryVoxelizer.createBoundingRegion(sphere);
        BoundingBoxData boundingBox = GeometryVoxelizer.createBoundingBox(region);

        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, diameter);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
