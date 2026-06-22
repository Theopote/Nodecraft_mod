package com.nodecraft.nodesystem.preview;

import net.minecraft.util.math.Vec3d;

/**
 * Data payload for {@link com.nodecraft.nodesystem.preview.elements.TransformationGizmoElement}.
 */
public final class TransformGizmoPreviewData {

    private final Vec3d origin;
    private final Vec3d xAxis;
    private final Vec3d yAxis;
    private final Vec3d zAxis;
    private final double baseAxisLength;
    private final String gizmoType;
    private final boolean moveEnabled;
    private final boolean rotateEnabled;
    private final boolean scaleEnabled;

    public TransformGizmoPreviewData(Vec3d origin) {
        this(origin, new Vec3d(1.0d, 0.0d, 0.0d), new Vec3d(0.0d, 1.0d, 0.0d), new Vec3d(0.0d, 0.0d, 1.0d), 1.0d, "all");
    }

    public TransformGizmoPreviewData(
        Vec3d origin,
        Vec3d xAxis,
        Vec3d yAxis,
        Vec3d zAxis,
        double baseAxisLength,
        String gizmoType
    ) {
        this(origin, xAxis, yAxis, zAxis, baseAxisLength, gizmoType, true, true, true);
    }

    public TransformGizmoPreviewData(
        Vec3d origin,
        Vec3d xAxis,
        Vec3d yAxis,
        Vec3d zAxis,
        double baseAxisLength,
        String gizmoType,
        boolean moveEnabled,
        boolean rotateEnabled,
        boolean scaleEnabled
    ) {
        this.origin = origin == null ? Vec3d.ZERO : origin;
        this.xAxis = normalizeOrDefault(xAxis, new Vec3d(1.0d, 0.0d, 0.0d));
        this.yAxis = normalizeOrDefault(yAxis, new Vec3d(0.0d, 1.0d, 0.0d));
        this.zAxis = normalizeOrDefault(zAxis, new Vec3d(0.0d, 0.0d, 1.0d));
        this.baseAxisLength = Math.max(0.25d, baseAxisLength);
        this.gizmoType = gizmoType == null || gizmoType.isBlank() ? "all" : gizmoType.trim().toLowerCase();
        this.moveEnabled = moveEnabled;
        this.rotateEnabled = rotateEnabled;
        this.scaleEnabled = scaleEnabled;
    }

    public Vec3d getOrigin() {
        return origin;
    }

    public Vec3d getXAxis() {
        return xAxis;
    }

    public Vec3d getYAxis() {
        return yAxis;
    }

    public Vec3d getZAxis() {
        return zAxis;
    }

    public double getBaseAxisLength() {
        return baseAxisLength;
    }

    public String getGizmoType() {
        return gizmoType;
    }

    public boolean showsMove() {
        return moveEnabled && ("all".equals(gizmoType) || "move".equals(gizmoType) || "translate".equals(gizmoType));
    }

    public boolean showsRotate() {
        return rotateEnabled && ("all".equals(gizmoType) || "rotate".equals(gizmoType) || "rotation".equals(gizmoType));
    }

    public boolean showsScale() {
        return scaleEnabled && ("all".equals(gizmoType) || "scale".equals(gizmoType));
    }

    public boolean isInteractive() {
        return showsMove() || showsRotate() || showsScale();
    }

    public TransformGizmoPreviewData withOriginAndLength(Vec3d newOrigin, double newAxisLength) {
        return new TransformGizmoPreviewData(
            newOrigin,
            xAxis,
            yAxis,
            zAxis,
            newAxisLength,
            gizmoType,
            moveEnabled,
            rotateEnabled,
            scaleEnabled
        );
    }

    public TransformGizmoPreviewData withGizmoType(String newGizmoType) {
        return new TransformGizmoPreviewData(
            origin,
            xAxis,
            yAxis,
            zAxis,
            baseAxisLength,
            newGizmoType,
            moveEnabled,
            rotateEnabled,
            scaleEnabled
        );
    }

    private static Vec3d normalizeOrDefault(Vec3d axis, Vec3d fallback) {
        if (axis == null || axis.lengthSquared() < 1.0e-9d) {
            return fallback;
        }
        return axis.normalize();
    }
}
