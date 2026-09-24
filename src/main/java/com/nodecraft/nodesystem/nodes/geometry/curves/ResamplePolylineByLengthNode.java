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
import com.nodecraft.nodesystem.util.PathSamplingUtils;
import com.nodecraft.nodesystem.util.SamplingMode;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.resample_path",
    displayName = "Resample Path",
    description = "Resamples a path along arc length by Count or Spacing. Primary output is PATH.",
    category = "geometry.curves",
    order = 12
)
public class ResamplePolylineByLengthNode extends AbstractCurveNode {

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
    private static final String OUTPUT_VALID_ID = "output_valid";

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
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        SamplingMode mode = resolveResampleMode(inputValues.get(INPUT_MODE_ID));
        if (mode == null) {
            writeInvalid();
            return;
        }

        int count = inputValues.get(INPUT_COUNT_ID) instanceof Number n ? n.intValue() : defaultCount;
        double spacing = inputValues.get(INPUT_SPACING_ID) instanceof Number n ? n.doubleValue() : defaultSpacing;

        PathSamplingUtils.PathSampleResult sample = PathSamplingUtils.sample(verts, mode, count, spacing);
        if (!sample.valid() || sample.points().isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> samples = sample.points();
        List<Vec3d> polyPts = PathUtils.toVec3dList(samples, sample.closed());
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPts);
        if (polyline == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(samples));
        outputValues.put(OUTPUT_COUNT_ID, samples.size());
        outputValues.put(OUTPUT_LENGTH_ID, sample.totalLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private @Nullable SamplingMode resolveResampleMode(@Nullable Object rawMode) {
        if (rawMode == null) {
            return isResampleModeAllowed(samplingMode) ? samplingMode : null;
        }
        if (rawMode instanceof SamplingMode mode) {
            return isResampleModeAllowed(mode) ? mode : null;
        }
        if (rawMode instanceof String text) {
            try {
                SamplingMode mode = SamplingMode.valueOf(text.trim().toUpperCase(Locale.ROOT));
                return isResampleModeAllowed(mode) ? mode : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isResampleModeAllowed(SamplingMode mode) {
        return mode == SamplingMode.COUNT || mode == SamplingMode.SPACING;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
