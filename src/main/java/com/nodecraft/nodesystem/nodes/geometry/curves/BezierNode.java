package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.CurveSampleFence;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.bezier",
    displayName = "Bezier",
    description = "Builds a sampled Bezier curve from an ordered list of control points",
    category = "geometry.curves",
    order = 5
)
public class BezierNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Samples", category = "Bezier", order = 1)
    private int defaultSamples = 32;

    private static final String INPUT_CONTROL_POINTS_ID = "input_control_points";
    private static final String INPUT_SAMPLES_ID = "input_samples";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_PATH_ID = "output_control_path";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public BezierNode() {
        super(UUID.randomUUID(), "geometry.curves.bezier");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered control points for the Bezier curve", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_SAMPLES_ID, "Samples", "Number of sample points along the curve", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary Bezier path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the Bezier curve", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_PATH_ID, "Control Path", "Path through the control points", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled length of the path approximation", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least 3 control points were resolved", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> resolved = CurveInputUtils.resolveStrictPointListBounded(
            inputValues.get(INPUT_CONTROL_POINTS_ID),
            GenerationLimits.MAX_CURVE_CONTROL_POINTS
        );
        if (resolved == null) {
            invalidate("Control points are missing, mixed, non-finite, empty, or exceed the control-point budget", 0);
            return;
        }
        List<Vec3d> controlPoints = toVec3d(resolved);

        Integer samples = resolveBoundedInteger(INPUT_SAMPLES_ID, defaultSamples, 2, GenerationLimits.MAX_CURVE_SAMPLES);
        if (samples == null) {
            invalidate("Samples must be an integer from 2 to " + GenerationLimits.MAX_CURVE_SAMPLES, controlPoints.size());
            return;
        }

        if (controlPoints.size() < 3) {
            invalidate("At least 3 control points are required", controlPoints.size());
            return;
        }
        if (!CurveInputUtils.isWithinEvaluationWork(controlPoints.size(), samples)) {
            invalidate("Bezier evaluation work exceeds limit (" + GenerationLimits.MAX_CURVE_EVALUATION_WORK + ")",
                controlPoints.size());
            return;
        }

        Curve curve = new Curve(Curve.CurveType.BEZIER, samples);
        for (Vec3d controlPoint : controlPoints) {
            curve.addControlPoint(controlPoint);
        }

        CurveSampleFence.Result fenced = CurveSampleFence.validate(curve.getSamplePoints());
        if (fenced == null) {
            invalidate("Bezier samples are non-finite or over budget", controlPoints.size());
            return;
        }
        PathData controlPath = PathUtils.toPathData(resolved);
        if (controlPath == null) {
            invalidate("Control path could not be constructed", controlPoints.size());
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, fenced.path());
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(fenced.vectors()));
        outputValues.put(OUTPUT_CONTROL_PATH_ID, controlPath);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
        outputValues.put(OUTPUT_LENGTH_ID, fenced.length());
        markSuccess();
    }

    public int getDefaultSamples() {
        return defaultSamples;
    }

    public void setDefaultSamples(int defaultSamples) {
        if (defaultSamples >= 2
                && defaultSamples <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultSamples != defaultSamples) {
            this.defaultSamples = defaultSamples;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultSamples", defaultSamples);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultSamples") instanceof Number value) {
            setDefaultSamples(value.intValue());
        } else if (map.get("defaultResolution") instanceof Number value) {
            setDefaultSamples(value.intValue());
        }
    }

    private void invalidate(String message, int controlCount) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_CONTROL_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(controlCount, OUTPUT_CONTROL_COUNT_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_LENGTH_ID);
        markInvalid(message);
    }

    private static List<Vec3d> toVec3d(List<Vector3d> points) {
        List<Vec3d> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        return out;
    }
}
