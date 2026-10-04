package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CapsuleGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_capsule",
    displayName = "Deconstruct Capsule",
    description = "Extracts axis, radius, component geometry, bounds, and analytical values from capsule geometry",
    category = "geometry.primitives",
    order = 30
)
public class DeconstructCapsuleNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_LENGTH_ID = "output_axis_length";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_TOTAL_LENGTH_ID = "output_total_length";
    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_START_HEMISPHERE_ID = "output_start_hemisphere";
    private static final String OUTPUT_END_HEMISPHERE_ID = "output_end_hemisphere";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructCapsuleNode() {
        super("geometry.primitives.deconstruct_capsule");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry",
            "Capsule geometry (cylinder + two hemispheres)", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Capsule axis start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Capsule axis end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Capsule axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LENGTH_ID, "Axis Length", "Distance between start and end points", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_LENGTH_ID, "Total Length", "Axis length plus two cap diameters", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Capsule middle cylinder", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_START_HEMISPHERE_ID, "Start Hemisphere", "Start cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_END_HEMISPHERE_ID, "End Hemisphere", "End cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Total capsule surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Total capsule volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts axis, radius, component geometry, bounds, and analytical values from capsule geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof CapsuleGeometryData capsule)) {
            writeEmptyOutputs("Valid capsule geometry is required");
            return;
        }

        String capsuleError = PrimitiveGeometryValidator.validateCapsule(capsule);
        if (capsuleError != null) {
            writeEmptyOutputs(capsuleError);
            return;
        }

        Vector3d start = capsule.getStart();
        Vector3d end = capsule.getEnd();
        double radius = capsule.getRadius();
        Vector3d axis = PrimitiveGeometryValidator.requirePositiveAxis(start, end);
        double axisLength = PrimitiveGeometryValidator.requirePositiveAxisLength(start, end);
        if (axis == null || !Double.isFinite(axisLength)) {
            writeEmptyOutputs("Capsule axis length must be > 0");
            return;
        }

        double totalLength = PrimitiveNumericUtils.safeAdd(axisLength, PrimitiveNumericUtils.safeMul(2.0d, radius));
        double r2 = PrimitiveNumericUtils.safeSquare(radius);
        double cylinderLateral = PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeMul(2.0d * Math.PI, radius), axisLength);
        double hemisphereCurved = PrimitiveNumericUtils.safeMul(2.0d * Math.PI, r2);
        double surfaceArea = PrimitiveNumericUtils.safeAdd(cylinderLateral, PrimitiveNumericUtils.safeMul(2.0d, hemisphereCurved));
        double cylinderVolume = PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeMul(Math.PI, r2), axisLength);
        double hemisphereVolume = PrimitiveNumericUtils.safeMul((2.0d / 3.0d) * Math.PI, PrimitiveNumericUtils.safeCube(radius));
        double volume = PrimitiveNumericUtils.safeAdd(cylinderVolume, PrimitiveNumericUtils.safeMul(2.0d, hemisphereVolume));
        if (!requireFiniteOutputs(axisLength, radius, totalLength, surfaceArea, volume)) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }

        CylinderGeometryData cylinder = capsule.cylinder();
        HemisphereGeometryData startCap = capsule.startHemisphere();
        HemisphereGeometryData endCap = capsule.endHemisphere();

        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(capsule);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_START_ID, new PointData(start));
        outputValues.put(OUTPUT_END_ID, new PointData(end));
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(start, end));
        outputValues.put(OUTPUT_AXIS_LENGTH_ID, axisLength);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_TOTAL_LENGTH_ID, totalLength);
        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_START_HEMISPHERE_ID, startCap);
        outputValues.put(OUTPUT_END_HEMISPHERE_ID, endCap);
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
            OUTPUT_CYLINDER_ID,
            OUTPUT_START_HEMISPHERE_ID,
            OUTPUT_END_HEMISPHERE_ID,
            OUTPUT_REGION_ID,
            OUTPUT_BOUNDING_BOX_ID
        );
        putDoubleOutputs(Double.NaN,
            OUTPUT_AXIS_LENGTH_ID,
            OUTPUT_RADIUS_ID,
            OUTPUT_TOTAL_LENGTH_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }
}
