package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PathSamplingUtils;
import com.nodecraft.nodesystem.util.SamplingMode;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.resample_path",
    displayName = "Resample Path",
    description = "Resamples a path along arc length by Count or Spacing. Primary output is PATH.",
    category = "geometry.curves",
    order = 20
)
public class ResamplePolylineByLengthNode extends AbstractCurveNode {

    private static final Set<String> RESAMPLE_MODES = Set.of("count", "spacing");

    @NodeProperty(displayName = "Sampling Mode", category = "Sampling", order = 1)
    private SamplingMode samplingMode = SamplingMode.COUNT;

    @NodeProperty(displayName = "Default Count", category = "Sampling", order = 2)
    private int defaultCount = 10;

    @NodeProperty(displayName = "Default Spacing", category = "Sampling", order = 3)
    private double defaultSpacing = 1.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_MODE_ID = "input_mode";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SPACING_ID = "input_spacing";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public ResamplePolylineByLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.resample_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to resample (line, polyline, or curve)",
            NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode",
            "Sampling mode: Count or Spacing", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Target distance between samples when Mode=Spacing",
            NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target sample count when Mode=Count (>= 2)",
            NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Resampled path (closed when the input path is closed)",
            NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Resampled points as point list",
            NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of resampled points",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length used for sampling",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when resampling succeeded",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            invalidate("Path is missing or invalid");
            return;
        }

        String modeKey = CurveInputUtils.resolveKnownStringEnum(
            this,
            INPUT_MODE_ID,
            samplingMode.name().toLowerCase(Locale.ROOT),
            RESAMPLE_MODES
        );
        if (modeKey == null) {
            invalidate("Mode is connected but invalid");
            return;
        }
        SamplingMode mode = SamplingMode.valueOf(modeKey.toUpperCase(Locale.ROOT));

        int count = 0;
        double spacing = 0.0d;
        if (mode == SamplingMode.COUNT) {
            Integer resolvedCount = resolveBoundedInteger(
                INPUT_COUNT_ID,
                defaultCount,
                2,
                GenerationLimits.MAX_CURVE_SAMPLES
            );
            if (resolvedCount == null) {
                invalidate("Count is connected but invalid (must be >= 2 and <= "
                    + GenerationLimits.MAX_CURVE_SAMPLES + ")");
                return;
            }
            count = resolvedCount;
        } else {
            Double resolvedSpacing = resolvePositiveDouble(INPUT_SPACING_ID, defaultSpacing);
            if (resolvedSpacing == null) {
                invalidate("Spacing is connected but invalid (must be finite and > 0)");
                return;
            }
            spacing = resolvedSpacing;
        }

        PathSamplingUtils.PathSampleResult sample = PathSamplingUtils.sample(verts, mode, count, spacing);
        if (!sample.valid() || sample.points().isEmpty()) {
            invalidate("Path could not be resampled with the requested parameters");
            return;
        }

        List<Vector3d> samples = sample.points();
        List<Vec3d> polyPts = PathUtils.toVec3dList(samples, sample.closed());
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPts);
        if (polyline == null) {
            invalidate("Resampled path is degenerate");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(samples));
        outputValues.put(OUTPUT_COUNT_ID, samples.size());
        outputValues.put(OUTPUT_LENGTH_ID, sample.totalLength());
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_LENGTH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}
