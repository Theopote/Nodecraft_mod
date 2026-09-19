package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.raycast",
    displayName = "Raycast",
    description = "Casts a ray in world space and returns nearest block/entity hit information.",
    category = "world.query",
    order = 8
)
public class RaycastNode extends BaseNode {

    private static final String INPUT_ORIGIN_ID = "input_origin";
    private static final String INPUT_DIRECTION_ID = "input_direction";
    private static final String INPUT_MAX_DISTANCE_ID = "input_max_distance";
    private static final String INPUT_INCLUDE_FLUIDS_ID = "input_include_fluids";
    private static final String INPUT_CHECK_ENTITIES_ID = "input_check_entities";
    private static final String INPUT_ENTITY_RADIUS_ID = "input_entity_radius";

    private static final String OUTPUT_HIT_ID = "output_hit";
    private static final String OUTPUT_HIT_TYPE_ID = "output_hit_type";
    private static final String OUTPUT_HIT_POS_ID = "output_hit_pos";
    private static final String OUTPUT_HIT_BLOCK_POS_ID = "output_hit_block_pos";
    private static final String OUTPUT_HIT_NORMAL_ID = "output_hit_normal";
    private static final String OUTPUT_HIT_ENTITY_ID = "output_hit_entity";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RaycastNode() {
        super(UUID.randomUUID(), "world.query.raycast");

        addInputPort(new BasePort(INPUT_ORIGIN_ID, "Origin", "Ray origin", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_DIRECTION_ID, "Direction", "Ray direction vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Maximum ray distance", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INCLUDE_FLUIDS_ID, "Include Fluids", "Whether fluids are considered by block hit test", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_CHECK_ENTITIES_ID, "Check Entities", "Whether to include entity hit testing", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ENTITY_RADIUS_ID, "Entity Radius", "Extra expansion radius for entity hit boxes", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HIT_ID, "Hit", "Whether any hit was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_TYPE_ID, "Hit Type", "none/block/entity", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HIT_POS_ID, "Hit Position", "World-space hit point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HIT_BLOCK_POS_ID, "Hit Block Position", "Block position when block hit", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_HIT_NORMAL_ID, "Hit Normal", "Hit surface normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HIT_ENTITY_ID, "Hit Entity", "Entity object when entity hit", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Distance from origin to hit", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether ray inputs and execution context were valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the raycast is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Casts a ray in world space and returns nearest block/entity hit information.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            writeNoHit(false, "Execution context or world is missing.");
            return;
        }

        Vector3d origin = WorldQueryPointResolver.resolveVector(inputValues.get(INPUT_ORIGIN_ID));
        Vector3d direction = WorldQueryPointResolver.resolveVector(inputValues.get(INPUT_DIRECTION_ID));
        if (origin == null || direction == null || direction.lengthSquared() <= 1.0e-12d) {
            writeNoHit(false, "Origin and non-zero Direction inputs are required.");
            return;
        }

        double maxDistance = inputValues.get(INPUT_MAX_DISTANCE_ID) instanceof Number n ? Math.max(1.0e-3d, n.doubleValue()) : 16.0d;
        boolean includeFluids = inputValues.get(INPUT_INCLUDE_FLUIDS_ID) instanceof Boolean b && b;
        boolean checkEntities = inputValues.get(INPUT_CHECK_ENTITIES_ID) instanceof Boolean b && b;
        double entityRadius = inputValues.get(INPUT_ENTITY_RADIUS_ID) instanceof Number n ? Math.max(0.0d, n.doubleValue()) : 0.15d;

        Vector3d dir = new Vector3d(direction).normalize();
        Vec3d start = new Vec3d(origin.x, origin.y, origin.z);
        Vec3d end = new Vec3d(
            origin.x + dir.x * maxDistance,
            origin.y + dir.y * maxDistance,
            origin.z + dir.z * maxDistance
        );

        Entity sourceEntity = context.getPlayer();
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
            Vec3d hitPos = blockHit.getPos();
            double d = start.distanceTo(hitPos);
            Direction side = blockHit.getSide();
            Vector3d normal = side != null
                ? new Vector3d(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ())
                : new Vector3d();
            blockCandidate = HitCandidate.block(hitPos, blockHit.getBlockPos(), normal, d);
        }

        HitCandidate entityCandidate = null;
        if (checkEntities) {
            entityCandidate = raycastEntities(context, sourceEntity, start, end, maxDistance, entityRadius);
        }

        HitCandidate best = chooseBest(blockCandidate, entityCandidate);
        if (best == null) {
            writeNoHit(true, "");
            return;
        }

        outputValues.put(OUTPUT_HIT_ID, true);
        outputValues.put(OUTPUT_HIT_TYPE_ID, best.type);
        outputValues.put(OUTPUT_HIT_POS_ID, new Vector3d(best.hitPos.x, best.hitPos.y, best.hitPos.z));
        outputValues.put(OUTPUT_HIT_BLOCK_POS_ID, best.blockPos);
        outputValues.put(OUTPUT_HIT_NORMAL_ID, best.normal != null ? new Vector3d(best.normal) : new Vector3d());
        outputValues.put(OUTPUT_HIT_ENTITY_ID, best.entity);
        outputValues.put(OUTPUT_DISTANCE_ID, best.distance);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private HitCandidate raycastEntities(ExecutionContext context,
                                         @Nullable Entity sourceEntity,
                                         Vec3d start,
                                         Vec3d end,
                                         double maxDistance,
                                         double extraRadius) {
        Box sweep = new Box(start, end).expand(extraRadius + 1.0d);
        List<Entity> entities = new ArrayList<>(context.getWorld().getOtherEntities(sourceEntity, sweep));
        if (sourceEntity instanceof PlayerEntity) {
            entities.remove(sourceEntity);
        }

        HitCandidate best = null;
        for (Entity entity : entities) {
            if (!isRaycastableEntity(entity)) {
                continue;
            }
            Box box = entity.getBoundingBox().expand(extraRadius);
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            Vec3d hitPos = box.raycast(start, end).orElse(null);
            if (hitPos == null) {
                continue;
            }
            double distance = start.distanceTo(hitPos);
            if (distance > maxDistance) {
                continue;
            }
            Vector3d normal = estimateNormal(box, hitPos);
            if (best == null || distance < best.distance) {
                best = HitCandidate.entity(hitPos, normal, distance, entity);
            }
        }
        return best;
    }

    private boolean isRaycastableEntity(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        return !(entity instanceof PlayerEntity player && player.isSpectator());
    }

    private Vector3d estimateNormal(Box box, Vec3d hitPos) {
        double eps = 1.0e-4d;
        if (Math.abs(hitPos.x - box.minX) <= eps) return new Vector3d(-1.0d, 0.0d, 0.0d);
        if (Math.abs(hitPos.x - box.maxX) <= eps) return new Vector3d(1.0d, 0.0d, 0.0d);
        if (Math.abs(hitPos.y - box.minY) <= eps) return new Vector3d(0.0d, -1.0d, 0.0d);
        if (Math.abs(hitPos.y - box.maxY) <= eps) return new Vector3d(0.0d, 1.0d, 0.0d);
        if (Math.abs(hitPos.z - box.minZ) <= eps) return new Vector3d(0.0d, 0.0d, -1.0d);
        if (Math.abs(hitPos.z - box.maxZ) <= eps) return new Vector3d(0.0d, 0.0d, 1.0d);
        return new Vector3d();
    }

    private HitCandidate chooseBest(@Nullable HitCandidate a, @Nullable HitCandidate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.distance <= b.distance ? a : b;
    }

    private void writeNoHit(boolean valid, String error) {
        outputValues.put(OUTPUT_HIT_ID, false);
        outputValues.put(OUTPUT_HIT_TYPE_ID, "none");
        outputValues.put(OUTPUT_HIT_POS_ID, new Vector3d());
        outputValues.put(OUTPUT_HIT_BLOCK_POS_ID, BlockPos.ORIGIN);
        outputValues.put(OUTPUT_HIT_NORMAL_ID, new Vector3d());
        outputValues.put(OUTPUT_HIT_ENTITY_ID, null);
        outputValues.put(OUTPUT_DISTANCE_ID, -1.0d);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private static final class HitCandidate {
        final String type;
        final Vec3d hitPos;
        final BlockPos blockPos;
        final Vector3d normal;
        final double distance;
        final Entity entity;

        private HitCandidate(String type, Vec3d hitPos, @Nullable BlockPos blockPos, @Nullable Vector3d normal, double distance, @Nullable Entity entity) {
            this.type = type;
            this.hitPos = hitPos;
            this.blockPos = blockPos;
            this.normal = normal;
            this.distance = distance;
            this.entity = entity;
        }

        static HitCandidate block(Vec3d hitPos, BlockPos blockPos, Vector3d normal, double distance) {
            return new HitCandidate("block", hitPos, blockPos, normal, distance, null);
        }

        static HitCandidate entity(Vec3d hitPos, Vector3d normal, double distance, Entity entity) {
            return new HitCandidate("entity", hitPos, null, normal, distance, entity);
        }
    }
}
