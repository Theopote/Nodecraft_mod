package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
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
    id = "geometry.primitives.deconstruct_cone",
    displayName = "Deconstruct Cone",
    description = "Extracts axis, height, radius, bounds, and analytical values from cone geometry",
    category = "geometry.primitives",
    order = 20
)
public class DeconstructConeNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_CONE_ID = "input_cone";

    private static final String OUTPUT_BASE_CENTER_ID = "output_base_center";
    private static final String OUTPUT_APEX_ID = "output_apex";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_BASE_AREA_ID = "output_base_area";
    private static final String OUTPUT_LATERAL_AREA_ID = "output_lateral_area";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructConeNode() {
        super("geometry.primitives.deconstruct_cone");

        addInputPort(new BasePort(INPUT_CONE_ID, "Cone", "Cone geometry to deconstruct", NodeDataType.CONE_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BASE_CENTER_ID, "Base Center", "Cone base center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_APEX_ID, "Apex", "Cone apex point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Cone axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cone axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cone height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Base Radius", "Cone base radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_BASE_AREA_ID, "Base Area", "Cone base circle area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_LATERAL_AREA_ID, "Lateral Area", "Cone lateral surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Cone total surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Cone volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts axis, height, radius, bounds, and analytical values from cone geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coneObj = inputValues.get(INPUT_CONE_ID);
        if (!(coneObj instanceof ConeGeometryData cone)) {
            writeEmptyOutputs("Valid cone geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateCone(cone);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d baseCenter = cone.getBaseCenter();
        Vector3d apex = cone.getApex();
        Vector3d axisVector = PrimitiveGeometryValidator.requirePositiveAxis(baseCenter, apex);
        double height = PrimitiveGeometryValidator.requirePositiveAxisLength(baseCenter, apex);
        if (axisVector == null || !Double.isFinite(height)) {
            writeEmptyOutputs("Cone height must be > 0");
            return;
        }
        double radius = cone.getBaseRadius();
        double slantHeight = PrimitiveNumericUtils.safeHypot(radius, height);
        double r2 = PrimitiveNumericUtils.safeSquare(radius);
        double baseArea = PrimitiveNumericUtils.safeMul(Math.PI, r2);
        double lateralArea = PrimitiveNumericUtils.safeMul(Math.PI * radius, slantHeight);
        double surfaceArea = PrimitiveNumericUtils.safeAdd(baseArea, lateralArea);
        double volume = PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeMul(Math.PI, r2), height) / 3.0d;
        if (!Double.isFinite(volume)) {
            volume = Double.NaN;
        }
        if (!requireFiniteOutputs(height, radius, slantHeight, baseArea, lateralArea, surfaceArea, volume)
            || !VectorUtils.isFinite(axisVector)) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(cone);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_BASE_CENTER_ID, new PointData(baseCenter));
        outputValues.put(OUTPUT_APEX_ID, new PointData(apex));
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(baseCenter, apex));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, VectorUtils.toVectorPort(axisVector));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
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
            OUTPUT_BASE_CENTER_ID,
            OUTPUT_APEX_ID,
            OUTPUT_AXIS_PATH_ID,
            OUTPUT_AXIS_VECTOR_ID,
            OUTPUT_REGION_ID,
            OUTPUT_BOUNDING_BOX_ID
        );
        putDoubleOutputs(Double.NaN,
            OUTPUT_HEIGHT_ID,
            OUTPUT_RADIUS_ID,
            OUTPUT_BASE_AREA_ID,
            OUTPUT_LATERAL_AREA_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }
}
