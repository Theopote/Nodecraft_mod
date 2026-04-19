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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assigns stair-facing, half, and corner shape state data using local stair neighborhood analysis.
 */
@NodeInfo(
    id = "material.block_state.stair_shape",
    displayName = "Stair Shape",
    description = "Assigns stair facing, half, and inner/outer corner shape states",
    category = "material.block_state",
    order = 2
)
public class StairShapeNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_CYLINDER_GEOMETRY_ID = "input_cylinder_geometry";
    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_HALF_ID = "input_half";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";

    public StairShapeNode() {
        super(UUID.randomUUID(), "material.block_state.stair_shape");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Optional incoming placements to convert into stair states", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinate list", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified abstract geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry data to materialize", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_CYLINDER_GEOMETRY_ID, "Cylinder Geometry", "Cylinder geometry data to materialize", NodeDataType.CYLINDER_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry data to materialize", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry data to materialize", NodeDataType.TORUS_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Fallback stair block id when building placements from coordinates or geometry", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Fallback direction used to derive a horizontal facing", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_HALF_ID, "Half", "Optional stair half override: bottom or top", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with stair state overrides", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "Positions", "Resolved block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Block IDs aligned with the positions list", NodeDataType.BLOCK_INFO_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Assigns stair facing, half, and inner/outer corner shape states";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Direction fallbackFacing = resolveHorizontalFacing(resolveDirection(inputValues.get(INPUT_DIRECTION_ID)));
        String half = resolveHalf(inputValues.get(INPUT_HALF_ID));

        List<BlockPlacementData> basePlacements = resolvePlacements(fallbackFacing, half);
        Map<BlockPos, StairPlacement> stairMap = buildStairMap(basePlacements, fallbackFacing, half);

        List<BlockPlacementData> resolved = new ArrayList<>(basePlacements.size());
        BlockPosList positions = new BlockPosList();
        List<String> blockIds = new ArrayList<>(basePlacements.size());

        for (BlockPlacementData placement : basePlacements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }

            StairPlacement stair = stairMap.get(placement.pos());
            BlockStateData state = stair != null
                ? createStateData(placement.stateData(), stair, stairMap)
                : copyState(placement.stateData());

            resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), state));
            positions.add(placement.pos());
            blockIds.add(placement.blockId());
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
        outputValues.put(OUTPUT_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, blockIds);
    }

    private List<BlockPlacementData> resolvePlacements(Direction fallbackFacing, String fallbackHalf) {
        Object placementsObj = inputValues.get(INPUT_PLACEMENTS_ID);
        if (placementsObj instanceof List<?> placementList && !placementList.isEmpty()) {
            List<BlockPlacementData> resolved = new ArrayList<>();
            for (Object entry : placementList) {
                if (entry instanceof BlockPlacementData placement && placement.pos() != null && placement.blockId() != null) {
                    resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), placement.stateData()));
                }
            }
            return resolved;
        }

        String blockType = getInputString(INPUT_BLOCK_TYPE_ID, "minecraft:stone_stairs");
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
            BlockStateData state = new BlockStateData();
            state.setProperty("facing", fallbackFacing.asString());
            state.setProperty("half", fallbackHalf);
            state.setProperty("shape", "straight");
            resolved.add(new BlockPlacementData(pos, blockType, state));
        }
        return resolved;
    }

    private Map<BlockPos, StairPlacement> buildStairMap(List<BlockPlacementData> placements, Direction fallbackFacing, String fallbackHalf) {
        Map<BlockPos, StairPlacement> map = new HashMap<>();
        for (BlockPlacementData placement : placements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }
            Direction facing = resolvePlacementFacing(placement.stateData(), fallbackFacing);
            String half = resolvePlacementHalf(placement.stateData(), fallbackHalf);
            map.put(placement.pos().toImmutable(), new StairPlacement(placement.pos().toImmutable(), placement.blockId(), facing, half));
        }
        return map;
    }

    private BlockStateData createStateData(@Nullable BlockStateData existingState, StairPlacement stair, Map<BlockPos, StairPlacement> stairMap) {
        BlockStateData merged = copyState(existingState);
        merged.setProperty("facing", stair.facing().asString());
        merged.setProperty("half", stair.half());
        merged.setProperty("shape", resolveShape(stair, stairMap));
        return merged;
    }

    private String resolveShape(StairPlacement stair, Map<BlockPos, StairPlacement> stairMap) {
        StairPlacement front = stairMap.get(stair.pos().offset(stair.facing()));
        if (isCompatibleStair(stair, front)) {
            Direction frontFacing = front.facing();
            if (frontFacing.getAxis() != stair.facing().getAxis()
                && isDifferentOrientation(stair, stairMap, frontFacing.getOpposite())) {
                return frontFacing == stair.facing().rotateYCounterclockwise() ? "outer_left" : "outer_right";
            }
        }

        StairPlacement back = stairMap.get(stair.pos().offset(stair.facing().getOpposite()));
        if (isCompatibleStair(stair, back)) {
            Direction backFacing = back.facing();
            if (backFacing.getAxis() != stair.facing().getAxis()
                && isDifferentOrientation(stair, stairMap, backFacing)) {
                return backFacing == stair.facing().rotateYCounterclockwise() ? "inner_left" : "inner_right";
            }
        }

        return "straight";
    }

    private boolean isCompatibleStair(StairPlacement base, @Nullable StairPlacement other) {
        return other != null
            && isStairBlock(other.blockId())
            && base.half().equals(other.half());
    }

    private boolean isDifferentOrientation(StairPlacement stair, Map<BlockPos, StairPlacement> stairMap, Direction offsetDirection) {
        StairPlacement other = stairMap.get(stair.pos().offset(offsetDirection));
        return other == null
            || !isStairBlock(other.blockId())
            || other.facing() != stair.facing()
            || !other.half().equals(stair.half());
    }

    private boolean isStairBlock(String blockId) {
        return blockId != null && blockId.contains("stairs");
    }

    private Direction resolvePlacementFacing(@Nullable BlockStateData stateData, Direction fallbackFacing) {
        String facing = stateData != null ? stateData.getProperty("facing", fallbackFacing.asString()) : fallbackFacing.asString();
        return switch (facing) {
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private String resolvePlacementHalf(@Nullable BlockStateData stateData, String fallbackHalf) {
        if (stateData == null) {
            return fallbackHalf;
        }
        String value = stateData.getProperty("half", fallbackHalf);
        return "top".equalsIgnoreCase(value) ? "top" : "bottom";
    }

    private String resolveHalf(@Nullable Object value) {
        if (value instanceof String text && "top".equalsIgnoreCase(text)) {
            return "top";
        }
        return "bottom";
    }

    private Direction resolveHorizontalFacing(@Nullable Vector3d direction) {
        if (direction == null || direction.lengthSquared() <= 1.0e-9d) {
            return Direction.NORTH;
        }

        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);
        if (absX >= absZ) {
            return direction.x >= 0.0d ? Direction.EAST : Direction.WEST;
        }
        return direction.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
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

    private BlockStateData copyState(@Nullable BlockStateData stateData) {
        return stateData != null ? stateData.copy() : new BlockStateData();
    }

    private String getInputString(String portId, String fallback) {
        Object value = inputValues.get(portId);
        return (value instanceof String text && !text.isBlank()) ? text : fallback;
    }

    private record StairPlacement(BlockPos pos, String blockId, Direction facing, String half) {
    }
}
