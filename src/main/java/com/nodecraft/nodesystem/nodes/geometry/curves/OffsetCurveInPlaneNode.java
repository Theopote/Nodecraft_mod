package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.InPlanePathOffset;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.offset_curve_plane",
    displayName = "Offset Path In Plane",
    description = "Offsets a path (line, polyline, or curve) in a work plane by signed distance.",
    category = "geometry.curves",
    order = 19
)
public class OffsetCurveInPlaneNode extends AbstractCurveNode {

    private static final double EPS = 1.0e-9d;

    @NodeProperty(displayName = "Miter Limit", category = "Offset", order = 1,
        description = "Maximum miter extension factor relative to |offset| before bevel fallback")
    private double miterLimit = 4.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_PATH_ID = "output_path";

    public OffsetCurveInPlaneNode() {
        super(UUID.randomUUID(), "geometry.curves.offset_curve_plane");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to offset (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Work plane containing the curve", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Signed offset distance in the plane", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Offset path in the work plane", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the offset succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        PlaneData plane = OptionalPortDrive.resolveOptionalPlane(this, INPUT_PLANE_ID, null);
        if (plane == null) {
            invalidate("Plane is missing or invalid");
            return;
        }

        Double offset = resolveFiniteDouble(INPUT_OFFSET_ID, 0.0d);
        if (offset == null) {
            invalidate("Offset is connected but invalid");
            return;
        }

        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            invalidate("Path is missing or invalid");
            return;
        }

        if (Math.abs(offset) < EPS) {
            PathData path = PathUtils.toPathData(verts);
            if (path == null) {
                invalidate("Path is missing or invalid");
                return;
            }
            outputValues.put(OUTPUT_PATH_ID, path);
            markSuccess();
            return;
        }

        InPlanePathOffset.Result result = InPlanePathOffset.offset(verts, plane, offset, miterLimit);
        if (result == null || result.polyline() == null) {
            invalidate("Path offset failed in the work plane");
            return;
        }

        outputValues.put(OUTPUT_PATH_ID, PathData.fromPolyline(result.polyline()));
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PATH_ID);
        markInvalid(error);
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
        return java.util.Map.of("miterLimit", miterLimit);
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map && map.get("miterLimit") instanceof Number number) {
            setMiterLimit(number.doubleValue());
        }
    }
}
