
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.bezier",
    displayName = "Bezier",
    description = "Builds a sampled Bezier curve from an ordered list of control points",
    category = "geometry.curves",
    order = 3
)
public class BezierNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Resolution", category = "Bezier", order = 1)
    private int defaultResolution = 32;

    private static final String INPUT_CONTROL_POINTS_ID = "input_control_points";
    private static final String INPUT_RESOLUTION_ID = "input_resolution";

    private static final String OUTPUT_CURVE_ID = "output_curve";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CONTROL_POLYGON_ID = "output_control_polygon";
    private static final String OUTPUT_CONTROL_COUNT_ID = "output_control_count";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BezierNode() {
        super(UUID.randomUUID(), "geometry.curves.bezier");

        addInputPort(new BasePort(INPUT_CONTROL_POINTS_ID, "Control Points", "Ordered control points for the Bezier curve", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_RESOLUTION_ID, "Resolution", "Number of sampled points along the curve", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CURVE_ID, "Curve", "Bezier curve representation", NodeDataType.CURVE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Sampled polyline approximation", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled points along the Bezier curve", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_POLYGON_ID, "Control Polygon", "Polyline through the control points", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_CONTROL_COUNT_ID, "Control Count", "Number of valid control points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Sampled length of the polyline approximation", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when at least 3 control points were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object controlPointsObj = inputValues.get(INPUT_CONTROL_POINTS_ID);
        if (!(controlPointsObj instanceof Collection<?> collection)) {
            writeEmptyOutputs();
            return;
        }

        List<Vec3d> controlPoints = new ArrayList<>();
        for (Object entry : collection) {
            Vec3d point = PlaneProjectionUtils.resolveVec3dPoint(entry);
            if (point != null) {
                controlPoints.add(point);
            }
        }

        int resolution = Math.max(2, getInputInt(INPUT_RESOLUTION_ID, defaultResolution));
        if (controlPoints.size() < 3) {
            writeEmptyOutputs();
            outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
            return;
        }

        Curve curve = new Curve(Curve.CurveType.BEZIER, resolution);
        for (Vec3d controlPoint : controlPoints) {
            curve.addControlPoint(controlPoint);
        }

        List<Vec3d> sampled = curve.getSamplePoints();
        PolylineData polyline = new PolylineData(sampled);
        PolylineData controlPolygon = new PolylineData(controlPoints);
        List<PointData> points = new ArrayList<>(sampled.size());
        for (Vec3d sample : sampled) {
            points.add(new PointData(sample.x, sample.y, sample.z));
        }

        outputValues.put(OUTPUT_CURVE_ID, curve);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(points));
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, controlPolygon);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, controlPoints.size());
        outputValues.put(OUTPUT_LENGTH_ID, polyline.getLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    public int getDefaultResolution() {
        return defaultResolution;
    }

    public void setDefaultResolution(int defaultResolution) {
        int resolved = Math.max(2, defaultResolution);
        if (this.defaultResolution != resolved) {
            this.defaultResolution = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return new java.util.HashMap<String, Object>() {{
            put("defaultResolution", defaultResolution);
        }};
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof java.util.Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultResolution") instanceof Number value) {
            setDefaultResolution(value.intValue());
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CURVE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CONTROL_POLYGON_ID, null);
        outputValues.put(OUTPUT_CONTROL_COUNT_ID, 0);
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
