package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "material.block_state.waterlogged",
    displayName = "Waterlogged State",
    description = "Assigns the waterlogged block-state property to placements or voxelized geometry.",
    category = "material.block_state",
    order = 3
)
public class WaterloggedStateNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_WATERLOGGED_ID = "input_waterlogged";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";

    public WaterloggedStateNode() {
        super(UUID.randomUUID(), "material.block_state.waterlogged");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to modify", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback block type when creating placements", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_WATERLOGGED_ID, "Waterlogged", "Whether waterlogged should be true or false", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with waterlogged state", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Assigns the waterlogged block-state property to placements or voxelized geometry.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean waterlogged = inputValues.get(INPUT_WATERLOGGED_ID) instanceof Boolean value && value;
        List<BlockPlacementData> base = resolvePlacements(getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone"));

        List<BlockPlacementData> resolved = new ArrayList<>(base.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(base.size());

        for (BlockPlacementData placement : base) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isBlank()) {
                continue;
            }
            BlockStateData state = placement.stateData() != null ? placement.stateData() : new BlockStateData();
            state.setProperty("waterlogged", waterlogged ? "true" : "false");
            resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), state));
            positions.add(placement.pos());
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
    }

    private List<BlockPlacementData> resolvePlacements(String fallbackBlockId) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null) {
                    resolved.add(placement);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> generated = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            generated.add(new BlockPlacementData(pos, fallbackBlockId));
        }
        return generated;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }
}
