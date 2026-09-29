package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * Shared roof construction for Roof Base (core types) and Roof Generator (advanced convenience).
 */
final class RoofGeometrySupport {

    private RoofGeometrySupport() {
    }

    record RoofLayout(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        String roofType,
        double height,
        double thickness,
        double overhang,
        String ridgeDirection,
        double eaveDrop
    ) {
    }

    record SpecialtyRoofParams(
        String roofType,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double inset,
        double mPeakRatio,
        double valleyDrop,
        double asymmetricLeftHeightRatio,
        double asymmetricRightHeightRatio,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
    }

    record RoofTopology(
        List<PathData> eaves,
        List<PathData> ridges,
        List<PathData> valleys
    ) {
        RoofTopology {
            eaves = eaves == null ? List.of() : List.copyOf(eaves);
            ridges = ridges == null ? List.of() : List.copyOf(ridges);
            valleys = valleys == null ? List.of() : List.copyOf(valleys);
        }

        static RoofTopology empty() {
            return new RoofTopology(List.of(), List.of(), List.of());
        }

        @Nullable PathData primaryEave() {
            return longestPath(eaves);
        }

        @Nullable PathData primaryRidge() {
            return ridges.isEmpty() ? null : ridges.getFirst();
        }

        private static @Nullable PathData longestPath(List<PathData> paths) {
            PathData best = null;
            double bestLength = -1.0d;
            for (PathData path : paths) {
                double length = pathLength(path);
                if (length > bestLength) {
                    bestLength = length;
                    best = path;
                }
            }
            return best;
        }

        private static double pathLength(@Nullable PathData path) {
            if (path == null || path.getLine() == null) {
                return 0.0d;
            }
            LineData line = path.getLine();
            return line.start().distanceTo(line.end());
        }
    }

    record RoofResult(
        @Nullable GeometryData geometry,
        RoofTopology topology
    ) {
        static RoofResult invalid() {
            return new RoofResult(null, RoofTopology.empty());
        }

        @Nullable PathData eavePath() {
            return topology.primaryEave();
        }

        @Nullable PathData ridgePath() {
            return topology.primaryRidge();
        }
    }

    static RoofResult buildCoreRoof(RoofLayout layout) {
        if (layout == null || layout.frame() == null) {
            return RoofResult.invalid();
        }
        double roofWidth = layout.frame().width() + 2.0d * layout.overhang();
        double roofDepth = layout.frame().height() + 2.0d * layout.overhang();
        Vector3d eaveCenter = new Vector3d(layout.frame().center()).fma(-layout.eaveDrop(), layout.frame().zAxis());

        GeometryData geometry = switch (layout.roofType()) {
            case "flat" -> new BoxGeometryData(
                new Vector3d(eaveCenter).fma(layout.thickness() / 2.0d, layout.frame().zAxis()),
                new Vector3d(roofWidth / 2.0d, layout.thickness() / 2.0d, roofDepth / 2.0d),
                ArchitecturalPrimitiveSupport.createOrientation(
                    layout.frame().xAxis(), layout.frame().yAxis(), layout.frame().zAxis()),
                true
            );
            case "shed" -> new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, layout.frame().yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, layout.frame().yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, layout.frame().yAxis())
                        .fma(layout.height(), layout.frame().zAxis()),
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, layout.frame().yAxis())
                        .fma(layout.height(), layout.frame().zAxis())
                ),
                new Vector3d(layout.frame().xAxis()).mul(roofWidth)
            );
            case "gable" -> buildGableRoof(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> null;
        };

        if (geometry == null) {
            return RoofResult.invalid();
        }

        RoofTopology topology = switch (layout.roofType()) {
            case "flat", "shed" -> perimeterTopology(layout.frame(), eaveCenter, roofWidth, roofDepth, List.of(), List.of());
            case "gable" -> gableTopology(
                layout.frame(), eaveCenter, roofWidth, roofDepth, layout.height(), layout.ridgeDirection());
            default -> RoofTopology.empty();
        };
        return new RoofResult(geometry, topology);
    }

    static RoofResult buildSpecialtyRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        SpecialtyRoofParams params
    ) {
        if (frame == null || params == null) {
            return RoofResult.invalid();
        }
        GeometryData geometry = switch (params.roofType()) {
            case "asymmetric_gable" -> buildAsymmetricGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(), params.ridgeRatio(),
                params.asymmetricLeftHeightRatio(), params.asymmetricRightHeightRatio());
            case "hip" -> buildHipRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.ridgeRatio(), params.inset());
            case "cross_gable" -> buildCrossGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.crossGableRatio(), params.secondaryHeightRatio(), params.crossGableOffset());
            case "m" -> buildMRoof(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.mPeakRatio(), params.valleyDrop());
            default -> null;
        };
        if (geometry == null) {
            return RoofResult.invalid();
        }

        RoofTopology topology = switch (params.roofType()) {
            case "asymmetric_gable" -> asymmetricGableTopology(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(), params.ridgeRatio(),
                params.asymmetricLeftHeightRatio(), params.asymmetricRightHeightRatio());
            case "hip" -> hipTopology(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.ridgeRatio(), params.inset());
            case "cross_gable" -> crossGableTopology(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.crossGableRatio(), params.secondaryHeightRatio(), params.crossGableOffset());
            case "m" -> mRoofTopology(
                frame, eaveCenter, roofWidth, roofDepth, params.height(), params.ridgeDirection(),
                params.mPeakRatio(), params.valleyDrop());
            default -> RoofTopology.empty();
        };
        return new RoofResult(geometry, topology);
    }

    static GeometryData buildGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        if ("y".equals(ridgeDirection)) {
            return new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                    new Vector3d(eaveCenter).fma(height, frame.zAxis()),
                    new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
                ),
                new Vector3d(frame.yAxis()).mul(roofDepth)
            );
        }
        return new PrismGeometryData(
            List.of(
                new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                new Vector3d(eaveCenter).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
            ),
            new Vector3d(frame.xAxis()).mul(roofWidth)
        );
    }

    private static RoofTopology gableTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        PathData ridge = gableRidgePath(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, List.of(ridge), List.of());
    }

    private static RoofTopology perimeterTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        List<PathData> ridges,
        List<PathData> valleys
    ) {
        return new RoofTopology(rectangleEaves(frame, eaveCenter, roofWidth, roofDepth), ridges, valleys);
    }

    private static List<PathData> rectangleEaves(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth
    ) {
        Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d);
        Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d);
        Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
        Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
        Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
        Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
        return List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
    }

    private static GeometryData buildHipRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double inset
    ) {
        double insetX = Math.min(inset, roofWidth * 0.45d);
        double insetY = Math.min(inset, roofDepth * 0.45d);
        if ("y".equals(ridgeDirection)) {
            double halfRidge = Math.max(roofDepth * ridgeRatio * 0.5d - insetY, roofDepth * 0.1d);
            List<Vector3d> profile = List.of(
                new Vector3d(eaveCenter).fma(-(roofWidth / 2.0d - insetX), frame.xAxis()),
                new Vector3d(eaveCenter).fma(-halfRidge, frame.yAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(halfRidge, frame.yAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofWidth / 2.0d - insetX, frame.xAxis())
            );
            return new PrismGeometryData(profile, new Vector3d(frame.yAxis()).mul(roofDepth - 2.0d * insetY));
        }

        double halfRidge = Math.max(roofWidth * ridgeRatio * 0.5d - insetX, roofWidth * 0.1d);
        List<Vector3d> profile = List.of(
            new Vector3d(eaveCenter).fma(-(roofDepth / 2.0d - insetY), frame.yAxis()),
            new Vector3d(eaveCenter).fma(-halfRidge, frame.xAxis()).fma(height, frame.zAxis()),
            new Vector3d(eaveCenter).fma(halfRidge, frame.xAxis()).fma(height, frame.zAxis()),
            new Vector3d(eaveCenter).fma(roofDepth / 2.0d - insetY, frame.yAxis())
        );
        return new PrismGeometryData(profile, new Vector3d(frame.xAxis()).mul(roofWidth - 2.0d * insetX));
    }

    private static RoofTopology hipTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double inset
    ) {
        double insetX = Math.min(inset, roofWidth * 0.45d);
        double insetY = Math.min(inset, roofDepth * 0.45d);
        Vector3d peak = new Vector3d(eaveCenter).fma(height, frame.zAxis());
        PathData ridge;
        List<PathData> eaves;
        if ("y".equals(ridgeDirection)) {
            double halfRidge = Math.max(roofDepth * ridgeRatio * 0.5d - insetY, roofDepth * 0.1d);
            ridge = linePath(
                new Vector3d(peak).fma(-halfRidge, frame.yAxis()),
                new Vector3d(peak).fma(halfRidge, frame.yAxis()));
            Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d - insetX);
            Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d - insetY);
            Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
            Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
            Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
            Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
            eaves = List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
        } else {
            double halfRidge = Math.max(roofWidth * ridgeRatio * 0.5d - insetX, roofWidth * 0.1d);
            ridge = linePath(
                new Vector3d(peak).fma(-halfRidge, frame.xAxis()),
                new Vector3d(peak).fma(halfRidge, frame.xAxis()));
            Vector3d hx = new Vector3d(frame.xAxis()).mul(roofWidth / 2.0d - insetX);
            Vector3d hy = new Vector3d(frame.yAxis()).mul(roofDepth / 2.0d - insetY);
            Vector3d c00 = new Vector3d(eaveCenter).sub(hx).sub(hy);
            Vector3d c10 = new Vector3d(eaveCenter).add(hx).sub(hy);
            Vector3d c11 = new Vector3d(eaveCenter).add(hx).add(hy);
            Vector3d c01 = new Vector3d(eaveCenter).sub(hx).add(hy);
            eaves = List.of(linePath(c00, c10), linePath(c10, c11), linePath(c11, c01), linePath(c01, c00));
        }
        return new RoofTopology(eaves, List.of(ridge), List.of());
    }

    private static GeometryData buildAsymmetricGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double leftHeightRatio,
        double rightHeightRatio
    ) {
        double ridgeHalfWidth = Math.max(Math.min(roofWidth, roofDepth) * 0.04d, 0.05d);
        double leftHeight = height * leftHeightRatio;
        double rightHeight = height * rightHeightRatio;
        if ("y".equals(ridgeDirection)) {
            double ridgeCenter = (-roofWidth / 2.0d) + roofWidth * ridgeRatio;
            double leftPeak = Math.max(-roofWidth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
            double rightPeak = Math.min(roofWidth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
            return new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                    new Vector3d(eaveCenter).fma(leftPeak, frame.xAxis()).fma(leftHeight, frame.zAxis()),
                    new Vector3d(eaveCenter).fma(rightPeak, frame.xAxis()).fma(rightHeight, frame.zAxis()),
                    new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
                ),
                new Vector3d(frame.yAxis()).mul(roofDepth)
            );
        }

        double ridgeCenter = (-roofDepth / 2.0d) + roofDepth * ridgeRatio;
        double leftPeak = Math.max(-roofDepth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
        double rightPeak = Math.min(roofDepth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
        return new PrismGeometryData(
            List.of(
                new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                new Vector3d(eaveCenter).fma(leftPeak, frame.yAxis()).fma(leftHeight, frame.zAxis()),
                new Vector3d(eaveCenter).fma(rightPeak, frame.yAxis()).fma(rightHeight, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
            ),
            new Vector3d(frame.xAxis()).mul(roofWidth)
        );
    }

    private static RoofTopology asymmetricGableTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double ridgeRatio,
        double leftHeightRatio,
        double rightHeightRatio
    ) {
        double ridgeHalfWidth = Math.max(Math.min(roofWidth, roofDepth) * 0.04d, 0.05d);
        double leftHeight = height * leftHeightRatio;
        double rightHeight = height * rightHeightRatio;
        PathData ridge;
        if ("y".equals(ridgeDirection)) {
            double ridgeCenter = (-roofWidth / 2.0d) + roofWidth * ridgeRatio;
            double leftPeak = Math.max(-roofWidth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
            double rightPeak = Math.min(roofWidth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
            Vector3d leftTop = new Vector3d(eaveCenter).fma(leftPeak, frame.xAxis()).fma(leftHeight, frame.zAxis());
            Vector3d rightTop = new Vector3d(eaveCenter).fma(rightPeak, frame.xAxis()).fma(rightHeight, frame.zAxis());
            ridge = linePath(leftTop, rightTop);
        } else {
            double ridgeCenter = (-roofDepth / 2.0d) + roofDepth * ridgeRatio;
            double leftPeak = Math.max(-roofDepth / 2.0d + ridgeHalfWidth, ridgeCenter - ridgeHalfWidth);
            double rightPeak = Math.min(roofDepth / 2.0d - ridgeHalfWidth, ridgeCenter + ridgeHalfWidth);
            Vector3d leftTop = new Vector3d(eaveCenter).fma(leftPeak, frame.yAxis()).fma(leftHeight, frame.zAxis());
            Vector3d rightTop = new Vector3d(eaveCenter).fma(rightPeak, frame.yAxis()).fma(rightHeight, frame.zAxis());
            ridge = linePath(leftTop, rightTop);
        }
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, List.of(ridge), List.of());
    }

    private static GeometryData buildCrossGableRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
        GeometryData primary = buildGableRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        String secondaryDirection = "y".equals(ridgeDirection) ? "x" : "y";
        Vector3d secondaryCenter = new Vector3d(eaveCenter);
        if ("y".equals(ridgeDirection)) {
            secondaryCenter.fma(crossGableOffset * roofDepth * 0.5d, frame.yAxis());
        } else {
            secondaryCenter.fma(crossGableOffset * roofWidth * 0.5d, frame.xAxis());
        }
        GeometryData secondary = buildGableRoof(
            frame,
            secondaryCenter,
            roofWidth * crossGableRatio,
            roofDepth * crossGableRatio,
            height * secondaryHeightRatio,
            secondaryDirection
        );
        return GeometryOutputUtils.packGeometry(List.of(primary, secondary));
    }

    private static RoofTopology crossGableTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
        PathData primaryRidge = gableRidgePath(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        String secondaryDirection = "y".equals(ridgeDirection) ? "x" : "y";
        Vector3d secondaryCenter = new Vector3d(eaveCenter);
        if ("y".equals(ridgeDirection)) {
            secondaryCenter.fma(crossGableOffset * roofDepth * 0.5d, frame.yAxis());
        } else {
            secondaryCenter.fma(crossGableOffset * roofWidth * 0.5d, frame.xAxis());
        }
        PathData secondaryRidge = gableRidgePath(
            frame,
            secondaryCenter,
            roofWidth * crossGableRatio,
            roofDepth * crossGableRatio,
            height * secondaryHeightRatio,
            secondaryDirection
        );
        PathData valley = approximateCrossGableValley(frame, eaveCenter, height, ridgeDirection, secondaryCenter);
        return perimeterTopology(
            frame, eaveCenter, roofWidth, roofDepth,
            List.of(primaryRidge, secondaryRidge),
            valley == null ? List.of() : List.of(valley)
        );
    }

    private static @Nullable PathData approximateCrossGableValley(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double height,
        String ridgeDirection,
        Vector3d secondaryCenter
    ) {
        Vector3d peak = new Vector3d(eaveCenter).fma(height, frame.zAxis());
        Vector3d secondaryPeak = new Vector3d(secondaryCenter).fma(height, frame.zAxis());
        if (peak.distanceSquared(secondaryPeak) <= 1.0e-12d) {
            return null;
        }
        return linePath(peak, secondaryPeak);
    }

    private static GeometryData buildMRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double mPeakRatio,
        double valleyDrop
    ) {
        double valleyHeight = Math.max(0.0d, height - valleyDrop);
        if ("y".equals(ridgeDirection)) {
            double ridgeOffset = roofWidth * mPeakRatio;
            List<Vector3d> profile = List.of(
                new Vector3d(eaveCenter).fma(-roofWidth / 2.0d, frame.xAxis()),
                new Vector3d(eaveCenter).fma(-ridgeOffset, frame.xAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(valleyHeight, frame.zAxis()),
                new Vector3d(eaveCenter).fma(ridgeOffset, frame.xAxis()).fma(height, frame.zAxis()),
                new Vector3d(eaveCenter).fma(roofWidth / 2.0d, frame.xAxis())
            );
            return new PrismGeometryData(profile, new Vector3d(frame.yAxis()).mul(roofDepth));
        }

        double ridgeOffset = roofDepth * mPeakRatio;
        List<Vector3d> profile = List.of(
            new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
            new Vector3d(eaveCenter).fma(-ridgeOffset, frame.yAxis()).fma(height, frame.zAxis()),
            new Vector3d(eaveCenter).fma(valleyHeight, frame.zAxis()),
            new Vector3d(eaveCenter).fma(ridgeOffset, frame.yAxis()).fma(height, frame.zAxis()),
            new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis())
        );
        return new PrismGeometryData(profile, new Vector3d(frame.xAxis()).mul(roofWidth));
    }

    private static RoofTopology mRoofTopology(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection,
        double mPeakRatio,
        double valleyDrop
    ) {
        double valleyHeight = Math.max(0.0d, height - valleyDrop);
        Vector3d valleyPoint = new Vector3d(eaveCenter).fma(valleyHeight, frame.zAxis());
        List<PathData> ridges;
        PathData valley;
        if ("y".equals(ridgeDirection)) {
            double ridgeOffset = roofWidth * mPeakRatio;
            Vector3d leftPeak = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.xAxis()).fma(height, frame.zAxis());
            Vector3d rightPeak = new Vector3d(eaveCenter).fma(ridgeOffset, frame.xAxis()).fma(height, frame.zAxis());
            ridges = List.of(linePath(leftPeak, valleyPoint), linePath(valleyPoint, rightPeak));
            Vector3d valleyA = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.xAxis()).fma(valleyHeight, frame.zAxis());
            Vector3d valleyB = new Vector3d(eaveCenter).fma(ridgeOffset, frame.xAxis()).fma(valleyHeight, frame.zAxis());
            valley = linePath(valleyA, valleyB);
        } else {
            double ridgeOffset = roofDepth * mPeakRatio;
            Vector3d leftPeak = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.yAxis()).fma(height, frame.zAxis());
            Vector3d rightPeak = new Vector3d(eaveCenter).fma(ridgeOffset, frame.yAxis()).fma(height, frame.zAxis());
            ridges = List.of(linePath(leftPeak, valleyPoint), linePath(valleyPoint, rightPeak));
            Vector3d valleyA = new Vector3d(eaveCenter).fma(-ridgeOffset, frame.yAxis()).fma(valleyHeight, frame.zAxis());
            Vector3d valleyB = new Vector3d(eaveCenter).fma(ridgeOffset, frame.yAxis()).fma(valleyHeight, frame.zAxis());
            valley = linePath(valleyA, valleyB);
        }
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, ridges, List.of(valley));
    }

    static PathData gableRidgePath(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
        double height,
        String ridgeDirection
    ) {
        Vector3d peak = new Vector3d(eaveCenter).fma(height, frame.zAxis());
        if ("y".equals(ridgeDirection)) {
            Vector3d a = new Vector3d(peak).fma(-roofDepth / 2.0d, frame.yAxis());
            Vector3d b = new Vector3d(peak).fma(roofDepth / 2.0d, frame.yAxis());
            return linePath(a, b);
        }
        Vector3d a = new Vector3d(peak).fma(-roofWidth / 2.0d, frame.xAxis());
        Vector3d b = new Vector3d(peak).fma(roofWidth / 2.0d, frame.xAxis());
        return linePath(a, b);
    }

    /** @deprecated use {@link RoofTopology#primaryEave()} via {@link RoofResult#topology()} */
    static PathData eaveLoop(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth
    ) {
        return perimeterTopology(frame, eaveCenter, roofWidth, roofDepth, List.of(), List.of()).primaryEave();
    }

    static String resolveCoreRoofType(Object value) {
        String type = resolveRoofType(value);
        return switch (type) {
            case "flat", "shed", "gable" -> type;
            default -> "gable";
        };
    }

    static String resolveRoofType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        }
        return "gable";
    }

    static String resolveRidgeDirection(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("y")) {
                return "y";
            }
        }
        return "x";
    }

    private static PathData linePath(Vector3d a, Vector3d b) {
        return PathData.fromLine(new LineData(
            new Vec3d(a.x, a.y, a.z),
            new Vec3d(b.x, b.y, b.z)
        ));
    }
}
