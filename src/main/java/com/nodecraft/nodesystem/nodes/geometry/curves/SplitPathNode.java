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
    id = "geometry.curves.split_path",
    displayName = "Split Path",
    description = "Splits a path at a normalized parameter into two path segments.",
    category = "geometry.curves",
    order = 5
)
public class SplitPathNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_PARAMETER_ID = "input_parameter";

    private static final String OUTPUT_PATH_A_ID = "output_path_a";
    private static final String OUTPUT_PATH_B_ID = "output_path_b";
    private static final String OUTPUT_PARAMETER_ID = "output_parameter";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SplitPathNode() {
        super(UUID.randomUUID(), "geometry.curves.split_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to split (line, polyline, or curve)", NodeDataType.PATH, this));
        addInputPort(new BasePort(INPUT_PARAMETER_ID, "Parameter",
            "Normalized split parameter in [0..1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_PATH_A_ID, "Path A",
            "Path from start to split parameter", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PATH_B_ID, "Path B",
            "Path from split parameter to end", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_PARAMETER_ID, "Parameter",
            "Clamped parameter used for the split", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when split succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        double parameter = readDoubleInput(INPUT_PARAMETER_ID, 0.5d);
        if (!Double.isFinite(parameter)) {
            writeInvalid();
            return;
        }
        parameter = Math.max(0.0d, Math.min(1.0d, parameter));

        PathUtils.PathSplitResult split = PathUtils.splitPathByParameter(verts, parameter);
        PathData pathA = split == null ? null : PathUtils.toPathData(split.pathA());
        PathData pathB = split == null ? null : PathUtils.toPathData(split.pathB());
        if (pathA == null || pathB == null) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_PATH_A_ID, pathA);
        outputValues.put(OUTPUT_PATH_B_ID, pathB);
        outputValues.put(OUTPUT_PARAMETER_ID, parameter);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_PATH_A_ID, null);
        outputValues.put(OUTPUT_PATH_B_ID, null);
        outputValues.put(OUTPUT_PARAMETER_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
