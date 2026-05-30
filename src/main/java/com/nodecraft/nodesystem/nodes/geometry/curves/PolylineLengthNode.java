package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Reports total arc length of a polyline or a line segment.
 */
@NodeInfo(
    id = "geometry.curves.polyline_length",
    displayName = "Polyline Length",
    description = "Computes the total length of a polyline or line segment",
    category = "geometry.curves",
    order = 13
)
public class PolylineLengthNode extends BaseNode {

    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_LINE_ID = "input_line";

    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.polyline_length");

        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline",
            "Polyline to measure",
            NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line",
            "Optional line segment when no polyline is connected",
            NodeDataType.LINE, this));

        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a length was computed",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object polyObj = inputValues.get(INPUT_POLYLINE_ID);
        Object lineObj = inputValues.get(INPUT_LINE_ID);
        if (polyObj instanceof PolylineData poly) {
            outputValues.put(OUTPUT_LENGTH_ID, poly.getLength());
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }
        if (lineObj instanceof LineData line) {
            outputValues.put(OUTPUT_LENGTH_ID, line.getLength());
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }
        outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
