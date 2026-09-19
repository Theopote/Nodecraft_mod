package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates configurable roof volumes from a box face footprint.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.roof_generator",
    displayName = "Roof Generator",
    description = "Generates configurable roof volumes from a box face footprint",
    category = "geometry.architectural_primitives",
    order = 5
)
public class RoofGeneratorNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_ROOF_TYPE_ID = "input_roof_type";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OVERHANG_ID = "input_overhang";
    private static final String INPUT_RIDGE_DIRECTION_ID = "input_ridge_direction";
    private static final String INPUT_RIDGE_RATIO_ID = "input_ridge_ratio";
    private static final String INPUT_INSET_ID = "input_inset";
    private static final String INPUT_EAVE_DROP_ID = "input_eave_drop";
    private static final String INPUT_M_PEAK_RATIO_ID = "input_m_peak_ratio";
    private static final String INPUT_VALLEY_DROP_ID = "input_valley_drop";
    private static final String INPUT_ASYMMETRIC_LEFT_HEIGHT_RATIO_ID = "input_asymmetric_left_height_ratio";
    private static final String INPUT_ASYMMETRIC_RIGHT_HEIGHT_RATIO_ID = "input_asymmetric_right_height_ratio";
    private static final String INPUT_CROSS_GABLE_RATIO_ID = "input_cross_gable_ratio";
    private static final String INPUT_SECONDARY_HEIGHT_RATIO_ID = "input_secondary_height_ratio";
    private static final String INPUT_CROSS_GABLE_OFFSET_ID = "input_cross_gable_offset";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RoofGeneratorNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.roof_generator");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the roof footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_ROOF_TYPE_ID, "Roof Type", "Roof type: flat, shed, gable, asymmetric_gable, hip, cross_gable, or m", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Roof peak height above the footprint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness used for flat roof slabs", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OVERHANG_ID, "Overhang", "Extra overhang beyond the footprint edges", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RIDGE_DIRECTION_ID, "Ridge Direction", "Ridge direction: x or y", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_RIDGE_RATIO_ID, "Ridge Ratio", "Normalized ridge placement used for hip roofs (0.1-0.9)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INSET_ID, "Inset", "Inset applied to hip roof eaves before rising to the ridge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_EAVE_DROP_ID, "Eave Drop", "Drops the eave edge below the footprint plane", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_M_PEAK_RATIO_ID, "M Peak Ratio", "Normalized peak placement for M roofs (0.1-0.45 per side)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_VALLEY_DROP_ID, "Valley Drop", "Vertical drop from the M-roof peak to its center valley", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ASYMMETRIC_LEFT_HEIGHT_RATIO_ID, "Asymmetric Left Height Ratio", "Height ratio for the left peak of an asymmetric gable roof", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ASYMMETRIC_RIGHT_HEIGHT_RATIO_ID, "Asymmetric Right Height Ratio", "Height ratio for the right peak of an asymmetric gable roof", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CROSS_GABLE_RATIO_ID, "Cross Gable Ratio", "Relative footprint scale used for the crossing gable wing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SECONDARY_HEIGHT_RATIO_ID, "Secondary Height Ratio", "Relative height of the crossing gable wing", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CROSS_GABLE_OFFSET_ID, "Cross Gable Offset", "Normalized offset for the crossing gable wing along the primary ridge axis (-0.5 to 0.5)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Generated roof geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid roof could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates configurable roof volumes from a box face footprint";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        GeometryData geometry = null;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                String roofType = resolveRoofType(inputValues.get(INPUT_ROOF_TYPE_ID));
                double height = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 2.0d);
                double thickness = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_THICKNESS_ID), 0.25d);
                double overhang = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_OVERHANG_ID), 0.0d);
                String ridgeDirection = resolveRidgeDirection(inputValues.get(INPUT_RIDGE_DIRECTION_ID));
                double ridgeRatio = resolveRidgeRatio(inputValues.get(INPUT_RIDGE_RATIO_ID));
                double inset = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_INSET_ID), 0.0d);
                double eaveDrop = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_EAVE_DROP_ID), 0.0d);
                double mPeakRatio = resolveMPeakRatio(inputValues.get(INPUT_M_PEAK_RATIO_ID));
                double valleyDrop = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_VALLEY_DROP_ID), height * 0.5d);
                double asymmetricLeftHeightRatio = resolveHeightRatio(inputValues.get(INPUT_ASYMMETRIC_LEFT_HEIGHT_RATIO_ID), 1.0d);
                double asymmetricRightHeightRatio = resolveHeightRatio(inputValues.get(INPUT_ASYMMETRIC_RIGHT_HEIGHT_RATIO_ID), 0.65d);
                double crossGableRatio = resolveCrossGableRatio(inputValues.get(INPUT_CROSS_GABLE_RATIO_ID));
                double secondaryHeightRatio = resolveHeightRatio(inputValues.get(INPUT_SECONDARY_HEIGHT_RATIO_ID), 0.85d);
                double crossGableOffset = resolveCrossGableOffset(inputValues.get(INPUT_CROSS_GABLE_OFFSET_ID));

                geometry = buildRoof(
                    frame,
                    roofType,
                    height,
                    thickness,
                    overhang,
                    ridgeDirection,
                    ridgeRatio,
                    inset,
                    eaveDrop,
                    mPeakRatio,
                    valleyDrop,
                    asymmetricLeftHeightRatio,
                    asymmetricRightHeightRatio,
                    crossGableRatio,
                    secondaryHeightRatio,
                    crossGableOffset
                );
                valid = geometry != null;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private GeometryData buildRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        String roofType,
        double height,
        double thickness,
        double overhang,
        String ridgeDirection,
        double ridgeRatio,
        double inset,
        double eaveDrop,
        double mPeakRatio,
        double valleyDrop,
        double asymmetricLeftHeightRatio,
        double asymmetricRightHeightRatio,
        double crossGableRatio,
        double secondaryHeightRatio,
        double crossGableOffset
    ) {
        double roofWidth = frame.width() + 2.0d * overhang;
        double roofDepth = frame.height() + 2.0d * overhang;
        Vector3d footprintCenter = new Vector3d(frame.center());
        Vector3d eaveCenter = new Vector3d(footprintCenter).fma(-eaveDrop, frame.zAxis());

        return switch (roofType) {
            case "flat" -> new BoxGeometryData(
                new Vector3d(eaveCenter).fma(thickness / 2.0d, frame.zAxis()),
                new Vector3d(roofWidth / 2.0d, thickness / 2.0d, roofDepth / 2.0d),
                ArchitecturalPrimitiveSupport.createOrientation(frame.xAxis(), frame.yAxis(), frame.zAxis()),
                true
            );
            case "shed" -> new PrismGeometryData(
                List.of(
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis()),
                    new Vector3d(eaveCenter).fma(roofDepth / 2.0d, frame.yAxis()).fma(height, frame.zAxis()),
                    new Vector3d(eaveCenter).fma(-roofDepth / 2.0d, frame.yAxis()).fma(height, frame.zAxis())
                ),
                new Vector3d(frame.xAxis()).mul(roofWidth)
            );
            case "gable" -> buildGableRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
            case "asymmetric_gable" -> buildAsymmetricGableRoof(
                frame,
                eaveCenter,
                roofWidth,
                roofDepth,
                height,
                ridgeDirection,
                ridgeRatio,
                asymmetricLeftHeightRatio,
                asymmetricRightHeightRatio
            );
            case "hip" -> buildHipRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection, ridgeRatio, inset);
            case "cross_gable" -> buildCrossGableRoof(
                frame,
                eaveCenter,
                roofWidth,
                roofDepth,
                height,
                ridgeDirection,
                crossGableRatio,
                secondaryHeightRatio,
                crossGableOffset
            );
            case "m" -> buildMRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection, mPeakRatio, valleyDrop);
            default -> null;
        };
    }

    private GeometryData buildGableRoof(
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

    private GeometryData buildHipRoof(
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

    private GeometryData buildAsymmetricGableRoof(
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

    private GeometryData buildCrossGableRoof(
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
        return new CompositeGeometryData(List.of(primary, secondary));
    }

    private GeometryData buildMRoof(
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

    private String resolveRoofType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        }
        return "gable";
    }

    private String resolveRidgeDirection(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("y")) {
                return "y";
            }
        }
        return "x";
    }

    private double resolveRidgeRatio(Object value) {
        if (value instanceof Number number) {
            return Math.max(0.1d, Math.min(0.9d, number.doubleValue()));
        }
        return 0.5d;
    }

    private double resolveMPeakRatio(Object value) {
        if (value instanceof Number number) {
            return Math.max(0.1d, Math.min(0.45d, number.doubleValue()));
        }
        return 0.25d;
    }

    private double resolveHeightRatio(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return Math.max(0.25d, Math.min(1.5d, number.doubleValue()));
        }
        return defaultValue;
    }

    private double resolveCrossGableRatio(Object value) {
        if (value instanceof Number number) {
            return Math.max(0.25d, Math.min(0.95d, number.doubleValue()));
        }
        return 0.6d;
    }

    private double resolveCrossGableOffset(Object value) {
        if (value instanceof Number number) {
            return Math.max(-0.5d, Math.min(0.5d, number.doubleValue()));
        }
        return 0.0d;
    }
}