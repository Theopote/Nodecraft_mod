package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.reverse_path",
    displayName = "Reverse Path",
    description = "Reverses the direction of a path.",
    category = "geometry.curves",
    order = 4
)
public class ReversePathNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ReversePathNode() {
        super(UUID.randomUUID(), "geometry.curves.reverse_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to reverse (line, polyline, or curve)", NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Reversed path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when reversal succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        List<Vector3d> reversed = PathUtils.reversePath(verts);
        PathData path = PathUtils.toPathData(reversed);
        if (path == null) {
            outputValues.put(OUTPUT_PATH_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
