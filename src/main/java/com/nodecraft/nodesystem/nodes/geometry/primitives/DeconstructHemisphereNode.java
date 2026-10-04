package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.deconstruct_hemisphere",
    displayName = "Deconstruct Hemisphere",
    description = "Extracts center, axis, radius, bounds, and analytical values from hemisphere geometry",
    category = "geometry.primitives",
    order = 22
)
public class DeconstructHemisphereNode extends AbstractPrimitiveDeconstructNode {

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

    public DeconstructHemisphereNode() {
        super("geometry.primitives.deconstruct_hemisphere");

        addInputPort(new BasePort(INPUT_HEMISPHERE_ID, "Hemisphere", "Hemisphere geometry to deconstruct", NodeDataType.HEMISPHERE_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Sphere center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_ID, "Axis", "Unit axis into the dome", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CURVED_AREA_ID, "Curved Area", "Spherical cap area (2πR²)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FLAT_AREA_ID, "Flat Area", "Disk area (πR²)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Curved + flat", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Solid hemisphere volume (2/3 πR³)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, axis, radius, bounds, and analytical values from hemisphere geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object hemisphereObj = inputValues.get(INPUT_HEMISPHERE_ID);
        if (!(hemisphereObj instanceof HemisphereGeometryData hemisphere)) {
            writeEmptyOutputs("Valid hemisphere geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateHemisphere(hemisphere);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d center = hemisphere.center();
        Vector3d axis = hemisphere.axis();
        double r = hemisphere.radius();
        double r2 = PrimitiveNumericUtils.safeSquare(r);
        double curved = PrimitiveNumericUtils.safeMul(2.0d * Math.PI, r2);
        double flat = PrimitiveNumericUtils.safeMul(Math.PI, r2);
        double surface = PrimitiveNumericUtils.safeAdd(curved, flat);
        double volume = PrimitiveNumericUtils.safeMul((2.0d / 3.0d) * Math.PI, PrimitiveNumericUtils.safeCube(r));
        if (!requireFiniteOutputs(r, curved, flat, surface, volume) || !VectorUtils.isFinite(axis)) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }

        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(hemisphere);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_AXIS_ID, VectorUtils.toVectorPort(axis));
        outputValues.put(OUTPUT_RADIUS_ID, r);
        outputValues.put(OUTPUT_CURVED_AREA_ID, curved);
        outputValues.put(OUTPUT_FLAT_AREA_ID, flat);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surface);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_AXIS_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID);
        putDoubleOutputs(Double.NaN,
            OUTPUT_RADIUS_ID,
            OUTPUT_CURVED_AREA_ID,
            OUTPUT_FLAT_AREA_ID,
            OUTPUT_SURFACE_AREA_ID,
            OUTPUT_VOLUME_ID
        );
        markInvalid(reason);
    }
}
