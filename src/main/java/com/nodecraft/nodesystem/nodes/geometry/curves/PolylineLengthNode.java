package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Reports total arc length of a path.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.polyline_length",
    displayName = "Path Length",
    description = "Computes the total length of a line, polyline, or curve path",
    category = "geometry.curves",
    order = 13
)
public class PolylineLengthNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PolylineLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.polyline_length");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to measure (line, polyline, or curve)",
            NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a length was computed",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            outputValues.put(OUTPUT_LENGTH_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double length = 0.0d;
        for (int i = 0; i < verts.size() - 1; i++) {
            length += verts.get(i).distance(verts.get(i + 1));
        }
        outputValues.put(OUTPUT_LENGTH_ID, length);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
