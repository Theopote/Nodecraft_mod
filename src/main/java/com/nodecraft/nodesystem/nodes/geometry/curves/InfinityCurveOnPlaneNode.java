package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
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
    id = "geometry.curves.infinity_curve",
    displayName = "Infinity Curve On Plane",
    description = "Builds a sampled figure-eight (lemniscate-like) curve on a plane",
    category = "geometry.curves",
    order = 11
)
public class InfinityCurveOnPlaneNode extends AbstractCurveNode {

    private static final int DEFAULT_SEGMENTS = 64;

    @NodeProperty(displayName = "Default Segments", category = "Infinity", order = 1)
    private int defaultSegments = DEFAULT_SEGMENTS;

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_POINTS_ID = "output_points";

    public InfinityCurveOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.infinity_curve");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Curve center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Overall curve scale", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Sample segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane x axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Primary infinity path output", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled infinity points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when infinity curve was constructed", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveInputPoint(inputValues.get(INPUT_CENTER_ID));
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object preferredAxisObj = inputValues.get(INPUT_AXIS_ID);

        if (center == null) {
            invalidate("Center is missing or invalid");
            return;
        }

        Double size = resolvePositiveDouble(INPUT_SIZE_ID, Double.NaN);
        Integer segments = resolveBoundedInteger(INPUT_SEGMENTS_ID, defaultSegments, 8, GenerationLimits.MAX_CURVE_SAMPLES);

        if (size == null) {
            invalidate("Size must be a finite value greater than 0");
            return;
        }
        if (segments == null) {
            invalidate("Segments must be an integer from 8 to " + GenerationLimits.MAX_CURVE_SAMPLES);
            return;
        }

        var basis = resolvePlaneBasis(planeObj, preferredAxisObj, PlaneData.XY_PLANE);
        if (basis == null) {
            invalidate("Plane basis could not be constructed");
            return;
        }

        List<Vec3d> pts = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0d) * i / segments;
            double x = Math.sin(t);
            double y = Math.sin(t) * Math.cos(t);
            Vector3d world = new Vector3d(center)
                .add(new Vector3d(basis.xAxis()).mul(x * size))
                .add(new Vector3d(basis.yAxis()).mul(y * size));
            pts.add(new Vec3d(world.x, world.y, world.z));
        }

        PolylineData polyline = new PolylineData(pts);
        List<Vector3d> pointVectors = new ArrayList<>(pts.size());
        for (Vec3d point : pts) {
            pointVectors.add(new Vector3d(point.x, point.y, point.z));
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(polyline));
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(pointVectors));
        markSuccess();
    }

    public int getDefaultSegments() {
        return defaultSegments;
    }

    public void setDefaultSegments(int defaultSegments) {
        if (defaultSegments >= 8
                && defaultSegments <= GenerationLimits.MAX_CURVE_SAMPLES
                && this.defaultSegments != defaultSegments) {
            this.defaultSegments = defaultSegments;
            markDirty();
        }
    }

    private void invalidate(String message) {
        putNullOutputs(OUTPUT_PATH_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        markInvalid(message);
    }
}
