package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.MaterialMappingSupport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.*;

/**
 * Assigns stair-facing, half, and corner shape state data using local stair neighborhood analysis.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.block_state.stair_shape",
    displayName = "Stair Shape",
    description = "Resolves stair corner shape from neighboring stair placements",
    category = "material.block_state",
    order = 3
)
public class StairShapeNode extends BaseNode {

    private static final String INPUT_PLACEMENTS_ID = "input_placements";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_HALF_ID = "input_half";

    private static final String OUTPUT_PLACEMENTS_ID = "output_placements";

    public StairShapeNode() {
        super(UUID.randomUUID(), "material.block_state.stair_shape");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Stair placements to resolve corner shapes for", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Fallback horizontal facing when placement state lacks facing", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_HALF_ID, "Half", "Optional stair half override: bottom or top", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with resolved stair shape state", NodeDataType.BLOCK_PLACEMENT_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Resolves stair corner shape from neighboring stair placements";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Direction fallbackFacing = resolveHorizontalFacing(
            BlockStateValidationUtils.resolveStrictVector3d(inputValues.get(INPUT_DIRECTION_ID)));
        String half = resolveHalf(inputValues.get(INPUT_HALF_ID));

        List<BlockPlacementData> basePlacements = MaterialMappingSupport.extractPlacements(inputValues.get(INPUT_PLACEMENTS_ID));
        Map<BlockPos, StairPlacement> stairMap = buildStairMap(basePlacements, fallbackFacing, half);

        List<BlockPlacementData> resolved = new ArrayList<>(basePlacements.size());
        for (BlockPlacementData placement : basePlacements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }

            if (!isStairBlock(placement.blockId())) {
                resolved.add(new BlockPlacementData(
                    placement.pos(),
                    placement.blockId(),
                    copyState(placement.stateData())
                ));
                continue;
            }

            StairPlacement stair = stairMap.get(placement.pos());
            BlockStateData state = stair != null
                ? createStateData(placement.stateData(), stair, stairMap)
                : copyState(placement.stateData());

            resolved.add(new BlockPlacementData(placement.pos(), placement.blockId(), state));
        }

        outputValues.put(OUTPUT_PLACEMENTS_ID, resolved);
    }

    private Map<BlockPos, StairPlacement> buildStairMap(List<BlockPlacementData> placements, Direction fallbackFacing, String fallbackHalf) {
        Map<BlockPos, StairPlacement> map = new HashMap<>();
        for (BlockPlacementData placement : placements) {
            if (placement.pos() == null || placement.blockId() == null || placement.blockId().isEmpty()) {
                continue;
            }
            if (!isStairBlock(placement.blockId())) {
                continue;
            }
            Direction facing = resolvePlacementFacing(placement.stateData(), fallbackFacing);
            String half = resolvePlacementHalf(placement.stateData(), fallbackHalf);
            map.put(Objects.requireNonNull(placement.pos()).toImmutable(), new StairPlacement(Objects.requireNonNull(placement.pos()).toImmutable(), placement.blockId(), facing, half));
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

    private BlockStateData copyState(@Nullable BlockStateData stateData) {
        BlockStateData copy = stateData != null ? stateData.copy() : new BlockStateData();
        BlockStateValidationUtils.stripIdentityKeys(copy);
        return copy;
    }

    private record StairPlacement(BlockPos pos, String blockId, Direction facing, String half) {
    }
}
