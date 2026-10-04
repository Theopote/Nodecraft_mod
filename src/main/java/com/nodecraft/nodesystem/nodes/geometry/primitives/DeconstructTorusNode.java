package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_torus",
    displayName = "Deconstruct Torus",
    description = "Extracts center, axis, radii, bounds, and analytical values from ring torus geometry",
    category = "geometry.primitives",
    order = 29
)
public class DeconstructTorusNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_TORUS_ID = "input_torus";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_AXIS_ID = "output_axis";
    private static final String OUTPUT_MAJOR_RADIUS_ID = "output_major_radius";
    private static final String OUTPUT_MINOR_RADIUS_ID = "output_minor_radius";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructTorusNode() {
        super("geometry.primitives.deconstruct_torus");

        addInputPort(new BasePort(INPUT_TORUS_ID, "Torus", "Torus geometry to deconstruct", NodeDataType.TORUS_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Torus center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_ID, "Axis", "Unit symmetry axis", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube cross-section radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Ring torus surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Ring torus volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, axis, radii, bounds, and analytical values from ring torus geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object torusObj = inputValues.get(INPUT_TORUS_ID);
        if (!(torusObj instanceof TorusGeometryData torus)) {
            writeEmptyOutputs("Valid torus geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateRingTorus(torus);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d center = torus.center();
        Vector3d axis = torus.axis();
        double majorRadius = torus.majorRadius();
        double minorRadius = torus.minorRadius();
        double surfaceArea = 4.0d * Math.PI * Math.PI * majorRadius * minorRadius;
        double volume = 2.0d * Math.PI * Math.PI * majorRadius * minorRadius * minorRadius;

        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(torus);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_AXIS_ID, VectorUtils.toVectorPort(axis));
        outputValues.put(OUTPUT_MAJOR_RADIUS_ID, majorRadius);
        outputValues.put(OUTPUT_MINOR_RADIUS_ID, minorRadius);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_AXIS_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID);
        putDoubleOutputs(0.0d,
            OUTPUT_MAJOR_RADIUS_ID,
            OUTPUT_MINOR_RADIUS_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }
}
