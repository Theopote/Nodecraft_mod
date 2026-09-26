package com.nodecraft.nodesystem.util;

/**
 * 表示三维向量或坐标的类
 */
public record Vector3(float x, float y, float z) {
    /**
     * 创建一个新的三维向量
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     */
    public Vector3 {
    }

    /**
     * 创建向量的副本
     *
     * @param other 要复制的向量
     */
    public Vector3(Vector3 other) {
        this(other.x, other.y, other.z);
    }

    /**
     * 获取X坐标
     *
     * @return X坐标值
     */
    @Override
    public float x() {
        return x;
    }

    /**
     * 获取Y坐标
     *
     * @return Y坐标值
     */
    @Override
    public float y() {
        return y;
    }

    /**
     * 获取Z坐标
     *
     * @return Z坐标值
     */
    @Override
    public float z() {
        return z;
    }

    /**
     * 创建一个带有新X坐标的向量
     *
     * @param x 新的X坐标
     * @return 新的向量实例
     */
    public Vector3 withX(float x) {
        return new Vector3(x, this.y, this.z);
    }

    /**
     * 创建一个带有新Y坐标的向量
     *
     * @param y 新的Y坐标
     * @return 新的向量实例
     */
    public Vector3 withY(float y) {
        return new Vector3(this.x, y, this.z);
    }

    /**
     * 创建一个带有新Z坐标的向量
     *
     * @param z 新的Z坐标
     * @return 新的向量实例
     */
    public Vector3 withZ(float z) {
        return new Vector3(this.x, this.y, z);
    }

    /**
     * 向量加法
     *
     * @param other 要添加的向量
     * @return 两个向量相加的结果
     */
    public Vector3 add(Vector3 other) {
        return new Vector3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    /**
     * 向量减法
     *
     * @param other 要减去的向量
     * @return 两个向量相减的结果
     */
    public Vector3 subtract(Vector3 other) {
        return new Vector3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    /**
     * 向量缩放
     *
     * @param scale 缩放因子
     * @return 缩放后的向量
     */
    public Vector3 scale(float scale) {
        return new Vector3(this.x * scale, this.y * scale, this.z * scale);
    }

    /**
     * 计算向量长度
     *
     * @return 向量的长度
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * 计算两点之间的距离
     *
     * @param other 另一个点
     * @return 与另一个点的距离
     */
    public float distanceTo(Vector3 other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 标准化向量
     *
     * @return 单位长度的向量
     */
    public Vector3 normalize() {
        float len = length();
        if (len < 1e-6f) {  // 避免除以0
            return new Vector3(0, 0, 0);
        }
        return new Vector3(x / len, y / len, z / len);
    }

    /**
     * 计算点积
     *
     * @param other 另一个向量
     * @return 两向量的点积
     */
    public float dot(Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /**
     * 计算叉积
     *
     * @param other 另一个向量
     * @return 叉积结果的向量
     */
    public Vector3 cross(Vector3 other) {
        return new Vector3(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
        );
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Vector3 other = (Vector3) obj;
        return Math.abs(x - other.x) < 1e-6f &&
                Math.abs(y - other.y) < 1e-6f &&
                Math.abs(z - other.z) < 1e-6f;
    }

}