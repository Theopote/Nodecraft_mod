package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_cylinder",
    displayName = "Deconstruct Cylinder",
    description = "Extracts axis, radius, height, bounds, and analytical values from cylinder geometry",
    category = "geometry.primitives",
    order = 19
)
public class DeconstructCylinderNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_CYLINDER_ID = "input_cylinder";

    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
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

    public DeconstructCylinderNode() {
        super("geometry.primitives.deconstruct_cylinder");

        addInputPort(new BasePort(INPUT_CYLINDER_ID, "Cylinder", "Cylinder geometry to deconstruct", NodeDataType.CYLINDER_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Cylinder axis start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Cylinder axis end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Cylinder axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cylinder axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cylinder axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Cylinder radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Cylinder diameter", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_AREA_ID, "Base Area", "Cylinder base circle area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LATERAL_AREA_ID, "Lateral Area", "Cylinder lateral surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Cylinder total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Cylinder volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts axis, radius, height, bounds, and analytical values from cylinder geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object cylinderObj = inputValues.get(INPUT_CYLINDER_ID);
        if (!(cylinderObj instanceof CylinderGeometryData cylinder)) {
            writeEmptyOutputs("Valid cylinder geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateCylinder(cylinder);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d start = cylinder.getStart();
        Vector3d end = cylinder.getEnd();
        Vector3d axisVector = PrimitiveGeometryValidator.requirePositiveAxis(start, end);
        double height = PrimitiveGeometryValidator.requirePositiveAxisLength(start, end);
        if (axisVector == null || !Double.isFinite(height)) {
            writeEmptyOutputs("Cylinder axis length must be > 0");
            return;
        }
        double radius = cylinder.getRadius();
        double diameter = PrimitiveNumericUtils.safeMul(radius, 2.0d);
        double r2 = PrimitiveNumericUtils.safeSquare(radius);
        double baseArea = PrimitiveNumericUtils.safeMul(Math.PI, r2);
        double lateralArea = PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeMul(2.0d * Math.PI, radius), height);
        double surfaceArea = PrimitiveNumericUtils.safeAdd(PrimitiveNumericUtils.safeMul(2.0d, baseArea), lateralArea);
        double volume = PrimitiveNumericUtils.safeMul(baseArea, height);
        if (!requireFiniteOutputs(height, radius, diameter, baseArea, lateralArea, surfaceArea, volume)
            || !VectorUtils.isFinite(axisVector)) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(cylinder);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_START_ID, new PointData(start));
        outputValues.put(OUTPUT_END_ID, new PointData(end));
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(start, end));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, VectorUtils.toVectorPort(axisVector));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, diameter);
        outputValues.put(OUTPUT_BASE_AREA_ID, baseArea);
        outputValues.put(OUTPUT_LATERAL_AREA_ID, lateralArea);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(
            OUTPUT_START_ID,
            OUTPUT_END_ID,
            OUTPUT_AXIS_PATH_ID,
            OUTPUT_AXIS_VECTOR_ID,
            OUTPUT_REGION_ID,
            OUTPUT_BOUNDING_BOX_ID
        );
        putDoubleOutputs(Double.NaN,
            OUTPUT_HEIGHT_ID,
            OUTPUT_RADIUS_ID,
            OUTPUT_DIAMETER_ID,
            OUTPUT_BASE_AREA_ID,
            OUTPUT_LATERAL_AREA_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }
}
