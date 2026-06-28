package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Remove Entities 节点: 移除实体。
 */
@NodeInfo(
    id = "world.write.remove_entities",
    displayName = "Remove Entities",
    description = "移除实体",
    category = "world.write"
)
public class RemoveEntitiesNode extends BaseNode {

    // --- 节点属性 ---
    private boolean dropItems = false; // 移除实体时是否掉落物品
    private String description = "移除指定的实体或实体列表";

    // --- 输入端口 IDs ---
    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_ENTITY_LIST_ID = "input_entity_list";
    private static final String INPUT_ENTITY_UUID_ID = "input_entity_uuid";
    private static final String INPUT_DROP_ITEMS_ID = "input_drop_items";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_MAX_COUNT_ID = "input_max_count";

    // --- 输出端口 IDs ---
    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_FAILED_ENTITIES_ID = "output_failed_entities";

    // --- 构造函数 ---
    public RemoveEntitiesNode() {
        super(UUID.randomUUID(), "world.write.remove_entities");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", 
                "要移除的单个实体", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_ENTITY_LIST_ID, "Entity List", 
                "要移除的实体列表", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ENTITY_UUID_ID, "Entity UUID", 
                "要移除的实体UUID", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_DROP_ITEMS_ID, "Drop Items", 
                "是否掉落物品", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", 
                "要移除的实体类型", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_MAX_COUNT_ID, "Max Count", 
                "最大移除数量", NodeDataType.INTEGER, this));

        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_REMOVED_COUNT_ID, "Removed Count", 
                "成功移除的实体数量", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", 
                "是否成功移除所有实体", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_FAILED_ENTITIES_ID, "Failed Entities", 
                "移除失败的实体列表", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    // --- 核心逻辑 ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 默认输出值
        int removedCount = 0;
        boolean success = false;
        List<Object> failedEntities = new java.util.ArrayList<>();
        
        // 获取输入值
        Object entityObj = inputValues.get(INPUT_ENTITY_ID);
        Object entityListObj = inputValues.get(INPUT_ENTITY_LIST_ID);
        Object entityUuidObj = inputValues.get(INPUT_ENTITY_UUID_ID);
        Object entityTypeObj = inputValues.get(INPUT_ENTITY_TYPE_ID);
        
        // 获取布尔值参数
        boolean dropItemsValue = this.dropItems;
        Object dropItemsObj = inputValues.get(INPUT_DROP_ITEMS_ID);
        if (dropItemsObj instanceof Boolean) {
            dropItemsValue = (Boolean) dropItemsObj;
        }
        
        // 获取最大移除数量
        int maxCount = Integer.MAX_VALUE;
        Object maxCountObj = inputValues.get(INPUT_MAX_COUNT_ID);
        if (maxCountObj instanceof Number) {
            maxCount = ((Number) maxCountObj).intValue();
            if (maxCount <= 0) {
                maxCount = Integer.MAX_VALUE;
            }
        }
        
        // 检查执行上下文是否有效
        if (context != null && context.getWorld() != null) {
            // 创建要处理的实体列表
            List<Object> entitiesToRemove = new java.util.ArrayList<>();
            
            // 添加单个实体
            if (entityObj != null) {
                entitiesToRemove.add(entityObj);
            }
            
            // 添加实体列表
            if (entityListObj instanceof List) {
                List<?> entityList = (List<?>) entityListObj;
                entitiesToRemove.addAll(entityList);
            }
            
            // 通过UUID查找并添加实体
            if (entityUuidObj instanceof String) {
                String uuid = (String) entityUuidObj;
                try {
                    UUID entityUUID = UUID.fromString(uuid);
                    // 在实际实现中，查找并添加实体
                    // Entity entity = context.getWorld().getEntity(entityUUID);
                    // if (entity != null) {
                    //     entitiesToRemove.add(entity);
                    // }
                } catch (IllegalArgumentException e) {
                    com.nodecraft.core.NodeCraft.LOGGER.warn("Invalid UUID format: {}", uuid);
                }
            }
            
            // 如果指定了实体类型，查找所有该类型的实体
            if (entityTypeObj != null && (entitiesToRemove.isEmpty() || maxCount > entitiesToRemove.size())) {
                String entityType = entityTypeObj.toString();
                // 在实际实现中，获取所有指定类型的实体
                // 例如：List<Entity> typedEntities = context.getWorld().getEntities(new EntityType.Builder().create(entityType));
                // int remainingSlots = maxCount - entitiesToRemove.size();
                // entitiesToRemove.addAll(typedEntities.subList(0, Math.min(typedEntities.size(), remainingSlots)));
            }
            
            // 限制移除数量
            if (entitiesToRemove.size() > maxCount) {
                entitiesToRemove = entitiesToRemove.subList(0, maxCount);
            }
            
            // 移除实体
            for (Object entity : entitiesToRemove) {
                try {
                    // 在实际实现中移除实体
                    /*
                    if (entity instanceof Entity) {
                        Entity minecraftEntity = (Entity) entity;
                        
                        // 设置是否掉落物品
                        if (minecraftEntity instanceof LivingEntity) {
                            ((LivingEntity) minecraftEntity).setDropsItems(dropItemsValue);
                        }
                        
                        // 移除实体
                        minecraftEntity.remove();
                        removedCount++;
                    }
                    */
                    
                    // 模拟成功移除
                    removedCount++;
                } catch (Exception e) {
                    // 记录失败的实体
                    failedEntities.add(entity);
                    com.nodecraft.core.NodeCraft.LOGGER.warn("Error removing entity", e);
                }
            }
            
            // 检查是否全部成功移除
            success = (removedCount == entitiesToRemove.size());
        }
        
        // 设置输出值
        outputValues.put(OUTPUT_REMOVED_COUNT_ID, removedCount);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_FAILED_ENTITIES_ID, failedEntities);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isDropItems() {
        return dropItems;
    }
    
    public void setDropItems(boolean dropItems) {
        this.dropItems = dropItems;
        markDirty();
    }
} 
