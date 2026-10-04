package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
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
    id = "geometry.primitives.deconstruct_ellipsoid",
    displayName = "Deconstruct Ellipsoid",
    description = "Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry",
    category = "geometry.primitives",
    order = 23
)
public class DeconstructEllipsoidNode extends AbstractPrimitiveDeconstructNode {

    private static final String INPUT_ELLIPSOID_ID = "input_ellipsoid";

    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADII_ID = "output_radii";
    private static final String OUTPUT_DIAMETERS_ID = "output_diameters";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_SURFACE_AREA_ID = "output_surface_area";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";

    public DeconstructEllipsoidNode() {
        super("geometry.primitives.deconstruct_ellipsoid");

        addInputPort(new BasePort(INPUT_ELLIPSOID_ID, "Ellipsoid", "Ellipsoid geometry to deconstruct", NodeDataType.ELLIPSOID_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Ellipsoid center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RADII_ID, "Radii", "Ellipsoid radii vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETERS_ID, "Diameters", "Ellipsoid diameters vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Ellipsoid volume", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_AREA_ID, "Surface Area", "Approximate ellipsoid surface area", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", REGION_PORT_DESCRIPTION, NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", BOUNDING_BOX_PORT_DESCRIPTION, NodeDataType.BOUNDING_BOX, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Extracts center, radii, bounds, volume, and approximate surface area from ellipsoid geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object ellipsoidObj = inputValues.get(INPUT_ELLIPSOID_ID);
        if (!(ellipsoidObj instanceof EllipsoidGeometryData ellipsoid)) {
            writeEmptyOutputs("Valid ellipsoid geometry is required");
            return;
        }

        String error = PrimitiveGeometryValidator.validateEllipsoid(ellipsoid);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d center = ellipsoid.getCenter();
        Vector3d radii = ellipsoid.getRadii();
        Vector3d diameters = VectorUtils.safeScale(radii, 2.0d);
        double volume = PrimitiveNumericUtils.safeMul(
            (4.0d / 3.0d) * Math.PI,
            PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeMul(radii.x, radii.y), radii.z));
        double surfaceArea = approximateSurfaceArea(radii.x, radii.y, radii.z);
        if (diameters == null
            || !requireFiniteOutputs(volume, surfaceArea, diameters.x, diameters.y, diameters.z)
            || !VectorUtils.isFinite(radii)) {
            writeEmptyOutputs("Derived analytical values are non-finite");
            return;
        }
        BoundsAndRegion boundsAndRegion = resolveContinuousBoundsAndRegion(ellipsoid);
        if (boundsAndRegion == null) {
            writeEmptyOutputs("Unable to resolve continuous bounds");
            return;
        }

        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADII_ID, VectorUtils.toVectorPort(radii));
        outputValues.put(OUTPUT_DIAMETERS_ID, VectorUtils.toVectorPort(diameters));
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_SURFACE_AREA_ID, surfaceArea);
        outputValues.put(OUTPUT_REGION_ID, boundsAndRegion.region());
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundsAndRegion.boundingBox());
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CENTER_ID, OUTPUT_RADII_ID, OUTPUT_DIAMETERS_ID, OUTPUT_REGION_ID, OUTPUT_BOUNDING_BOX_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_VOLUME_ID, OUTPUT_SURFACE_AREA_ID);
        markInvalid(reason);
    }

    private double approximateSurfaceArea(double a, double b, double c) {
        double p = 1.6075d;
        double ap = Math.pow(PrimitiveNumericUtils.safeMul(a, b), p);
        double bp = Math.pow(PrimitiveNumericUtils.safeMul(a, c), p);
        double cp = Math.pow(PrimitiveNumericUtils.safeMul(b, c), p);
        if (!PrimitiveNumericUtils.allFinite(ap, bp, cp)) {
            return Double.NaN;
        }
        double mean = PrimitiveNumericUtils.safeMul(PrimitiveNumericUtils.safeAdd(PrimitiveNumericUtils.safeAdd(ap, bp), cp), 1.0d / 3.0d);
        if (!Double.isFinite(mean)) {
            return Double.NaN;
        }
        return PrimitiveNumericUtils.safeMul(4.0d * Math.PI, Math.pow(mean, 1.0d / p));
    }
}
