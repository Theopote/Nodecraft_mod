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
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public StairShapeNode() {
        super(UUID.randomUUID(), "material.block_state.stair_shape");

        addInputPort(new BasePort(INPUT_PLACEMENTS_ID, "Block Placements", "Stair placements to resolve corner shapes for", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Fallback horizontal facing when placement state lacks facing", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_HALF_ID, "Half", "Optional stair half override: bottom or top", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PLACEMENTS_ID, "Block Placements", "Placements with resolved stair shape state", NodeDataType.BLOCK_PLACEMENT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when placements and optional Direction/Half inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Resolves stair corner shape from neighboring stair placements";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        MaterialMappingSupport.PlacementListResult parsed =
            MaterialMappingSupport.parsePlacementsStrict(inputValues.get(INPUT_PLACEMENTS_ID));
        if (!parsed.valid()) {
            emitFail(parsed.error());
            return;
        }

        DirectionResult directionResult = resolveDirectionInput();
        if (!directionResult.valid()) {
            emitFail(directionResult.error());
            return;
        }

        HalfResult halfResult = resolveHalfInput();
        if (!halfResult.valid()) {
            emitFail(halfResult.error());
            return;
        }

        FacingBuildResult facingBuild = buildStairMap(
            parsed.placements(),
            directionResult.fallbackFacing(),
            halfResult.overrideHalf()
        );
        if (!facingBuild.valid()) {
            emitFail(facingBuild.error());
            return;
        }

        Map<BlockPos, StairPlacement> stairMap = facingBuild.stairMap();
        List<BlockPlacementData> resolved = new ArrayList<>(parsed.placements().size());
        for (BlockPlacementData placement : parsed.placements()) {
            if (!BlockStateValidationUtils.isStairsBlock(placement.blockId())) {
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

        emitOk(resolved);
    }

    private DirectionResult resolveDirectionInput() {
        if (!isDriven(INPUT_DIRECTION_ID)) {
            return DirectionResult.ok(null);
        }
        Vector3d direction = BlockStateValidationUtils.resolveStrictVector3d(inputValues.get(INPUT_DIRECTION_ID));
        if (direction == null || direction.lengthSquared() <= 1.0e-9d) {
            return DirectionResult.fail("Direction connected but invalid or zero");
        }
        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);
        if (absX < 1.0e-9d && absZ < 1.0e-9d) {
            return DirectionResult.fail("Direction connected but invalid or zero");
        }
        Direction facing;
        if (absX >= absZ) {
            facing = direction.x >= 0.0d ? Direction.EAST : Direction.WEST;
        } else {
            facing = direction.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
        }
        return DirectionResult.ok(facing);
    }

    private HalfResult resolveHalfInput() {
        if (!isDriven(INPUT_HALF_ID)) {
            return HalfResult.ok(null);
        }
        Object value = inputValues.get(INPUT_HALF_ID);
        if (!(value instanceof String text)) {
            return HalfResult.fail("Half must be 'top' or 'bottom'");
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if ("top".equals(normalized) || "bottom".equals(normalized)) {
            return HalfResult.ok(normalized);
        }
        return HalfResult.fail("Half must be 'top' or 'bottom'");
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.get(portId) != null;
    }

    private FacingBuildResult buildStairMap(
            List<BlockPlacementData> placements,
            @Nullable Direction fallbackFacing,
            @Nullable String overrideHalf
    ) {
        Map<BlockPos, StairPlacement> map = new HashMap<>();
        for (BlockPlacementData placement : placements) {
            if (!BlockStateValidationUtils.isStairsBlock(placement.blockId())) {
                continue;
            }
            Direction facing = resolvePlacementFacing(placement.stateData(), fallbackFacing);
            if (facing == null) {
                return FacingBuildResult.fail("Stair facing required");
            }
            String half = resolvePlacementHalf(placement.stateData(), overrideHalf);
            map.put(
                Objects.requireNonNull(placement.pos()).toImmutable(),
                new StairPlacement(
                    Objects.requireNonNull(placement.pos()).toImmutable(),
                    placement.blockId(),
                    facing,
                    half
                )
            );
        }
        return FacingBuildResult.ok(map);
    }

    private BlockStateData createStateData(
            @Nullable BlockStateData existingState,
            StairPlacement stair,
            Map<BlockPos, StairPlacement> stairMap
    ) {
        BlockStateData merged = copyState(existingState)
            .withProperty("facing", stair.facing().asString())
            .withProperty("half", stair.half())
            .withProperty("shape", resolveShape(stair, stairMap));
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
            && BlockStateValidationUtils.isStairsBlock(other.blockId())
            && base.half().equals(other.half());
    }

    private boolean isDifferentOrientation(
            StairPlacement stair,
            Map<BlockPos, StairPlacement> stairMap,
            Direction offsetDirection
    ) {
        StairPlacement other = stairMap.get(stair.pos().offset(offsetDirection));
        return other == null
            || !BlockStateValidationUtils.isStairsBlock(other.blockId())
            || other.facing() != stair.facing()
            || !other.half().equals(stair.half());
    }

    /**
     * @return facing from state, or optional Direction fallback; null when neither is available
     */
    private @Nullable Direction resolvePlacementFacing(
            @Nullable BlockStateData stateData,
            @Nullable Direction fallbackFacing
    ) {
        String facing = stateData != null ? stateData.get("facing") : null;
        if (facing == null || facing.isBlank()) {
            return fallbackFacing;
        }
        return switch (facing.toLowerCase(Locale.ROOT)) {
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "north" -> Direction.NORTH;
            default -> null;
        };
    }

    private String resolvePlacementHalf(@Nullable BlockStateData stateData, @Nullable String overrideHalf) {
        if (overrideHalf != null) {
            return overrideHalf;
        }
        if (stateData == null) {
            return "bottom";
        }
        String value = stateData.get("half");
        if (value == null || value.isBlank()) {
            return "bottom";
        }
        return "top".equalsIgnoreCase(value) ? "top" : "bottom";
    }

    private BlockStateData copyState(@Nullable BlockStateData stateData) {
        BlockStateData copy = stateData != null ? stateData.copy() : new BlockStateData();
        BlockStateValidationUtils.stripIdentityKeys(copy);
        return copy;
    }

    private void emitFail(String error) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void emitOk(List<BlockPlacementData> placements) {
        outputValues.put(OUTPUT_PLACEMENTS_ID, placements);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private record DirectionResult(boolean valid, @Nullable Direction fallbackFacing, String error) {
        static DirectionResult ok(@Nullable Direction facing) {
            return new DirectionResult(true, facing, "");
        }

        static DirectionResult fail(String error) {
            return new DirectionResult(false, null, error);
        }
    }

    private record HalfResult(boolean valid, @Nullable String overrideHalf, String error) {
        static HalfResult ok(@Nullable String half) {
            return new HalfResult(true, half, "");
        }

        static HalfResult fail(String error) {
            return new HalfResult(false, null, error);
        }
    }

    private record FacingBuildResult(boolean valid, Map<BlockPos, StairPlacement> stairMap, String error) {
        static FacingBuildResult ok(Map<BlockPos, StairPlacement> map) {
            return new FacingBuildResult(true, map, "");
        }

        static FacingBuildResult fail(String error) {
            return new FacingBuildResult(false, Map.of(), error);
        }
    }

    private record StairPlacement(BlockPos pos, String blockId, Direction facing, String half) {
    }
}
