package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.CylinderBlockGenerator;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a cylinder block volume and exposes the abstract cylinder geometry.
 */
@NodeInfo(
    id = "spatial.generators.cylinder_blocks",
    displayName = "Cylinder (Blocks)",
    description = "Generates a cylinder of blocks and outputs cylinder geometry",
    category = "spatial.generators"
)
public class CylinderBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Fill Cylinder", category = "Shape", order = 1)
    private boolean fillCylinder = true;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_BLOCKS_ID = "output_cylinder_blocks";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CYLINDER_GEOMETRY_ID = "output_cylinder_geometry";

    public CylinderBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.cylinder_blocks");

        addInputPort(new BasePort(INPUT_START_ID, "Start Point", "Start point of the cylinder axis", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_END_ID, "End Point", "End point of the cylinder axis", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Cylinder radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Cylinder Blocks", "The blocks forming the cylinder", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Resolved cylinder geometry", NodeDataType.CYLINDER_GEOMETRY, this));
    }

    @Override
    public String getDescription() {
        return "Generates a cylinder of blocks and outputs cylinder geometry";
    }

    @Override
    public String getDisplayName() {
        return "Cylinder (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object startObj = inputValues.get(INPUT_START_ID);
        Object endObj = inputValues.get(INPUT_END_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        BlockPosList result = new BlockPosList();
        CylinderGeometryData geometry = null;

        if (startObj instanceof BlockPos start
            && endObj instanceof BlockPos end
            && radiusObj instanceof Number radiusNumber) {
            double radius = Math.max(1.0d, radiusNumber.doubleValue());
            geometry = new CylinderGeometryData(
                new Vector3d(start.getX(), start.getY(), start.getZ()),
                new Vector3d(end.getX(), end.getY(), end.getZ()),
                radius
            );
            CylinderBlockGenerator.populateCylinder(
                result,
                CylinderBlockGenerator.createBoundingRegion(geometry),
                geometry,
                fillCylinder
            );
        }

        outputValues.put(OUTPUT_BLOCKS_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CYLINDER_GEOMETRY_ID, geometry);
    }

    public boolean isFillCylinder() {
        return fillCylinder;
    }

    public void setFillCylinder(boolean fillCylinder) {
        if (this.fillCylinder != fillCylinder) {
            this.fillCylinder = fillCylinder;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillCylinder", fillCylinder);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("fillCylinder") instanceof Boolean fillValue) {
            setFillCylinder(fillValue);
        }
    }
}
