package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_cone",
    displayName = "Deconstruct Cone",
    description = "Extracts axis, height, radius, bounds, and analytical values from cone geometry",
    category = "geometry.primitives",
    order = 13
)
public class DeconstructConeNode extends BaseNode {

    private static final String INPUT_CONE_ID = "input_cone";

    private static final String OUTPUT_BASE_CENTER_ID = "output_base_center";
    private static final String OUTPUT_APEX_ID = "output_apex";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_BASE_AREA_ID = "output_base_area";
    private static final String OUTPUT_LATERAL_AREA_ID = "output_lateral_area";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructConeNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_cone");

        addInputPort(new BasePort(INPUT_CONE_ID, "Cone", "Cone geometry to deconstruct", NodeDataType.CONE_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BASE_CENTER_ID, "Base Center", "Cone base center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_APEX_ID, "Apex", "Cone apex point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cone axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cone height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Base Radius", "Cone base radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_AREA_ID, "Base Area", "Cone base circle area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LATERAL_AREA_ID, "Lateral Area", "Cone lateral surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Cone total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Cone volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when cone input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts axis, height, radius, bounds, and analytical values from cone geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coneObj = inputValues.get(INPUT_CONE_ID);
        if (!(coneObj instanceof ConeGeometryData cone)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d apex = cone.getApex();
        Vector3d axisVector = cone.getAxisVector();
        double height = cone.getHeight();
        double radius = cone.getBaseRadius();
        double slantHeight = Math.sqrt(radius * radius + height * height);
        double baseArea = Math.PI * radius * radius;
        double lateralArea = Math.PI * radius * slantHeight;
        double surfaceArea = baseArea + lateralArea;
        double volume = (Math.PI * radius * radius * height) / 3.0d;
        RegionData region = GeometryVoxelizer.createBoundingRegion(cone);
        BoundingBoxData boundingBox = GeometryVoxelizer.createBoundingBox(region);

        outputValues.put(OUTPUT_BASE_CENTER_ID, new PointData(baseCenter));
        outputValues.put(OUTPUT_APEX_ID, new PointData(apex));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_BASE_AREA_ID, baseArea);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, lateralArea);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_BASE_CENTER_ID, null);
        outputValues.put(OUTPUT_APEX_ID, null);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_BASE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

