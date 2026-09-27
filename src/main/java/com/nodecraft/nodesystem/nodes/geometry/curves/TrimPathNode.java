package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.trim_path",
    displayName = "Trim Path",
    description = "Extracts a sub-path between two normalized parameters.",
    category = "geometry.curves",
    order = 15
)
public class TrimPathNode extends AbstractCurveNode {

    @NodeProperty(displayName = "Default Start", category = "Trim", order = 1)
    private double defaultStart = 0.0d;

    @NodeProperty(displayName = "Default End", category = "Trim", order = 2)
    private double defaultEnd = 1.0d;

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String OUTPUT_PATH_ID = "output_path";

    public TrimPathNode() {
        super(UUID.randomUUID(), "geometry.curves.trim_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to trim (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_START_ID, "Start",
            "Normalized start parameter in [0..1]", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ID, "End",
            "Normalized end parameter in [0..1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path",
            "Trimmed path between Start and End", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when trim succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        Double start = CurveInputUtils.resolveOptionalFiniteDouble(this, INPUT_START_ID, defaultStart);
        Double end = CurveInputUtils.resolveOptionalFiniteDouble(this, INPUT_END_ID, defaultEnd);
        if (start == null || end == null) {
            putNullOutputs(OUTPUT_PATH_ID);
            markInvalid("Start or End is connected but invalid");
            return;
        }

        List<Vector3d> trimmed = PathUtils.trimPathByParameter(verts, start, end);
        PathData path = PathUtils.toPathData(trimmed);
        if (path == null) {
            putNullOutputs(OUTPUT_PATH_ID);
            markInvalid("Path is missing or trim parameters are invalid");
            return;
        }
        outputValues.put(OUTPUT_PATH_ID, path);
        markSuccess();
    }
}
