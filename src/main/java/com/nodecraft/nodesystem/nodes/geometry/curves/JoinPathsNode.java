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
    id = "geometry.curves.join_paths",
    displayName = "Join Paths",
    description = "Joins two paths end-to-end into one continuous path.",
    category = "geometry.curves",
    order = 3
)
public class JoinPathsNode extends AbstractCurveNode {

    private static final String INPUT_PATH_A_ID = "input_path_a";
    private static final String INPUT_PATH_B_ID = "input_path_b";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public JoinPathsNode() {
        super(UUID.randomUUID(), "geometry.curves.join_paths");

        addInputPort(new BasePort(INPUT_PATH_A_ID, "Path A",
            "First path segment (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PATH_B_ID, "Path B",
            "Second path segment appended after Path A", NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Joined path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of vertices in the joined path", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when join succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pathA = resolvePathVertices(INPUT_PATH_A_ID);
        List<Vector3d> pathB = resolvePathVertices(INPUT_PATH_B_ID);
        List<Vector3d> joined = PathUtils.joinPaths(pathA, pathB);
        PathData path = PathUtils.toPathData(joined);
        if (path == null) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_COUNT_ID, joined.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
