package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.deconstruct_edge",
    displayName = "Deconstruct Face Edge",
    description = "Extracts endpoints, midpoint, direction, displacement, and length from a face edge",
    category = "reference.points",
    order = 14
)
public class DeconstructFaceEdgeNode extends BaseNode {

    private static final String INPUT_EDGE_ID = "input_edge";

    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";
    private static final String OUTPUT_MIDPOINT_ID = "output_midpoint";
    private static final String OUTPUT_DIRECTION_ID = "output_direction";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructFaceEdgeNode() {
        super(UUID.randomUUID(), "reference.points.deconstruct_edge");

        addInputPort(new BasePort(INPUT_EDGE_ID, "Edge", "The face edge to deconstruct", NodeDataType.LINE, this));

        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Edge start point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Edge end point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_MIDPOINT_ID, "Midpoint", "Edge midpoint", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_DIRECTION_ID, "Direction", "Normalized edge direction", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "Full edge displacement vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Edge length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the edge input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts endpoints, midpoint, direction, displacement, and length from a face edge";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object edgeObj = inputValues.get(INPUT_EDGE_ID);

        if (!(edgeObj instanceof LineData edge)) {
            writeInvalid();
            return;
        }

        Vec3d start = edge.getStart();
        Vec3d end = edge.getEnd();
        Vec3d direction = edge.getDirection();
        Vec3d vector = edge.getVector();
        Vec3d midpoint = start.add(end).multiply(0.5d);

        outputValues.put(OUTPUT_START_ID, new PointData(start.x, start.y, start.z));
        outputValues.put(OUTPUT_END_ID, new PointData(end.x, end.y, end.z));
        outputValues.put(OUTPUT_MIDPOINT_ID, new PointData(midpoint.x, midpoint.y, midpoint.z));
        outputValues.put(OUTPUT_DIRECTION_ID, new Vector3d(direction.x, direction.y, direction.z));
        outputValues.put(OUTPUT_VECTOR_ID, new Vector3d(vector.x, vector.y, vector.z));
        outputValues.put(OUTPUT_LENGTH_ID, edge.getLength());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_START_ID, null);
        outputValues.put(OUTPUT_END_ID, null);
        outputValues.put(OUTPUT_MIDPOINT_ID, null);
        outputValues.put(OUTPUT_DIRECTION_ID, null);
        outputValues.put(OUTPUT_VECTOR_ID, null);
        outputValues.put(OUTPUT_LENGTH_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
