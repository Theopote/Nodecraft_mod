package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "reference.frames.frame_along_surface",
    displayName = "Sphere Surface Frame",
    description = "Builds a local tangent frame on a sphere using the nearest surface point and outward normal",
    category = "reference.frames"
)
public class SphereSurfaceFrameNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    private static final String INPUT_SPHERE_ID = "input_sphere";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_X_AXIS_ID = "output_x_axis";
    private static final String OUTPUT_Y_AXIS_ID = "output_y_axis";
    private static final String OUTPUT_Z_AXIS_ID = "output_z_axis";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SphereSurfaceFrameNode() {
        super(UUID.randomUUID(), "reference.frames.frame_along_surface");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to evaluate against", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Reference point near or on the sphere surface", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Origin", "Projected surface point used as frame origin", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Sphere center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Outward sphere normal at the frame origin", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_AXIS_ID, "X Axis", "First tangent axis on the sphere surface", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXIS_ID, "Y Axis", "Second tangent axis on the sphere surface", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXIS_ID, "Z Axis", "Frame Z axis aligned with the surface normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Tangent plane at the frame origin", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid frame could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds a local tangent frame on a sphere using the nearest surface point and outward normal";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));

        if (!(sphereObj instanceof SphereData sphere) || point == null) {
            writeEmptyOutputs();
            return;
        }

        Vector3d center = sphere.getCenter();
        double radius = sphere.getRadius();
        Vector3d radial = new Vector3d(point).sub(center);
        Vector3d normal = radial.lengthSquared() > EPSILON
            ? radial.normalize()
            : new Vector3d(0.0d, 1.0d, 0.0d);
        Vector3d origin = new Vector3d(normal).mul(radius).add(center);

        Vector3d referenceUp = Math.abs(normal.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);
        Vector3d xAxis = referenceUp.cross(normal, new Vector3d());
        if (xAxis.lengthSquared() <= EPSILON) {
            referenceUp.set(0.0d, 0.0d, 1.0d);
            xAxis = referenceUp.cross(normal, new Vector3d());
        }
        if (xAxis.lengthSquared() <= EPSILON) {
            writeEmptyOutputs();
            return;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= EPSILON) {
            writeEmptyOutputs();
            return;
        }
        yAxis.normalize();

        PlaneData plane = new PlaneData(origin, normal);

        outputValues.put(OUTPUT_ORIGIN_ID, origin);
        outputValues.put(OUTPUT_CENTER_ID, center);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_X_AXIS_ID, xAxis);
        outputValues.put(OUTPUT_Y_AXIS_ID, yAxis);
        outputValues.put(OUTPUT_Z_AXIS_ID, new Vector3d(normal));
        outputValues.put(OUTPUT_PLANE_ID, plane);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_X_AXIS_ID, null);
        outputValues.put(OUTPUT_Y_AXIS_ID, null);
        outputValues.put(OUTPUT_Z_AXIS_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
