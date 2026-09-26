package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.translate_point",
    displayName = "Translate Point",
    description = "Translates a geometric point by a displacement vector (Point + Vector → Point)",
    category = "reference.points",
    order = 6
)
public class TranslatePointNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_POINT_ID = "output_point";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TranslatePointNode() {
        super(UUID.randomUUID(), "reference.points.translate_point");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Start geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset",
            "Displacement vector added to the point",
            NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Point",
            "Translated geometric point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when point and offset inputs are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Translate Point";
    }

    @Override
    public String getDescription() {
        return "Translates a geometric point by a displacement vector (Point + Vector → Point)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = PointUtils.toPointPosition(inputValues.get(INPUT_POINT_ID));
        Vector3d offset = SpatialValueResolver.resolveVector(inputValues.get(INPUT_OFFSET_ID));

        if (!PointUtils.isFinite(point) || !PointUtils.isFinite(offset)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d result = new Vector3d(point).add(offset);
        outputValues.put(OUTPUT_POINT_ID, new PointData(result));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
