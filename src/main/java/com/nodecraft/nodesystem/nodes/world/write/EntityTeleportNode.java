package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Entity Teleport 节点: 将实体传送到指定坐标
 */
@NodeInfo(
    id = "world.write.entity_teleport",
    displayName = "Teleport Entity",
    description = "传送实体",
    category = "world.write"
)
public class EntityTeleportNode extends BaseNode {

    // --- 节点属性 ---
    private boolean preserveRotation = true; // 是否保留实体的原有旋转
    private boolean resetFallDistance = true; // 是否重置坠落距离
    private boolean allowAcrossDimension = false; // 是否允许跨维度传送
    private String description = "将实体传送到指定位置";

    // --- 输入端口 IDs ---
    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_ENTITY_LIST_ID = "input_entity_list";
    private static final String INPUT_DESTINATION_ID = "input_destination";
    private static final String INPUT_PRESERVE_ROTATION_ID = "input_preserve_rotation";
    private static final String INPUT_ROTATION_YAW_ID = "input_rotation_yaw";
    private static final String INPUT_ROTATION_PITCH_ID = "input_rotation_pitch";
    private static final String INPUT_DIMENSION_ID = "input_dimension";
    private static final String INPUT_RESET_FALL_DISTANCE_ID = "input_reset_fall_distance";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_ALL_SUCCESS_ID = "output_all_success";
    private static final String OUTPUT_TELEPORTED_ENTITIES_ID = "output_teleported_entities";

    // --- 构造函数 ---
    public EntityTeleportNode() {
        super(UUID.randomUUID(), "world.write.entity_teleport");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", 
                "要传送的单个实体", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_ENTITY_LIST_ID, "Entity List", 
                "要传送的实体列表", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_DESTINATION_ID, "Destination", 
                "目标位置", NodeDataType.POSITION, this));
        addInputPort(new BasePort(INPUT_PRESERVE_ROTATION_ID, "Preserve Rotation", 
                "是否保留原有旋转", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ROTATION_YAW_ID, "Yaw", 
                "水平旋转角度", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_ROTATION_PITCH_ID, "Pitch", 
                "垂直旋转角度", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_DIMENSION_ID, "Dimension", 
                "目标维度ID", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RESET_FALL_DISTANCE_ID, "Reset Fall Distance", 
                "是否重置坠落距离", NodeDataType.BOOLEAN, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", 
                "成功传送的实体数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", 
                "尝试传送的实体总数", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SUCCESS_ID, "All Success", 
                "是否所有实体都成功传送", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_TELEPORTED_ENTITIES_ID, "Teleported Entities", 
                "成功传送的实体列表", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int successCount = 0;
        int totalCount = 0;
        boolean allSuccess = true;
        List<Object> teleportedEntities = new java.util.ArrayList<>();
        
        // 获取输入值
        Object entityObj = inputValues.get(INPUT_ENTITY_ID);
        Object entityListObj = inputValues.get(INPUT_ENTITY_LIST_ID);
        Object destinationObj = inputValues.get(INPUT_DESTINATION_ID);
        Object dimensionObj = inputValues.get(INPUT_DIMENSION_ID);
        
        // 获取布尔值参数
        boolean preserveRotationValue = this.preserveRotation;
        Object preserveRotationObj = inputValues.get(INPUT_PRESERVE_ROTATION_ID);
        if (preserveRotationObj instanceof Boolean) {
            preserveRotationValue = (Boolean) preserveRotationObj;
        }
        
        boolean resetFallDistanceValue = this.resetFallDistance;
        Object resetFallDistanceObj = inputValues.get(INPUT_RESET_FALL_DISTANCE_ID);
        if (resetFallDistanceObj instanceof Boolean) {
            resetFallDistanceValue = (Boolean) resetFallDistanceObj;
        }
        
        // 获取旋转角度
        float yaw = 0.0f;
        Object yawObj = inputValues.get(INPUT_ROTATION_YAW_ID);
        if (yawObj instanceof Number) {
            yaw = ((Number) yawObj).floatValue();
        }
        
        float pitch = 0.0f;
        Object pitchObj = inputValues.get(INPUT_ROTATION_PITCH_ID);
        if (pitchObj instanceof Number) {
            pitch = ((Number) pitchObj).floatValue();
        }
        
        // 检查执行上下文和必要输入是否有效
        if (context != null && context.getWorld() != null && destinationObj != null) {
            // 创建要处理的实体列表
            List<Object> entitiesToTeleport = new java.util.ArrayList<>();
            
            // 添加单个实体
            if (entityObj != null) {
                entitiesToTeleport.add(entityObj);
            }
            
            // 添加实体列表
            if (entityListObj instanceof List) {
                List<?> entityList = (List<?>) entityListObj;
                entitiesToTeleport.addAll(entityList);
            }
            
            // 处理目标位置
            double x = 0.0, y = 0.0, z = 0.0;
            
            if (destinationObj instanceof Vector3d) {
                Vector3d pos = (Vector3d) destinationObj;
                x = pos.x;
                y = pos.y;
                z = pos.z;
            } else if (destinationObj instanceof BlockPos) {
                BlockPos pos = (BlockPos) destinationObj;
                x = pos.getX() + 0.5; // 中心对齐
                y = pos.getY();
                z = pos.getZ() + 0.5; // 中心对齐
            }
            
            // 处理维度
            String dimension = null;
            if (dimensionObj instanceof String) {
                dimension = (String) dimensionObj;
            }
            
            // 遍历实体进行传送
            for (Object entity : entitiesToTeleport) {
                totalCount++;
                
                try {
                    // 在实际实现中传送实体
                    /*
                    if (entity instanceof Entity) {
                        Entity minecraftEntity = (Entity) entity;
                        
                        // 如果需要跨维度传送
                        if (dimension != null && !dimension.isEmpty() && allowAcrossDimension) {
                            RegistryKey<World> targetDimension = RegistryKey.getOrCreateKey(
                                Registry.WORLD_KEY, new ResourceLocation(dimension));
                            
                            if (minecraftEntity.world.getRegistryKey() != targetDimension) {
                                // 处理跨维度传送
                                ServerWorld targetWorld = minecraftEntity.getServer().getWorld(targetDimension);
                                if (targetWorld != null && minecraftEntity instanceof ServerPlayerEntity) {
                                    ServerPlayerEntity player = (ServerPlayerEntity) minecraftEntity;
                                    player.teleport(targetWorld, x, y, z, 
                                                   preserveRotationValue ? player.rotationYaw : yaw, 
                                                   preserveRotationValue ? player.rotationPitch : pitch);
                                    if (resetFallDistanceValue) {
                                        player.fallDistance = 0.0f;
                                    }
                                    successCount++;
                                    teleportedEntities.add(minecraftEntity);
                                    continue;
                                }
                            }
                        }
                        
                        // 同维度传送
                        boolean teleportSuccess = minecraftEntity.teleport(x, y, z);
                        
                        // 设置旋转
                        if (!preserveRotationValue) {
                            minecraftEntity.rotationYaw = yaw;
                            minecraftEntity.rotationPitch = pitch;
                            minecraftEntity.setRotationYawHead(yaw);
                        }
                        
                        // 重置坠落距离
                        if (resetFallDistanceValue) {
                            minecraftEntity.fallDistance = 0.0f;
                        }
                        
                        if (teleportSuccess) {
                            successCount++;
                            teleportedEntities.add(minecraftEntity);
                        } else {
                            allSuccess = false;
                        }
                    }
                    */
                    
                    // 模拟成功传送
                    successCount++;
                    teleportedEntities.add(entity);
                } catch (Exception e) {
                    // 记录错误
                    allSuccess = false;
                    com.nodecraft.core.NodeCraft.LOGGER.warn("Error teleporting entity", e);
                }
            }
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_ALL_SUCCESS_ID, allSuccess);
        outputValues.put(OUTPUT_TELEPORTED_ENTITIES_ID, teleportedEntities);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isPreserveRotation() {
        return preserveRotation;
    }
    
    public void setPreserveRotation(boolean preserveRotation) {
        this.preserveRotation = preserveRotation;
        markDirty();
    }
    
    public boolean isResetFallDistance() {
        return resetFallDistance;
    }
    
    public void setResetFallDistance(boolean resetFallDistance) {
        this.resetFallDistance = resetFallDistance;
        markDirty();
    }
    
    public boolean isAllowAcrossDimension() {
        return allowAcrossDimension;
    }
    
    public void setAllowAcrossDimension(boolean allowAcrossDimension) {
        this.allowAcrossDimension = allowAcrossDimension;
        markDirty();
    }
} 
