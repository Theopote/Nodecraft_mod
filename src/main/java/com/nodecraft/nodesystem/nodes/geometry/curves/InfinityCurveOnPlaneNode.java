package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.infinity_curve",
    displayName = "Infinity Curve On Plane",
    description = "Builds a sampled figure-eight (lemniscate-like) curve on a plane",
    category = "geometry.curves",
    order = 17
)
public class InfinityCurveOnPlaneNode extends AbstractCurveNode {
    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String INPUT_SEGMENTS_ID = "input_segments";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public InfinityCurveOnPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.infinity_curve");
        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Curve center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Overall curve scale", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEGMENTS_ID, "Segments", "Sample segment count", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target construction plane. Defaults to XY plane", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "X Axis", "Optional in-plane x axis", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Sampled infinity curve", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled infinity polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled infinity points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when infinity curve was constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = PlaneProjectionUtils.resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);
        Object segmentsObj = inputValues.get(INPUT_SEGMENTS_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object preferredAxisObj = inputValues.get(INPUT_AXIS_ID);

        if (center == null || !(sizeObj instanceof Number sN) || !(segmentsObj instanceof Number segN)) {
            writeInvalid();
            return;
        }
        double size = sN.doubleValue();
        int segments = Math.max(8, segN.intValue());
        if (!Double.isFinite(size) || size <= 0.0d) {
            writeInvalid();
            return;
        }

        PlaneProjectionUtils.Basis basis = resolvePlaneBasis(planeObj, preferredAxisObj, PlaneData.XY_PLANE);
        if (basis == null) {
            writeInvalid();
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

        Curve curve = buildLinearCurve(pts);
        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, new PolylineData(pts));
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(pts));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        putNullOutputs(OUTPUT_CURVE_ID, OUTPUT_POLYLINE_ID);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putBooleanOutputs(false, OUTPUT_VALID_ID);
    }

}
