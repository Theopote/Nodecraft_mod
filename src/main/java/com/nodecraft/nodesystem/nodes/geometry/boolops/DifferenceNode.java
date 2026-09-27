package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DifferenceGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Deferred voxel boolean difference: evaluated on the Minecraft block grid at voxelize/bake time.
 * {@code Valid} means the deferred expression was constructed — not that voxelization is guaranteed.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.difference",
    displayName = "Difference",
    description = "Subtracts cutter geometry when voxelized/built. Result is evaluated on the Minecraft block grid (deferred voxel boolean, not analytic BRep).",
    category = "geometry.boolean",
    order = 0
)
public class DifferenceNode extends BaseNode {

    private static final String INPUT_BASE_ID = "input_base";
    private static final String INPUT_CUTTER_ID = "input_cutter";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DifferenceNode() {
        super(UUID.randomUUID(), "geometry.boolean.difference");

        addInputPort(new BasePort(INPUT_BASE_ID, "Base Geometry", "Geometry to subtract from", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CUTTER_ID, "Cutter Geometry", "Geometry that will be removed from the base", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Deferred voxel difference geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the deferred Difference expression was constructed (not a voxelization guarantee)",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Subtracts cutter geometry when voxelized/built. Result is evaluated on the Minecraft block grid (deferred voxel boolean, not analytic BRep).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object baseObj = inputValues.get(INPUT_BASE_ID);
        Object cutterObj = inputValues.get(INPUT_CUTTER_ID);

        if (!(baseObj instanceof GeometryData baseGeometry)) {
            writeInvalid("Base Geometry is required");
            return;
        }
        if (!(cutterObj instanceof GeometryData cutterGeometry)) {
            writeInvalid("Cutter Geometry is required");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, new DifferenceGeometryData(baseGeometry, cutterGeometry));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
