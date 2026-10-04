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
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_gradient_point",
    displayName = "SDF Gradient At Point",
    description = "Samples SDF gradient at a point and outputs a normalized normal-like direction",
    category = "geometry.sdf",
    order = 19
)
public class SdfGradientPointNode extends AbstractSdfNode {

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
        Vector3d hx = new Vector3d(h, 0.0d, 0.0d);
        Vector3d hy = new Vector3d(0.0d, h, 0.0d);
        Vector3d hz = new Vector3d(0.0d, 0.0d, h);
        Vector3d plusX = VectorUtils.safeAdd(p, hx);
        Vector3d minusX = VectorUtils.safeSubtract(p, hx);
        Vector3d plusY = VectorUtils.safeAdd(p, hy);
        Vector3d minusY = VectorUtils.safeSubtract(p, hy);
        Vector3d plusZ = VectorUtils.safeAdd(p, hz);
        Vector3d minusZ = VectorUtils.safeSubtract(p, hz);
        if (plusX == null || minusX == null || plusY == null || minusY == null || plusZ == null || minusZ == null) {
            writeFailure("Gradient probe point overflow");
            return;
        }

        double d = sdf.sampleDistance(p);
        double gx = sdf.sampleDistance(plusX) - sdf.sampleDistance(minusX);
        double gy = sdf.sampleDistance(plusY) - sdf.sampleDistance(minusY);
        double gz = sdf.sampleDistance(plusZ) - sdf.sampleDistance(minusZ);

        if (!Double.isFinite(d) || !Double.isFinite(gx) || !Double.isFinite(gy) || !Double.isFinite(gz)) {
            writeFailure("Sampled distance or gradient components are not finite");
            return;
        }

        Vector3d normal = VectorUtils.safeNormalize(new Vector3d(gx, gy, gz));
        if (normal == null) {
            writeFailure("Gradient is degenerate (near-zero length)");
            return;
        }

        outputValues.put(OUTPUT_GRADIENT_ID, VectorUtils.toVectorPort(normal));
        outputValues.put(OUTPUT_DISTANCE_ID, d);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_GRADIENT_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_DISTANCE_ID);
        markInvalid(error);
    }
}
