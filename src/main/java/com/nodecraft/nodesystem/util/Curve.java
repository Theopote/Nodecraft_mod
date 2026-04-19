package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 曲线类，表示一系列连续的点构成的路径
 */
public class Curve {
    
    /**
     * 曲线类型枚举
     */
    public enum CurveType {
        /**
         * 线性曲线（点到点的直线）
         */
        LINEAR,
        
        /**
         * 贝塞尔曲线
         */
        BEZIER,
        
        /**
         * 样条曲线
         */
        SPLINE
    }
    
    private final List<Vec3d> controlPoints;
    private final CurveType curveType;
    private final int resolution;
    
    /**
     * 创建一个新的曲线
     * @param curveType 曲线类型
     * @param resolution 分辨率（采样点数量）
     */
    public Curve(CurveType curveType, int resolution) {
        this.controlPoints = new ArrayList<>();
        this.curveType = curveType;
        this.resolution = Math.max(2, resolution);
    }
    
    /**
     * 添加控制点
     * @param point 控制点
     * @return 此曲线
     */
    public Curve addControlPoint(Vec3d point) {
        controlPoints.add(point);
        return this;
    }
    
    /**
     * 获取控制点列表
     * @return 控制点列表
     */
    public List<Vec3d> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    /**
     * 获取曲线类型
     * @return 曲线类型
     */
    public CurveType getCurveType() {
        return curveType;
    }
    
    /**
     * 获取曲线分辨率
     * @return 分辨率
     */
    public int getResolution() {
        return resolution;
    }
    
    /**
     * 获取点的数量
     * @return 控制点数量
     */
    public int size() {
        return controlPoints.size();
    }
    
    /**
     * 获取曲线上的采样点
     * @return 采样点列表
     */
    public List<Vec3d> getSamplePoints() {
        List<Vec3d> samples = new ArrayList<>();
        
        if (controlPoints.size() < 2) {
            return samples;
        }
        
        switch (curveType) {
            case LINEAR:
                return getLinearSamples();
            case BEZIER:
                return getBezierSamples();
            case SPLINE:
                return getSplineSamples();
            default:
                return samples;
        }
    }
    
    /**
     * 获取线性采样点
     * @return 线性采样点列表
     */
    private List<Vec3d> getLinearSamples() {
        List<Vec3d> samples = new ArrayList<>();
        
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Vec3d start = controlPoints.get(i);
            Vec3d end = controlPoints.get(i + 1);
            
            for (int j = 0; j < resolution; j++) {
                float t = j / (float) (resolution - 1);
                samples.add(lerpVec3d(start, end, t));
            }
        }
        
        return samples;
    }
    
    /**
     * 获取贝塞尔曲线采样点
     * @return 贝塞尔曲线采样点列表
     */
    private List<Vec3d> getBezierSamples() {
        List<Vec3d> samples = new ArrayList<>();
        
        // 简化实现，仅支持二次贝塞尔曲线
        if (controlPoints.size() < 3) {
            return getLinearSamples();
        }
        
        for (int j = 0; j < resolution; j++) {
            float t = j / (float) (resolution - 1);
            samples.add(deCasteljau(controlPoints, t));
        }
        
        return samples;
    }
    
    /**
     * 获取样条曲线采样点
     * @return 样条曲线采样点列表
     */
    private List<Vec3d> getSplineSamples() {
        // 简化实现，返回线性采样点
        return getLinearSamples();
    }
    
    /**
     * 线性插值两个点
     * @param start 起始点
     * @param end 结束点
     * @param t 插值因子 (0-1)
     * @return 插值结果
     */
    private Vec3d lerpVec3d(Vec3d start, Vec3d end, float t) {
        double x = start.x + (end.x - start.x) * t;
        double y = start.y + (end.y - start.y) * t;
        double z = start.z + (end.z - start.z) * t;
        return new Vec3d(x, y, z);
    }
    
    /**
     * 计算二次贝塞尔曲线点
     * @param p0 起始点
     * @param p1 控制点
     * @param p2 结束点
     * @param t 参数 (0-1)
     * @return 曲线上的点
     */
    private Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, float t) {
        double mt = 1 - t;
        double mt2 = mt * mt;
        double t2 = t * t;
        
        double x = mt2 * p0.x + 2 * mt * t * p1.x + t2 * p2.x;
        double y = mt2 * p0.y + 2 * mt * t * p1.y + t2 * p2.y;
        double z = mt2 * p0.z + 2 * mt * t * p1.z + t2 * p2.z;
        
        return new Vec3d(x, y, z);
    }

    private Vec3d deCasteljau(List<Vec3d> points, float t) {
        List<Vec3d> working = new ArrayList<>(points);
        while (working.size() > 1) {
            List<Vec3d> next = new ArrayList<>(working.size() - 1);
            for (int i = 0; i < working.size() - 1; i++) {
                next.add(lerpVec3d(working.get(i), working.get(i + 1), t));
            }
            working = next;
        }
        return working.get(0);
    }
} 
