package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.IntersectionGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Deferred voxel boolean intersection: evaluated on the Minecraft block grid at voxelize/bake time.
 * {@code Valid} means the deferred expression was constructed — not that voxelization is guaranteed.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.intersection",
    displayName = "Intersection",
    description = "Keeps overlapping voxelized blocks from both geometries when built. Deferred voxel boolean on the Minecraft block grid (not analytic BRep).",
    category = "geometry.boolean",
    order = 1
)
public class IntersectionNode extends BaseNode {

    private static final String INPUT_LEFT_ID = "input_left";
    private static final String INPUT_RIGHT_ID = "input_right";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public IntersectionNode() {
        super(UUID.randomUUID(), "geometry.boolean.intersection");

        addInputPort(new BasePort(INPUT_LEFT_ID, "Left Geometry", "First geometry operand", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_RIGHT_ID, "Right Geometry", "Second geometry operand", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Deferred voxel intersection geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the deferred Intersection expression was constructed (not a voxelization guarantee)",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Keeps overlapping voxelized blocks from both geometries when built. Deferred voxel boolean on the Minecraft block grid (not analytic BRep).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object leftObj = inputValues.get(INPUT_LEFT_ID);
        Object rightObj = inputValues.get(INPUT_RIGHT_ID);

        if (!(leftObj instanceof GeometryData leftGeometry)) {
            writeInvalid("Left Geometry is required");
            return;
        }
        if (!(rightObj instanceof GeometryData rightGeometry)) {
            writeInvalid("Right Geometry is required");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, new IntersectionGeometryData(leftGeometry, rightGeometry));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
