package com.nodecraft.nodesystem.util;

import org.jspecify.annotations.NonNull;

/**
 * 表示Minecraft世界中的整数坐标
 * 专门用于方块位置，区别于浮点数的Vector3
 */
public record Coordinate(int x, int y, int z) {

    /**
     * 转换为Vector3（浮点数形式）
     */
    public Vector3 toVector3() {
        return new Vector3(x, y, z);
    }

    /**
     * 从Vector3创建Coordinate（四舍五入）
     */
    public static Coordinate fromVector3(Vector3 vector) {
        return new Coordinate(
                Math.round(vector.x()),
                Math.round(vector.y()),
                Math.round(vector.z())
        );
    }

    @Override
    public @NonNull String toString() {
        return String.format("(%d, %d, %d)", x, y, z);
    }
} 