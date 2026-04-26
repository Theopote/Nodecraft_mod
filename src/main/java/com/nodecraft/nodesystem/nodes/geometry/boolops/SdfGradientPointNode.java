package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_gradient_point",
    displayName = "SDF Gradient At Point",
    description = "Samples SDF gradient at a point and outputs a normalized normal-like direction",
    category = "geometry.boolean",
    order = 28
)
public class SdfGradientPointNode extends BaseNode {
    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Step", category = "SDF", order = 1)
    private double step = 0.25d;

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_GRADIENT_ID = "output_gradient";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfGradientPointNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_gradient_point");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Finite difference step size", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GRADIENT_ID, "Gradient", "Normalized SDF gradient direction", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Signed distance at query point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when gradient sampling succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples SDF gradient at a point and outputs a normalized normal-like direction";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Vector3d p = resolvePoint(inputValues.get(INPUT_POINT_ID));
        double h = Math.max(1.0e-4d, getInputDouble(INPUT_STEP_ID, step));
        if (!(sdfObj instanceof SignedDistanceFieldData sdf) || p == null) {
            outputValues.put(OUTPUT_GRADIENT_ID, null);
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double d = sdf.sampleDistance(p);
        double gx = sdf.sampleDistance(new Vector3d(p.x + h, p.y, p.z)) - sdf.sampleDistance(new Vector3d(p.x - h, p.y, p.z));
        double gy = sdf.sampleDistance(new Vector3d(p.x, p.y + h, p.z)) - sdf.sampleDistance(new Vector3d(p.x, p.y - h, p.z));
        double gz = sdf.sampleDistance(new Vector3d(p.x, p.y, p.z + h)) - sdf.sampleDistance(new Vector3d(p.x, p.y, p.z - h));

        Vector3d gradient = new Vector3d(gx, gy, gz);
        if (gradient.lengthSquared() <= EPS) {
            outputValues.put(OUTPUT_GRADIENT_ID, null);
            outputValues.put(OUTPUT_DISTANCE_ID, d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        gradient.normalize();
        outputValues.put(OUTPUT_GRADIENT_ID, gradient);
        outputValues.put(OUTPUT_DISTANCE_ID, d);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
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
