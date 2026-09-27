package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import org.joml.Vector3d;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.box_from_corners",
    displayName = "Box by Two Corners",
    description = "Generates an axis-aligned box from two opposite corner points",
    category = "geometry.primitives",
    order = 2
)
public class BoxCornersNode extends AbstractBoxGeneratorNode {

    private static final String INPUT_CORNER_A_ID = "input_corner_a";
    private static final String INPUT_CORNER_B_ID = "input_corner_b";

    public BoxCornersNode() {
        super("geometry.primitives.box_from_corners");

        addInputPort(new BasePort(INPUT_CORNER_A_ID, "Corner A", "First corner of the box", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_CORNER_B_ID, "Corner B", "Opposite corner of the box. The result stays axis-aligned.", NodeDataType.POINT, this));
    }

    @Override
    public String getDescription() {
        return "Generates an axis-aligned box from two opposite corner points";
    }

    @Override
    public String getDisplayName() {
        return "Box by Two Corners";
    }

    @Override
    protected BoxDefinition resolveBoxDefinition() {
        Vector3d cornerA = resolveOptionalPoint(INPUT_CORNER_A_ID, null);
        if (cornerA == null) {
            failBox(isPortConnected(INPUT_CORNER_A_ID)
                ? "Corner A input is invalid"
                : "Box requires corner A");
            return null;
        }

        Vector3d cornerB = resolveOptionalPoint(INPUT_CORNER_B_ID, null);
        if (cornerB == null) {
            failBox(isPortConnected(INPUT_CORNER_B_ID)
                ? "Corner B input is invalid"
                : "Box requires corner B");
            return null;
        }

        return createContinuousAxisAlignedDefinition(cornerA, cornerB);
    }
}
