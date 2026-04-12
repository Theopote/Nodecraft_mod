package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Applies a dominant-axis facing state to placements based on an input direction vector.
 */
@NodeInfo(
    id = "material.block_state.auto_orient_blocks",
    displayName = "Auto Orient Blocks",
    description = "Automatically assigns a facing state from the dominant direction axis",
    category = "material.block_state",
    order = 1
)
public class AutoOrientBlocksNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_DIRECTION_ID = "input_direction";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";

    public AutoOrientBlocksNode() {
        super(UUID.randomUUID(), "material.block_state.auto_orient_blocks");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to orient", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback block type when building placements from coordinates or geometry", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Direction vector used to derive the facing state", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with optional facing state overrides", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Automatically assigns a facing state from the dominant direction axis";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String facing = resolveFacing(resolveDirection(inputValues.get(INPUT_DIRECTION_ID)));
        List<BlockPlacementData> placements = resolvePlacements(facing);
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>();

        for (BlockPlacementData placement : placements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }
            positions.add(placement.pos());
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
    }

    private List<BlockPlacementData> resolvePlacements(@Nullable String facing) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null && placement.blockId() != null) {
                    resolved.add(new BlockPlacementData(
                        placement.pos(),
                        placement.blockId(),
                        applyFacing(placement.stateData(), facing)
                    ));
                }
            }
            return resolved;
        }

        String blockType = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone");
        BlockPosList positions = GeometryVoxelizer.resolveBlocks(
            inputValues.get(INPUT_COORDINATES_ID),
            inputValues.get(INPUT_GEOMETRY_ID),
            inputValues.get(INPUT_BOX_GEOMETRY_ID),
            inputValues.get(INPUT_CYLINDER_GEOMETRY_ID),
            inputValues.get(INPUT_SPHERE_GEOMETRY_ID),
            inputValues.get(INPUT_TORUS_GEOMETRY_ID),
            true
        );

        List<BlockPlacementData> resolved = new ArrayList<>();
        for (BlockPos pos : positions) {
            resolved.add(new BlockPlacementData(pos, blockType, applyFacing(null, facing)));
        }
        return resolved;
    }

    private @Nullable BlockStateData applyFacing(@Nullable BlockStateData existingState, @Nullable String facing) {
        if (facing == null || facing.isEmpty()) {
            return existingState != null ? existingState.copy() : null;
        }

        BlockStateData mergedState = existingState != null ? existingState.copy() : new BlockStateData();
        mergedState.setProperty("facing", facing);
        return mergedState;
    }

    private @Nullable String resolveFacing(@Nullable Vector3d direction) {
        if (direction == null || direction.lengthSquared() <= 1.0e-9) {
            return null;
        }

        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);

        if (absX >= absY && absX >= absZ) {
            return direction.x >= 0.0d ? "east" : "west";
        }
        if (absY >= absX && absY >= absZ) {
            return direction.y >= 0.0d ? "up" : "down";
        }
        return direction.z >= 0.0d ? "south" : "north";
    }

    private @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof PointData point) {
            return point.getPosition();
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof Coordinate coordinate) {
            return new Vector3d(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        }
        if (value instanceof BlockPos pos) {
            return new Vector3d(pos.getX(), pos.getY(), pos.getZ());
        }
        return null;
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isEmpty()) ? text : fallback;
    }
}
