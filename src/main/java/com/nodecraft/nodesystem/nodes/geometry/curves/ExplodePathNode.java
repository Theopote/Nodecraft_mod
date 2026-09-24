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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.curves.explode_path",
    displayName = "Explode Path",
    description = "Decomposes a path into per-segment paths as PATH_LIST.",
    category = "geometry.curves",
    order = 7
)
public class ExplodePathNode extends AbstractCurveNode {

    private static final String INPUT_PATH_ID = "input_path";

    private static final String OUTPUT_SEGMENTS_ID = "output_segments";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ExplodePathNode() {
        super(UUID.randomUUID(), "geometry.curves.explode_path");

        addInputPort(new BasePort(INPUT_PATH_ID, "Path",
            "Path to explode into segment paths", NodeDataType.PATH, this));

        addOutputPort(new BasePort(OUTPUT_SEGMENTS_ID, "Segments",
            "One path per geometric segment", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of segment paths", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when explode succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> verts = resolvePathVertices(INPUT_PATH_ID);
        List<List<Vector3d>> rawSegments = PathUtils.explodePath(verts);
        if (rawSegments.isEmpty()) {
            writeInvalid();
            return;
        }

        List<PathData> segments = new ArrayList<>(rawSegments.size());
        for (List<Vector3d> segmentVerts : rawSegments) {
            PathData segment = PathUtils.toPathData(segmentVerts);
            if (segment != null) {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_SEGMENTS_ID, List.copyOf(segments));
        outputValues.put(OUTPUT_COUNT_ID, segments.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
