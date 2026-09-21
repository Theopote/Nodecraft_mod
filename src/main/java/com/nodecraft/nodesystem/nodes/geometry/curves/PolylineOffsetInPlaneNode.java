package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.InPlanePathOffset;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Legacy polyline-only offset. Prefer {@link OffsetCurveInPlaneNode} (Offset Path In Plane) for new graphs.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.offset_polyline_plane",
    displayName = "Offset Polyline In Plane",
    description = "Legacy polyline offset in a plane. Prefer Offset Path In Plane for line/polyline/curve paths.",
    category = "geometry.curves",
    order = 99
)
public class PolylineOffsetInPlaneNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 1,
        description = "Maximum miter extension factor relative to |offset| before falling back to a bevel corner")
    private double miterLimit = 4.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineOffsetInPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.offset_polyline_plane");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to offset in the plane (line, polyline, or curve)",
            NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Work plane containing the polyline",
            NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset",
            "Signed offset distance in the plane (positive = left of forward segments in UV)",
            NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline",
            "Offset polyline in 3D (still lying in the work plane)",
            NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when inputs resolved to a valid offset polyline",
            NodeDataType.BOOLEAN, this));
    }

    public double getMiterLimit() {
        return miterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        double resolved = Math.max(0.0d, miterLimit);
        if (Double.compare(this.miterLimit, resolved) != 0) {
            this.miterLimit = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("miterLimit", miterLimit);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("miterLimit") instanceof Number value) {
            setMiterLimit(value.doubleValue());
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        Object offsetObj = inputValues.get(INPUT_OFFSET_ID);
        if (!(planeObj instanceof PlaneData plane) || !(offsetObj instanceof Number number)) {
            writeInvalid();
            return;
        }
        double offset = number.doubleValue();
        if (Math.abs(offset) < EPS) {
            writeInvalid();
            return;
        }

        List<Vector3d> worldVerts = resolvePathVertices(INPUT_PATH_ID);
        InPlanePathOffset.Result result = InPlanePathOffset.offset(worldVerts, plane, offset, miterLimit);
        if (result == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_POLYLINE_ID, result.polyline());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
