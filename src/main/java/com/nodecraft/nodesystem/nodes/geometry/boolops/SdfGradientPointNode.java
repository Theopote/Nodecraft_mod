package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_gradient_point",
    displayName = "SDF Gradient At Point",
    description = "Samples SDF gradient at a point and outputs a normalized normal-like direction",
    category = "geometry.sdf",
    order = 19
)
public class SdfGradientPointNode extends AbstractSdfNode {
    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Step", category = "SDF", order = 1)
    private double step = 0.25d;

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_GRADIENT_ID = "output_gradient";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";

    public SdfGradientPointNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_gradient_point");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Finite difference step size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GRADIENT_ID, "Gradient", "Normalized SDF gradient direction", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Signed distance at query point", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs("True when gradient sampling succeeded");
    }

    @Override
    public String getDescription() {
        return "Samples SDF gradient at a point and outputs a normalized normal-like direction";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Vector3d p = resolveOptionalPoint(INPUT_POINT_ID, null);
        Double stepResolved = resolvePositiveDouble(INPUT_STEP_ID, step);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf) || p == null) {
            writeFailure("Valid SDF and finite query point are required");
            return;
        }
        if (stepResolved == null) {
            writeFailure("Step must be finite and > 0");
            return;
        }

        double h = stepResolved;
        double d = sdf.sampleDistance(p);
        double gx = sdf.sampleDistance(new Vector3d(p.x + h, p.y, p.z))
            - sdf.sampleDistance(new Vector3d(p.x - h, p.y, p.z));
        double gy = sdf.sampleDistance(new Vector3d(p.x, p.y + h, p.z))
            - sdf.sampleDistance(new Vector3d(p.x, p.y - h, p.z));
        double gz = sdf.sampleDistance(new Vector3d(p.x, p.y, p.z + h))
            - sdf.sampleDistance(new Vector3d(p.x, p.y, p.z - h));

        if (!Double.isFinite(d) || !Double.isFinite(gx) || !Double.isFinite(gy) || !Double.isFinite(gz)) {
            writeFailure("Sampled distance or gradient components are not finite");
            return;
        }

        Vector3d gradient = new Vector3d(gx, gy, gz);
        if (gradient.lengthSquared() <= EPS) {
            writeFailure("Gradient is degenerate (near-zero length)");
            return;
        }

        gradient.normalize();
        outputValues.put(OUTPUT_GRADIENT_ID, gradient);
        outputValues.put(OUTPUT_DISTANCE_ID, d);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_GRADIENT_ID);
        putDoubleOutputs(0.0d, OUTPUT_DISTANCE_ID);
        markInvalid(error);
    }
}
