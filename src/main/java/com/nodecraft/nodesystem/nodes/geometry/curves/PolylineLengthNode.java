package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.path_length",
    displayName = "Path Length",
    description = "Computes the total length of a line, polyline, or curve path",
    category = "geometry.curves",
    order = 21
)
public class PolylineLengthNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    public PolylineLengthNode() {
        super(UUID.randomUUID(), "geometry.curves.path_length");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to measure (line, polyline, or curve)",
            NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Total path length",
            NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when a length was computed",
            NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        if (verts == null || verts.size() < 2) {
            putNullOutputs(OUTPUT_LENGTH_ID);
            markInvalid("Path is missing or invalid");
            return;
        }

        boolean closed = PathUtils.isClosed(verts);
        List<Vector3d> unique = closed ? verts.subList(0, verts.size() - 1) : verts;
        double[] cumulative = PathUtils.buildCumulative(unique, closed);
        if (cumulative == null || cumulative.length == 0) {
            putNullOutputs(OUTPUT_LENGTH_ID);
            markInvalid("Path length could not be computed");
            return;
        }

        outputValues.put(OUTPUT_LENGTH_ID, cumulative[cumulative.length - 1]);
        markSuccess();
    }
}
