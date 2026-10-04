package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.sphere_from_diameter",
    displayName = "Sphere By Diameter",
    description = "Constructs sphere geometry from two diameter endpoints",
    category = "geometry.primitives",
    order = 4
)
public class SphereByDiameterNode extends AbstractPrimitiveNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";

    private static final String OUTPUT_SPHERE_ID = "output_sphere";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_DIAMETER_ID = "output_diameter";
    private static final String OUTPUT_DIAMETER_PATH_ID = "output_diameter_path";

    public SphereByDiameterNode() {
        super("geometry.primitives.sphere_from_diameter");

        addInputPort(new BasePort(INPUT_START_ID, "Point A", "First diameter endpoint", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "Point B", "Second diameter endpoint", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_SPHERE_ID, "Sphere", "Constructed sphere geometry", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved sphere center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_ID, "Diameter", "Diameter length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_DIAMETER_PATH_ID, "Diameter Path", "Path between both diameter endpoints", NodeDataType.PATH, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs sphere geometry from two diameter endpoints";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolveOptionalPoint(INPUT_START_ID, null);
        if (start == null) {
            writeEmptyOutputs(isPortConnected(INPUT_START_ID)
                ? "Point A input is invalid"
                : "Sphere diameter requires point A");
            return;
        }

        Vector3d end = resolveOptionalPoint(INPUT_END_ID, null);
        if (end == null) {
            writeEmptyOutputs(isPortConnected(INPUT_END_ID)
                ? "Point B input is invalid"
                : "Sphere diameter requires point B");
            return;
        }

        Vector3d center = PrimitiveGeometryValidator.overflowSafeMidpoint(start, end);
        double diameter = PointUtils.safeDistance(start, end);
        double radius = diameter * 0.5d;
        if (center == null || !Double.isFinite(diameter) || !Double.isFinite(radius)
            || diameter <= PrimitiveGeometryValidator.AXIS_EPS) {
            writeEmptyOutputs("Diameter endpoints must be distinct");
            return;
        }

        String error = PrimitiveGeometryValidator.validateSphere(center, radius);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        SphereData sphere = new SphereData(center, radius);
        outputValues.put(OUTPUT_SPHERE_ID, sphere);
        outputValues.put(OUTPUT_GEOMETRY_ID, sphere);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_DIAMETER_ID, diameter);
        outputValues.put(OUTPUT_DIAMETER_PATH_ID, pathFromLine(start, end));
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_SPHERE_ID, OUTPUT_GEOMETRY_ID, OUTPUT_CENTER_ID, OUTPUT_DIAMETER_PATH_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_RADIUS_ID, OUTPUT_DIAMETER_ID);
        markInvalid(reason);
    }
}
