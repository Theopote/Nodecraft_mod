package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.edge_to_curve",
    displayName = "Face Edge To Path",
    description = "Converts a face edge into a path and endpoint outputs for path workflows",
    category = "geometry.curves",
    order = 2
)
public class FaceEdgeToPathNode extends AbstractCurveNode {

    private static final String INPUT_EDGE_ID = "input_edge";
    private static final String INPUT_START_CORNER_INDEX_ID = "input_start_corner_index";
    private static final String INPUT_END_CORNER_INDEX_ID = "input_end_corner_index";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_START_POINT_ID = "output_start_point";
    private static final String OUTPUT_END_POINT_ID = "output_end_point";
    private static final String OUTPUT_START_CORNER_INDEX_ID = "output_start_corner_index";
    private static final String OUTPUT_END_CORNER_INDEX_ID = "output_end_corner_index";

    public FaceEdgeToPathNode() {
        super(UUID.randomUUID(), "geometry.curves.edge_to_curve");

        addInputPort(new BasePort(INPUT_EDGE_ID, "Edge", "Face edge to convert", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_START_CORNER_INDEX_ID, "Start Corner Index", "Optional start corner index from the parent box", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_END_CORNER_INDEX_ID, "End Corner Index", "Optional end corner index from the parent box", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Edge as a path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_START_POINT_ID, "Start Point", "Start point of the edge", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_POINT_ID, "End Point", "End point of the edge", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_START_CORNER_INDEX_ID, "Start Corner Index", "Start corner index passed through from the edge source", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_END_CORNER_INDEX_ID, "End Corner Index", "End corner index passed through from the edge source", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid edge was provided", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object edgeObj = inputValues.get(INPUT_EDGE_ID);
        if (!(edgeObj instanceof LineData edge)) {
            invalidate("Edge is missing or invalid");
            return;
        }

        Integer startIndex = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_START_CORNER_INDEX_ID));
        Integer endIndex = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_END_CORNER_INDEX_ID));

        var start = edge.start();
        var end = edge.end();
        Vector3d startPoint = new Vector3d(start.x, start.y, start.z);
        Vector3d endPoint = new Vector3d(end.x, end.y, end.z);

        outputValues.put(OUTPUT_PATH_ID, PathData.fromLine(edge));
        outputValues.put(OUTPUT_START_POINT_ID, new PointData(startPoint.x, startPoint.y, startPoint.z));
        outputValues.put(OUTPUT_END_POINT_ID, new PointData(endPoint.x, endPoint.y, endPoint.z));
        outputValues.put(OUTPUT_START_CORNER_INDEX_ID, startIndex);
        outputValues.put(OUTPUT_END_CORNER_INDEX_ID, endIndex);
        markSuccess();
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_PATH_ID, OUTPUT_START_POINT_ID, OUTPUT_END_POINT_ID,
            OUTPUT_START_CORNER_INDEX_ID, OUTPUT_END_CORNER_INDEX_ID);
        markInvalid(error);
    }
}
