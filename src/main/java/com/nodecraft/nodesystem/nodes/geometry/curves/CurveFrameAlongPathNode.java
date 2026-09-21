package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.PathFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Samples a path and generates local frames (origin + axes) along it.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.frame_along_path",
    displayName = "Curve Frame Along Path",
    description = "Generates local frames along a curve/path using count or spacing, outputting origins, axes, and planes per sample",
    category = "geometry.curves",
    order = 15
)
public class CurveFrameAlongPathNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Default Spacing", category = "Frame", order = 1,
        description = "Target distance between frames when Spacing port is unconnected (Minecraft-friendly 1 block)")
    private double defaultSpacing = 1.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_SPACING_ID = "input_spacing";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_UP_VECTOR_ID = "input_up_vector";

    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_X_AXES_ID = "output_x_axes";
    private static final String OUTPUT_Y_AXES_ID = "output_y_axes";
    private static final String OUTPUT_Z_AXES_ID = "output_z_axes";
    private static final String OUTPUT_PLANES_ID = "output_planes";
    private static final String OUTPUT_TANGENTS_ID = "output_tangents";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CurveFrameAlongPathNode() {
        super(UUID.randomUUID(), "geometry.curves.frame_along_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to sample for frames (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_SPACING_ID, "Spacing",
            "Target distance between samples (> 0 when used)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count",
            "Target sample count (>= 2). Overrides spacing when provided", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_UP_VECTOR_ID, "Up Vector",
            "Reference up vector used to stabilize frame orientation", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins",
            "Frame origins as point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_X_AXES_ID, "X Axes",
            "Frame X axes (tangent) as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXES_ID, "Y Axes",
            "Frame Y axes (normal) as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXES_ID, "Z Axes",
            "Frame Z axes (binormal) as Vector3d list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANES_ID, "Planes",
            "PlaneData list built from each frame origin + Z axis", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_TANGENTS_ID, "Tangents",
            "Alias of X axes for path-direction workflows", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Frame origins as point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of generated frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length used for sampling", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when frames were generated successfully", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolveVertices();
        if (verts == null || verts.size() < 2) {
            writeInvalid();
            return;
        }

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        if (unique.size() < 2) {
            writeInvalid();
            return;
        }

        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null) {
            writeInvalid();
            return;
        }
        double total = cumulative[cumulative.length - 1];
        if (total <= EPS) {
            writeInvalid();
            return;
        }

        Object countObj = inputValues.get(INPUT_COUNT_ID);
        int count = countObj instanceof Number n ? n.intValue() : -1;
        if (count >= 2) {
            count = GenerationLimits.clampPositiveCount(count);
        }
        double spacing = readDoubleInput(INPUT_SPACING_ID, defaultSpacing);

        List<Double> sampleDistances = new ArrayList<>();
        if (count >= 2) {
            for (int i = 0; i < count; i++) {
                sampleDistances.add(total * i / (double) (count - 1));
            }
        } else if (spacing > EPS) {
            int maxInstances = GenerationLimits.clampSpacingInstanceCount(total, spacing);
            int emitted = 0;
            for (double d = 0.0d; d <= total + EPS && emitted < maxInstances; d += spacing) {
                sampleDistances.add(Math.min(d, total));
                emitted++;
            }
            if (emitted < maxInstances
                && (sampleDistances.isEmpty() || sampleDistances.get(sampleDistances.size() - 1) < total - EPS)) {
                sampleDistances.add(total);
            }
        } else {
            writeInvalid();
            return;
        }

        Vector3d up = resolveUpVector(inputValues.get(INPUT_UP_VECTOR_ID));
        List<Vector3d> sampleOrigins = new ArrayList<>(sampleDistances.size());
        List<Vector3d> sampleTangents = new ArrayList<>(sampleDistances.size());
        double delta = Math.max(total * 1.0e-4d, 1.0e-4d);
        for (double d : sampleDistances) {
            Vector3d origin = PathUtils.sampleAtDistance(unique, closed, cumulative, d);
            double backDistance = closed ? wrapDistance(d - delta, total) : Math.max(0.0d, d - delta);
            double forwardDistance = closed ? wrapDistance(d + delta, total) : Math.min(total, d + delta);

            Vector3d prev = PathUtils.sampleAtDistance(unique, closed, cumulative, backDistance);
            Vector3d next = PathUtils.sampleAtDistance(unique, closed, cumulative, forwardDistance);
            Vector3d tangent = new Vector3d(next).sub(prev);
            if (tangent.lengthSquared() <= EPS) {
                continue;
            }
            tangent.normalize();
            sampleOrigins.add(origin);
            sampleTangents.add(tangent);
        }

        List<PathFrameUtils.Frame> pathFrames = PathFrameUtils.framesFromSamples(sampleOrigins, sampleTangents, up);
        List<Vector3d> origins = new ArrayList<>(pathFrames.size());
        List<Vector3d> xAxes = new ArrayList<>(pathFrames.size());
        List<Vector3d> yAxes = new ArrayList<>(pathFrames.size());
        List<Vector3d> zAxes = new ArrayList<>(pathFrames.size());
        List<PlaneData> planes = new ArrayList<>(pathFrames.size());
        for (PathFrameUtils.Frame frame : pathFrames) {
            // Curve Frame ports: X=tangent, Y=normal, Z=binormal
            origins.add(new Vector3d(frame.origin()));
            xAxes.add(new Vector3d(frame.zAxis()));
            yAxes.add(new Vector3d(frame.yAxis()));
            zAxes.add(new Vector3d(frame.xAxis()));
            planes.add(new PlaneData(new Vector3d(frame.origin()), new Vector3d(frame.xAxis())));
        }

        if (origins.isEmpty()) {
            writeInvalid();
            return;
        }

        List<PointData> originPoints = SpatialValueResolver.toPointDataList(origins);
        outputValues.put(OUTPUT_ORIGINS_ID, originPoints);
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_TANGENTS_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_POINTS_ID, originPoints);
        outputValues.put(OUTPUT_COUNT_ID, origins.size());
        outputValues.put(OUTPUT_LENGTH_ID, total);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_ORIGINS_ID, List.of());
        outputValues.put(OUTPUT_X_AXES_ID, List.of());
        outputValues.put(OUTPUT_Y_AXES_ID, List.of());
        outputValues.put(OUTPUT_Z_AXES_ID, List.of());
        outputValues.put(OUTPUT_PLANES_ID, List.of());
        outputValues.put(OUTPUT_TANGENTS_ID, List.of());
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolveVertices() {
        return resolvePathVertices(INPUT_PATH_ID);
    }

    private Vector3d resolveUpVector(Object value) {
        if (value instanceof Vector3d vector && vector.lengthSquared() > EPS) {
            return new Vector3d(vector).normalize();
        }
        return new Vector3d(0.0d, 1.0d, 0.0d);
    }

    private static double wrapDistance(double value, double length) {
        if (length <= EPS) {
            return 0.0d;
        }
        double wrapped = value % length;
        return wrapped < 0.0d ? wrapped + length : wrapped;
    }
}
