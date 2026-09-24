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
    id = "geometry.curves.extend_path",
    displayName = "Extend Path",
    description = "Linearly extends an open path along start/end tangents by the given lengths.",
    category = "geometry.curves",
    order = 8
)
public class ExtendPathNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_START_LENGTH_ID = "input_start_length";
    private static final String INPUT_END_LENGTH_ID = "input_end_length";

    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ExtendPathNode() {
        super(UUID.randomUUID(), "geometry.curves.extend_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Open path to extend (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_START_LENGTH_ID, "Start Length",
            "Length to extend before path start (>= 0)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_LENGTH_ID, "End Length",
            "Length to extend after path end (>= 0)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Extended path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when extension succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        double startLength = readDoubleInput(INPUT_START_LENGTH_ID, 0.0d);
        double endLength = readDoubleInput(INPUT_END_LENGTH_ID, 0.0d);

        List<Vector3d> extended = PathUtils.extendPath(verts, startLength, endLength);
        PathData path = PathUtils.toPathData(extended);
        if (path == null) {
            writeInvalid();
            return;
        }
        outputValues.put(OUTPUT_PATH_ID, path);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
