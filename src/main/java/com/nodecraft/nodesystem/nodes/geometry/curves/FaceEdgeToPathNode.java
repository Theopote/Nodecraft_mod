package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.edge_to_curve",
    displayName = "Face Edge To Path",
    description = "Converts a face edge into line, polyline, and point outputs for path workflows",
    category = "geometry.curves",
    order = 2
)
public class FaceEdgeToPathNode extends AbstractCurveNode {

    private static final String INPUT_EDGE_ID = "input_edge";
    private static final String INPUT_START_CORNER_INDEX_ID = "input_start_corner_index";
    private static final String INPUT_END_CORNER_INDEX_ID = "input_end_corner_index";

    private static final String OUTPUT_LINE_ID = "output_line";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_START_CORNER_INDEX_ID = "output_start_corner_index";
    private static final String OUTPUT_END_CORNER_INDEX_ID = "output_end_corner_index";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FaceEdgeToPathNode() {
        super(UUID.randomUUID(), "geometry.curves.edge_to_curve");

        addInputPort(new BasePort(INPUT_EDGE_ID, "Edge", "Face edge to convert", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_START_CORNER_INDEX_ID, "Start Corner Index", "Optional start corner index from the parent box", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_END_CORNER_INDEX_ID, "End Corner Index", "Optional end corner index from the parent box", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_LINE_ID, "Line", "Edge as a line segment", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Edge as a 2-point polyline", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Ordered edge endpoints as point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Start point of the edge", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "End point of the edge", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_START_CORNER_INDEX_ID, "Start Corner Index", "Start corner index passed through from the edge source", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_END_CORNER_INDEX_ID, "End Corner Index", "End corner index passed through from the edge source", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid edge was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object edgeObj = inputValues.get(INPUT_EDGE_ID);
        Object startCornerIndexObj = inputValues.get(INPUT_START_CORNER_INDEX_ID);
        Object endCornerIndexObj = inputValues.get(INPUT_END_CORNER_INDEX_ID);

        if (!(edgeObj instanceof LineData edge)) {
            outputValues.put(OUTPUT_LINE_ID, null);
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_START_ID, null);
            outputValues.put(OUTPUT_END_ID, null);
            outputValues.put(OUTPUT_START_CORNER_INDEX_ID, null);
            outputValues.put(OUTPUT_END_CORNER_INDEX_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vec3d start = edge.getStart();
        Vec3d end = edge.getEnd();
        Vector3d startPoint = new Vector3d(start.x, start.y, start.z);
        Vector3d endPoint = new Vector3d(end.x, end.y, end.z);
        PolylineData polyline = new PolylineData(List.of(start, end));

        outputValues.put(OUTPUT_LINE_ID, edge);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.of(startPoint, endPoint));
        outputValues.put(OUTPUT_START_ID, startPoint);
        outputValues.put(OUTPUT_END_ID, endPoint);
        outputValues.put(OUTPUT_START_CORNER_INDEX_ID, startCornerIndexObj instanceof Number number ? number.intValue() : null);
        outputValues.put(OUTPUT_END_CORNER_INDEX_ID, endCornerIndexObj instanceof Number number ? number.intValue() : null);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
