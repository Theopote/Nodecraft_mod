package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "input.context.player_raycast",
    displayName = "Player Raycast",
    description = "Raycasts from the player view and reports hit position, block, entity, and distance.",
    category = "input.context",
    order = 1
)
public class PlayerRaycastNode extends BaseCustomUINode {

    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_HIT_POSITION_ID = "output_hit_position";
    private static final String OUTPUT_HIT_BLOCK_ID = "output_hit_block";
    private static final String OUTPUT_HIT_ENTITY_ID = "output_hit_entity";
    private static final String OUTPUT_HIT_DISTANCE_ID = "output_hit_distance";
    private static final String OUTPUT_HAS_HIT_ID = "output_has_hit";

    @NodeProperty(
            displayName = "Max Distance",
            category = "Raycast",
            order = 1,
            description = "Maximum raycast distance."
    )
    private double maxDistance = 100.0d;

    @NodeProperty(
            displayName = "Include Entities",
            category = "Raycast",
            order = 2,
            description = "Whether entities should be included in the hit test."
    )
    private boolean includeEntities = true;

    @NodeProperty(
            displayName = "Include Fluids",
            category = "Raycast",
            order = 3,
            description = "Whether fluids should be included in the hit test."
    )
    private boolean includeFluids = false;

    public PlayerRaycastNode() {
        super(UUID.randomUUID(), "input.context.player_raycast");

        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether world context was available for raycast", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_POSITION_ID, "Hit Position", "World-space hit location", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_HIT_BLOCK_ID, "Hit Block", "The currently hit block", NodeDataType.BLOCK_INFO, this));
        addOutputPort(new BasePort(OUTPUT_HIT_ENTITY_ID, "Hit Entity", "The currently hit entity", NodeDataType.ENTITY_INFO, this));
        addOutputPort(new BasePort(OUTPUT_HIT_DISTANCE_ID, "Hit Distance", "Distance from the player to the hit", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HAS_HIT_ID, "Has Hit", "Whether the raycast hit something", NodeDataType.BOOLEAN, this));

        invalidateContext();
    }

    @Override
    public String getDescription() {
        return "Raycasts from the player view and reports hit position, block, entity, and distance.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!ContextReadUtils.isLiveContextAvailable(context)) {
            invalidateContext();
            return;
        }

        performRaycast(context);
    }

    @Override
    protected float calculateUIHeight() {
        return 0.0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }

    private void performRaycast(ExecutionContext context) {
        PlayerEntity sourceEntity = context.getPlayer();
        if (!(sourceEntity instanceof ServerPlayerEntity player)) {
            invalidateContext();
            return;
        }

        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        if (look.lengthSquared() <= 1.0e-12d) {
            setMiss();
            return;
        }

        double castDistance = Math.max(0.0d, maxDistance);
        Vec3d end = start.add(look.normalize().multiply(castDistance));

        RaycastContext.FluidHandling fluidHandling = includeFluids
            ? RaycastContext.FluidHandling.ANY
            : RaycastContext.FluidHandling.NONE;
        BlockHitResult blockHit = context.getWorld().raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.OUTLINE,
            fluidHandling,
            sourceEntity
        ));

        HitCandidate blockCandidate = null;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            blockCandidate = HitCandidate.block(
                blockHit.getPos(),
                blockHit.getBlockPos(),
                start.distanceTo(blockHit.getPos())
            );
        }

        HitCandidate entityCandidate = includeEntities
            ? raycastEntities(context, sourceEntity, start, end, castDistance)
            : null;

        HitCandidate best = chooseNearest(blockCandidate, entityCandidate);
        if (best == null) {
            setMiss();
            return;
        }

        setHit(
            best.hitPos,
            best.blockPos != null ? context.getWorld().getBlockState(best.blockPos) : null,
            best.entity,
            best.distance
        );
    }

    private @Nullable HitCandidate raycastEntities(ExecutionContext context,
                                                   PlayerEntity sourceEntity,
                                                   Vec3d start,
                                                   Vec3d end,
                                                   double maxCastDistance) {
        Box sweep = new Box(start, end).expand(1.0d);
        List<Entity> entities = new ArrayList<>(context.getWorld().getOtherEntities(sourceEntity, sweep));
        entities.remove(sourceEntity);

        HitCandidate best = null;
        for (Entity entity : entities) {
            Vec3d hitPos = entity.getBoundingBox().raycast(start, end).orElse(null);
            if (hitPos == null) {
                continue;
            }

            double distance = start.distanceTo(hitPos);
            if (distance > maxCastDistance) {
                continue;
            }

            if (best == null || distance < best.distance) {
                best = HitCandidate.entity(hitPos, distance, entity);
            }
        }
        return best;
    }

    private @Nullable HitCandidate chooseNearest(@Nullable HitCandidate block, @Nullable HitCandidate entity) {
        if (block == null) {
            return entity;
        }
        if (entity == null) {
            return block;
        }
        return block.distance <= entity.distance ? block : entity;
    }

    private void invalidateContext() {
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_HAS_HIT_ID, false);
        outputValues.put(OUTPUT_HIT_POSITION_ID, null);
        outputValues.put(OUTPUT_HIT_BLOCK_ID, null);
        outputValues.put(OUTPUT_HIT_ENTITY_ID, null);
        outputValues.put(OUTPUT_HIT_DISTANCE_ID, 0.0d);
        syncOutputPorts();
    }

    private void setMiss() {
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HAS_HIT_ID, false);
        outputValues.put(OUTPUT_HIT_POSITION_ID, null);
        outputValues.put(OUTPUT_HIT_BLOCK_ID, null);
        outputValues.put(OUTPUT_HIT_ENTITY_ID, null);
        outputValues.put(OUTPUT_HIT_DISTANCE_ID, 0.0d);
        syncOutputPorts();
    }

    private void setHit(Vec3d hitPos, @Nullable Object blockState, @Nullable Entity entity, double distance) {
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_HAS_HIT_ID, true);
        outputValues.put(OUTPUT_HIT_POSITION_ID, new PointData(hitPos.x, hitPos.y, hitPos.z));
        outputValues.put(OUTPUT_HIT_BLOCK_ID, blockState);
        outputValues.put(OUTPUT_HIT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_HIT_DISTANCE_ID, distance);
        syncOutputPorts();
    }

    private static final class HitCandidate {
        final Vec3d hitPos;
        @Nullable
        final BlockPos blockPos;
        @Nullable
        final Entity entity;
        final double distance;

        private HitCandidate(Vec3d hitPos, @Nullable BlockPos blockPos, @Nullable Entity entity, double distance) {
            this.hitPos = hitPos;
            this.blockPos = blockPos;
            this.entity = entity;
            this.distance = distance;
        }

        private static HitCandidate block(Vec3d hitPos, BlockPos blockPos, double distance) {
            return new HitCandidate(hitPos, blockPos, null, distance);
        }

        private static HitCandidate entity(Vec3d hitPos, double distance, Entity entity) {
            return new HitCandidate(hitPos, null, entity, distance);
        }
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double maxDistance) {
        double sanitized = ContextReadUtils.sanitizeMaxDistance(maxDistance, this.maxDistance);
        if (Double.compare(this.maxDistance, sanitized) != 0) {
            this.maxDistance = sanitized;
            markDirty();
        }
    }

    public boolean isIncludeEntities() {
        return includeEntities;
    }

    public void setIncludeEntities(boolean includeEntities) {
        if (this.includeEntities != includeEntities) {
            this.includeEntities = includeEntities;
            markDirty();
        }
    }

    public boolean isIncludeFluids() {
        return includeFluids;
    }

    public void setIncludeFluids(boolean includeFluids) {
        if (this.includeFluids != includeFluids) {
            this.includeFluids = includeFluids;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("maxDistance", getMaxDistance());
        state.put("includeEntities", isIncludeEntities());
        state.put("includeFluids", isIncludeFluids());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map<?, ?> map) {
            if (map.containsKey("maxDistance")) {
                Object value = map.get("maxDistance");
                if (value instanceof Number number) {
                    setMaxDistance(number.doubleValue());
                }
            }
            if (map.containsKey("includeEntities")) {
                Object value = map.get("includeEntities");
                if (value instanceof Boolean bool) {
                    setIncludeEntities(bool);
                }
            }
            if (map.containsKey("includeFluids")) {
                Object value = map.get("includeFluids");
                if (value instanceof Boolean bool) {
                    setIncludeFluids(bool);
                }
            }
        }
    }
}
