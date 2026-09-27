package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
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
    order = 17
)
public class ExtendPathNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Start Length", category = "Extend", order = 1)
    private double defaultStartLength = 0.0d;

    @NodeProperty(displayName = "Default End Length", category = "Extend", order = 2)
    private double defaultEndLength = 0.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_START_LENGTH_ID = "input_start_length";
    private static final String INPUT_END_LENGTH_ID = "input_end_length";
    private static final String OUTPUT_PATH_ID = "output_path";

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
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        Double startLength = resolveNonNegativeDouble(INPUT_START_LENGTH_ID, defaultStartLength);
        Double endLength = resolveNonNegativeDouble(INPUT_END_LENGTH_ID, defaultEndLength);
        if (startLength == null || endLength == null) {
            putNullOutputs(OUTPUT_PATH_ID);
            markInvalid("Start Length or End Length is connected but invalid");
            return;
        }

        List<Vector3d> extended = PathUtils.extendPath(verts, startLength, endLength);
        PathData path = PathUtils.toPathData(extended);
        if (path == null) {
            putNullOutputs(OUTPUT_PATH_ID);
            markInvalid("Path is missing, closed, or cannot be extended");
            return;
        }
        outputValues.put(OUTPUT_PATH_ID, path);
        markSuccess();
    }
}
