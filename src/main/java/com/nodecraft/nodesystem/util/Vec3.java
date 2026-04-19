package com.nodecraft.nodesystem.util;

/**
 * 表示三维向量的简单类
 */
public class Vec3 {
    private double x;
    private double y;
    private double z;
    
    public Vec3() {
        this(0, 0, 0);
    }
    
    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public double getX() {
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
    }
    
    public double getZ() {
        return z;
    }
    
    public void setZ(double z) {
        this.z = z;
    }
    
    public Vec3 add(Vec3 other) {
        return new Vec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }
    
    public Vec3 subtract(Vec3 other) {
        return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }
    
    public Vec3 multiply(double scalar) {
        return new Vec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }
    
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
    
    public Vec3 normalize() {
        double len = length();
        if (len > 0) {
            return new Vec3(x / len, y / len, z / len);
        }
        return new Vec3();
    }
    
    public double dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }
    
    public Vec3 cross(Vec3 other) {
        return new Vec3(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }
    
    @Override
    public String toString() {
        return String.format("Vec3(%.2f, %.2f, %.2f)", x, y, z);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Vec3 other = (Vec3) obj;
        return Double.compare(other.x, x) == 0 &&
               Double.compare(other.y, y) == 0 &&
               Double.compare(other.z, z) == 0;
    }
    
    @Override
    public int hashCode() {
        int result = 17;
        long xBits = Double.doubleToLongBits(x);
        long yBits = Double.doubleToLongBits(y);
        long zBits = Double.doubleToLongBits(z);
        result = 31 * result + Long.hashCode(xBits);
        result = 31 * result + Long.hashCode(yBits);
        result = 31 * result + Long.hashCode(zBits);
        return result;
    }
} 