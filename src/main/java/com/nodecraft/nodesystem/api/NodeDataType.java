package com.nodecraft.nodesystem.api;

import com.nodecraft.nodesystem.datatypes.*;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Curve;

import net.minecraft.util.math.BlockPos;
// import net.minecraft.util.math.Vec3d; // Using JOML Vector3d instead
import org.joml.Vector3d;

/**
 * 节点数据类型枚举，定义了节点系统中支持的数据类型
 */
public enum NodeDataType {
    ANY("any", "任意类型", Object.class), // Represents any type, use with caution
    STRING("string", "字符串", String.class),
    INTEGER("integer", "整数", Integer.class),
    DOUBLE("double", "双精度浮点数", Double.class), // Represents floating-point numbers (float/double)
    BOOLEAN("boolean", "布尔值", Boolean.class),

    // 基础数据类型
    FLOAT("float", "浮点数", Float.class),
    
    // Geometry types
    POINT("point", "点", PointData.class),
    VECTOR("vector", "向量", Vector3d.class),
    COORDINATE("coordinate", "坐标", BlockPos.class), // 整数坐标
    POSITION("position", "位置", Vector3d.class),     // 浮点坐标
    PLANE("plane", "平面", PlaneData.class),
    BOUNDING_BOX("bounding_box", "包围盒", BoundingBoxData.class),
    SPHERE("sphere", "球体", SphereData.class),
    LINE("line", "线段", LineData.class),
    POLYLINE("polyline", "多段线", PolylineData.class),
    CURVE("curve", "曲线", Curve.class),
    REGION("region", "区域", RegionData.class), // 表示一个空间区域
    
    // Color type
    COLOR("color", "颜色", ColorData.class),

    // Minecraft specific types
    BLOCK_POS("block_pos", "方块坐标", BlockPos.class),
    BLOCK_LIST("block_list", "方块列表", BlockPosList.class),
    BLOCK_INFO("block_info", "方块信息", Object.class), // 包含方块状态和NBT的完整信息
    BLOCK_STATE_DATA("block_state_data", "方块状态数据", com.nodecraft.nodesystem.util.BlockStateData.class), // 方块状态键值对
    BLOCK_TYPE("block_type", "方块类型", String.class), // 方块ID
    ITEM_TYPE("item_type", "物品类型", String.class),   // 物品ID
    ITEM_STACK("item_stack", "物品堆", Object.class),   // 表示一个带NBT和数量的物品
    ENTITY_TYPE("entity_type", "实体类型", String.class), // 实体ID
    ENTITY_INFO("entity_info", "实体信息", Object.class), // 包含实体类型和NBT的完整信息
    MINECRAFT_ENTITY("minecraft_entity", "游戏实体", Object.class), // 表示游戏中的实体实例
    MINECRAFT_BLOCK("minecraft_block", "游戏方块", Object.class),   // 表示游戏中实际存在的方块
    BIOME("biome", "生物群系", String.class),     // 生物群系ID
    WORLD("world", "世界", Object.class), // 游戏世界
    DIMENSION("dimension", "维度", Object.class), // 游戏维度
    PLAYER("player", "玩家", Object.class), // 玩家对象
    SOUND_EVENT("sound_event", "声音事件", String.class), // 声音事件ID
    EFFECT_TYPE("effect_type", "效果类型", String.class), // 状态效果ID
    
    // NBT相关类型
    NBT_COMPOUND("nbt_compound", "NBT复合标签", Object.class), // NBT复合标签
    NBT_LIST("nbt_list", "NBT列表标签", Object.class),        // NBT列表标签
    NBT("nbt", "NBT数据", Object.class),                      // 通用NBT数据，可以是复合标签或列表标签
    
    // Flora & L-Systems 相关类型
    L_SYSTEM_RULE("l_system_rule", "L-系统规则", LSystemRule.class), // L-系统生产规则
    L_SYSTEM_RULE_LIST("l_system_rule_list", "L-系统规则列表", java.util.List.class), // L-系统规则列表
    PLANT_STRUCTURE("plant_structure", "植物结构", PlantStructure.class), // 植物三维结构
    PLANT_BLOCK("plant_block", "植物方块", PlantStructure.PlantBlock.class), // 植物方块信息
    PLANT_BLOCK_LIST("plant_block_list", "植物方块列表", java.util.List.class), // 植物方块列表
    TREE_TYPE("tree_type", "树木类型", String.class), // 树木类型枚举
    BUSH_TYPE("bush_type", "灌木类型", String.class), // 灌木类型枚举
    FLOWER_TYPE("flower_type", "花朵类型", String.class), // 花朵类型枚举
    PLANT_PART("plant_part", "植物部位", String.class), // 植物部位枚举(Trunk, Branch, Leaf等)
    
    // 文件相关类型
    FILE_PATH("file_path", "文件路径", String.class),
    
    // 列表类型
    LIST("list", "列表", java.util.List.class), // 通用列表
    COORDINATE_LIST("coordinate_list", "坐标列表", java.util.List.class), // 坐标列表
    BLOCK_INFO_LIST("block_info_list", "方块信息列表", java.util.List.class), // 方块信息列表
    BLOCK_PLACEMENT_LIST("block_placement_list", "方块放置列表", java.util.List.class), // List<BlockPlacementData> 按位置分配方块
    VECTOR_LIST("vector_list", "向量列表", java.util.List.class), // 向量列表
    REGION_LIST("region_list", "区域列表", java.util.List.class), // 区域列表
    PLANT_STRUCTURE_LIST("plant_structure_list", "植物结构列表", java.util.List.class); // 植物结构列表
    
    private final String id;
    private final String displayName;
    private final Class<?> javaClass;

    NodeDataType(String id, String displayName, Class<?> javaClass) {
        this.id = id;
        this.displayName = displayName;
        this.javaClass = javaClass;
    }

    /**
     * 获取数据类型ID
     * @return 类型ID
     */
    public String getId() {
        return id;
    }

    /**
     * 获取数据类型显示名称
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取关联的Java类
     * @return Java类
     */
    public Class<?> getJavaClass() {
        return javaClass;
    }

    /**
     * 检查值是否与此数据类型兼容
     * @param value 要检查的值
     * @return 如果兼容返回true，否则返回false
     */
    public boolean isCompatible(Object value) {
        if (value == null) {
            return true; // Null is generally compatible
        }

        if (this == ANY) {
            return true;
        }

        // 列表类型的特殊处理
        if (this == LIST && value instanceof java.util.List) {
            return true;
        }
        
        if (this == COORDINATE_LIST && value instanceof java.util.List) {
            // 理想情况下应该检查列表中的每个元素
            return true; 
        }
        
        if (this == L_SYSTEM_RULE_LIST && value instanceof java.util.List) {
            return true;
        }
        
        if (this == PLANT_BLOCK_LIST && value instanceof java.util.List) {
            return true;
        }
        
        if (this == PLANT_STRUCTURE_LIST && value instanceof java.util.List) {
            return true;
        }
        if (this == BLOCK_PLACEMENT_LIST && value instanceof java.util.List) {
            return true;
        }

        // 数字类型的特殊处理
        if (this == DOUBLE && value instanceof Number) {
             return true;
        }
        if (this == INTEGER && value instanceof Integer) {
             return true;
        }
        if (this == FLOAT && value instanceof Float) {
             return true;
        }
        
        // NBT类型的特殊处理
        if (this == NBT) {
            // 检查值是否为任何NBT类型
            return this == NBT_COMPOUND || this == NBT_LIST ||
                   value.getClass().getSimpleName().contains("Tag") ||
                   value.getClass().getSimpleName().contains("NBT");
        }

        return javaClass != null && javaClass.isInstance(value);
    }

    /**
     * 检查「输出端口类型」是否可以连接到「输入端口类型」。
     * 用于连线时类型校验：不匹配时连线应显示为红色警告。
     *
     * @param outputType 输出端口的数据类型
     * @param inputType  输入端口的数据类型
     * @return 若输出可以安全接到该输入返回 true，否则返回 false
     */
    public static boolean isConnectableTo(NodeDataType outputType, NodeDataType inputType) {
        if (outputType == null) outputType = ANY;
        if (inputType == null) inputType = ANY;
        if (inputType == ANY) return true;
        if (outputType == ANY) return true;
        if (outputType == inputType) return true;
        // 数字可放宽：整数/浮点可接到双精度
        if (inputType == DOUBLE && (outputType == INTEGER || outputType == FLOAT)) return true;
        if (inputType == FLOAT && outputType == INTEGER) return true;
        return false;
    }

    /**
     * 根据ID查找数据类型
     * @param id 类型ID
     * @return 找到的数据类型，如果未找到则返回ANY
     */
    public static NodeDataType fromId(String id) {
        if (id == null) return ANY;
        for (NodeDataType type : values()) {
            if (id.equals(type.id)) {
                return type;
            }
        }
        System.err.println("Warning: NodeDataType not found for id: " + id + ". Returning ANY.");
        return ANY;
    }
} 