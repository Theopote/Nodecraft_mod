package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_cylinder",
    displayName = "Deconstruct Cylinder",
    description = "Extracts axis, radius, height, bounds, and analytical values from cylinder geometry",
    category = "geometry.primitives",
    order = 12
)
public class DeconstructCylinderNode extends BaseNode {

    private static final String INPUT_CYLINDER_ID = "input_cylinder";

    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_AXIS_LINE_ID = "output_axis_line";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_DIAMETER_ID = "output_diameter";
    private static final String OUTPUT_BASE_AREA_ID = "output_base_area";
    private static final String OUTPUT_LATERAL_AREA_ID = "output_lateral_area";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructCylinderNode() {
        super(UUID.randomUUID(), "geometry.primitives.deconstruct_cylinder");

        addInputPort(new BasePort(INPUT_CYLINDER_ID, "Cylinder", "Cylinder geometry to deconstruct", NodeDataType.CYLINDER_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Cylinder axis start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Cylinder axis end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LINE_ID, "Axis Line", "Cylinder axis line", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cylinder axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cylinder axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Cylinder radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Cylinder diameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_AREA_ID, "Base Area", "Cylinder base circle area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LATERAL_AREA_ID, "Lateral Area", "Cylinder lateral surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Cylinder total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Cylinder volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Geometric axis-aligned bounds", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when cylinder input is present", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts axis, radius, height, bounds, and analytical values from cylinder geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object cylinderObj = inputValues.get(INPUT_CYLINDER_ID);
        if (!(cylinderObj instanceof CylinderGeometryData cylinder)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        Vector3d axisVector = new Vector3d(end).sub(start);
        double height = axisVector.length();
        double radius = cylinder.getRadius();
        double diameter = radius * 2.0d;
        double baseArea = Math.PI * radius * radius;
        double lateralArea = 2.0d * Math.PI * radius * height;
        double surfaceArea = 2.0d * baseArea + lateralArea;
        double volume = baseArea * height;
        LineData axisLine = new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );
        RegionData region = GeometryVoxelizer.createBoundingRegion(cylinder);
        BoundingBoxData boundingBox = GeometryVoxelizer.createBoundingBox(region);

        outputValues.put(OUTPUT_START_ID, new PointData(start));
        outputValues.put(OUTPUT_END_ID, new PointData(end));
        outputValues.put(OUTPUT_AXIS_LINE_ID, axisLine);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, diameter);
        outputValues.put(OUTPUT_BASE_AREA_ID, baseArea);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, lateralArea);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_START_ID, null);
        outputValues.put(OUTPUT_END_ID, null);
        outputValues.put(OUTPUT_AXIS_LINE_ID, null);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_DIAMETER_ID, 0.0d);
        outputValues.put(OUTPUT_BASE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, 0.0d);
        outputValues.put(OUTPUT_VOLUME_ID, 0.0d);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}

