package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.rotate_geometry_axis",
    displayName = "Rotate Geometry Around Axis",
    description = "Rotates analytic geometry around a center point and arbitrary axis",
    category = "transform.basic_transforms",
    order = 14
)
public class RotateGeometryAroundAxisNode extends BaseNode {

    @NodeProperty(displayName = "Default Angle", category = "Rotation", order = 1)
    private double defaultAngle = 90.0d;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_ANGLE_ID = "input_angle";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RotateGeometryAroundAxisNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.rotate_geometry_axis");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to rotate", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Rotation center point", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Rotation axis vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_ANGLE_ID, "Angle", "Rotation angle in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Rotated geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when geometry was rotated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rotates analytic geometry around a center point and arbitrary axis";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, false);
            return;
        }

        Vector3d center = inputValues.get(INPUT_CENTER_ID) instanceof Vector3d value ? new Vector3d(value) : new Vector3d();
        Vector3d axis = inputValues.get(INPUT_AXIS_ID) instanceof Vector3d value ? new Vector3d(value) : new Vector3d(0.0d, 1.0d, 0.0d);
        double angle = getInputDouble(INPUT_ANGLE_ID, defaultAngle);
        if (!isFinite(center) || !isFinite(axis) || axis.lengthSquared() <= 1.0e-12d || !Double.isFinite(angle)) {
            writeResult(null, false);
            return;
        }

        axis.normalize();
        Quaterniond quaternion = new Quaterniond(new AxisAngle4d(Math.toRadians(angle), axis.x, axis.y, axis.z));
        Matrix3d rotation = new Matrix3d().set(quaternion);
        GeometryData rotated = GeometryTransform.transformAround(geometry, center, rotation, 1.0d);
        writeResult(rotated, rotated != null);
    }

    public double getDefaultAngle() {
        return defaultAngle;
    }

    public void setDefaultAngle(double defaultAngle) {
        if (Double.compare(this.defaultAngle, defaultAngle) != 0) {
            this.defaultAngle = defaultAngle;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of("defaultAngle", defaultAngle);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("defaultAngle") instanceof Number value) {
            setDefaultAngle(value.doubleValue());
        }
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
