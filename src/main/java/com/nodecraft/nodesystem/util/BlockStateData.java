package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 封装Minecraft方块状态数据
 * 例如: {facing="north", waterlogged="false", lit="true"}
 */
public class BlockStateData extends HashMap<String, String> {
    
    public BlockStateData() {
        super();
    }
    
    public BlockStateData(Map<String, String> stateMap) {
        super(stateMap);
    }
    
    /**
     * 获取方块状态属性
     * @param property 属性名
     * @param defaultValue 默认值
     * @return 属性值
     */
    public String getProperty(String property, String defaultValue) {
        return getOrDefault(property, defaultValue);
    }
    
    /**
     * 设置方块状态属性
     * @param property 属性名
     * @param value 属性值
     */
    public void setProperty(String property, String value) {
        put(property, value);
    }
    
    /**
     * 获取布尔类型的属性
     * @param property 属性名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public boolean getBooleanProperty(String property, boolean defaultValue) {
        String value = get(property);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value);
    }
    
    /**
     * 设置布尔类型的属性
     * @param property 属性名
     * @param value 布尔值
     */
    public void setBooleanProperty(String property, boolean value) {
        put(property, String.valueOf(value));
    }
    
    /**
     * 获取整数类型的属性
     * @param property 属性名
     * @param defaultValue 默认值
     * @return 整数值
     */
    public int getIntProperty(String property, int defaultValue) {
        String value = get(property);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 设置整数类型的属性
     * @param property 属性名
     * @param value 整数值
     */
    public void setIntProperty(String property, int value) {
        put(property, String.valueOf(value));
    }
    
    /**
     * 检查是否有指定属性
     * @param property 属性名
     * @return 是否存在
     */
    public boolean hasProperty(String property) {
        return containsKey(property);
    }
    
    /**
     * 创建状态数据的副本
     * @return 新的BlockStateData实例
     */
    public BlockStateData copy() {
        return new BlockStateData(this);
    }

    /**
     * Merges {@code override} into a copy of {@code base}; override keys win.
     * Identity keys ({@code blockId}, {@code id}) are never copied from either side.
     */
    public static BlockStateData merge(@Nullable BlockStateData base, @Nullable BlockStateData override) {
        BlockStateData merged = base != null ? base.copy() : new BlockStateData();
        stripIdentityKeys(merged);
        if (override != null) {
            for (Map.Entry<String, String> entry : override.entrySet()) {
                if (!isIdentityKey(entry.getKey())) {
                    merged.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        return merged;
    }

    public static void stripIdentityKeys(BlockStateData state) {
        if (state == null) {
            return;
        }
        state.remove("blockId");
        state.remove("id");
    }

    private static boolean isIdentityKey(String key) {
        return "blockId".equals(key) || "id".equals(key);
    }
    
    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
} 