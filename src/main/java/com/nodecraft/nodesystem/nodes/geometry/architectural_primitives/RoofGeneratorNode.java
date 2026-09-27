package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Advanced roof convenience node (specialty shapes).
 * Prefer {@link RoofBaseNode} for flat / shed / gable.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.roof_generator",
    displayName = "Roof Generator",
    description = "Advanced roof convenience (specialty shapes); prefer Roof Base for flat/shed/gable",
    category = "geometry.architectural_primitives",
    order = 6
)
public class RoofGeneratorNode extends BaseNode {

    private static final Set<String> SPECIALTY_TYPES = Set.of("asymmetric_gable", "hip", "cross_gable", "m");
    private static final Set<String> RIDGE_DIRECTIONS = Set.of("x", "y");

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
    private static final String OUTPUT_EAVE_PATH_ID = "output_eave_path";
    private static final String OUTPUT_RIDGE_PATH_ID = "output_ridge_path";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RoofGeneratorNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.roof_generator");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the roof footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_ROOF_TYPE_ID, "Roof Type",
            "Specialty roof type: asymmetric_gable, hip, cross_gable, or m", NodeDataType.STRING, this));
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
        addOutputPort(new BasePort(OUTPUT_EAVE_PATH_ID, "Primary Eave Path", "Primary eave edge path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_RIDGE_PATH_ID, "Primary Ridge Path", "Primary ridge path when applicable", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid roof could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Advanced roof convenience (specialty shapes); prefer Roof Base for flat/shed/gable";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxFaceData face = ArchitecturalInputUtils.resolveRequiredFace(this, INPUT_FACE_ID);
        if (face == null) {
            writeInvalid("Face is required");
            return;
        }
        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        if (frame == null) {
            writeInvalid("Face is required (non-degenerate box face)");
            return;
        }

        String roofType = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_ROOF_TYPE_ID, "hip", SPECIALTY_TYPES);
        if (roofType == null) {
            writeInvalid("Roof Type must be one of: asymmetric_gable, hip, cross_gable, m "
                + "(use Roof Base for flat/shed/gable)");
            return;
        }
        String ridgeDirection = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_RIDGE_DIRECTION_ID, "x", RIDGE_DIRECTIONS);
        if (ridgeDirection == null) {
            writeInvalid("Ridge Direction must be one of: x, y");
            return;
        }

        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 2.0d);
        if (height == null) {
            writeInvalid("Height must be a positive finite number");
            return;
        }
        Double thickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_THICKNESS_ID, 0.25d);
        if (thickness == null) {
            writeInvalid("Thickness must be a positive finite number");
            return;
        }
        Double overhang = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_OVERHANG_ID, 0.0d);
        if (overhang == null) {
            writeInvalid("Overhang must be a non-negative finite number");
            return;
        }
        Double inset = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_INSET_ID, 0.0d);
        if (inset == null) {
            writeInvalid("Inset must be a non-negative finite number");
            return;
        }
        Double eaveDrop = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_EAVE_DROP_ID, 0.0d);
        if (eaveDrop == null) {
            writeInvalid("Eave Drop must be a non-negative finite number");
            return;
        }

        Double ridgeRatio = resolveBoundedRatio(INPUT_RIDGE_RATIO_ID, 0.5d, 0.1d, 0.9d, "Ridge Ratio");
        if (ridgeRatio == null) {
            return;
        }
        Double mPeakRatio = resolveBoundedRatio(INPUT_M_PEAK_RATIO_ID, 0.25d, 0.1d, 0.45d, "M Peak Ratio");
        if (mPeakRatio == null) {
            return;
        }
        Double valleyDrop = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(
            this, INPUT_VALLEY_DROP_ID, height * 0.5d);
        if (valleyDrop == null) {
            writeInvalid("Valley Drop must be a non-negative finite number");
            return;
        }
        Double asymmetricLeft = resolveBoundedRatio(
            INPUT_ASYMMETRIC_LEFT_HEIGHT_RATIO_ID, 1.0d, 0.25d, 1.5d, "Asymmetric Left Height Ratio");
        if (asymmetricLeft == null) {
            return;
        }
        Double asymmetricRight = resolveBoundedRatio(
            INPUT_ASYMMETRIC_RIGHT_HEIGHT_RATIO_ID, 0.65d, 0.25d, 1.5d, "Asymmetric Right Height Ratio");
        if (asymmetricRight == null) {
            return;
        }
        Double crossGableRatio = resolveBoundedRatio(
            INPUT_CROSS_GABLE_RATIO_ID, 0.6d, 0.25d, 0.95d, "Cross Gable Ratio");
        if (crossGableRatio == null) {
            return;
        }
        Double secondaryHeightRatio = resolveBoundedRatio(
            INPUT_SECONDARY_HEIGHT_RATIO_ID, 0.85d, 0.25d, 1.5d, "Secondary Height Ratio");
        if (secondaryHeightRatio == null) {
            return;
        }
        Double crossGableOffset = resolveBoundedRatio(
            INPUT_CROSS_GABLE_OFFSET_ID, 0.0d, -0.5d, 0.5d, "Cross Gable Offset");
        if (crossGableOffset == null) {
            return;
        }

        double roofWidth = frame.width() + 2.0d * overhang;
        double roofDepth = frame.height() + 2.0d * overhang;
        Vector3d eaveCenter = new Vector3d(frame.center()).fma(-eaveDrop, frame.zAxis());
        PathData eavePath = RoofGeometrySupport.eaveLoop(frame, eaveCenter, roofWidth, roofDepth);

        GeometryData geometry = buildSpecialtyRoof(
            frame,
            eaveCenter,
            roofWidth,
            roofDepth,
            roofType,
            height,
            ridgeDirection,
            ridgeRatio,
            inset,
            mPeakRatio,
            valleyDrop,
            asymmetricLeft,
            asymmetricRight,
            crossGableRatio,
            secondaryHeightRatio,
            crossGableOffset
        );
        if (geometry == null) {
            writeInvalid("Could not generate roof geometry for the given parameters");
            return;
        }

        PathData ridgePath = RoofGeometrySupport.gableRidgePath(
            frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_EAVE_PATH_ID, eavePath);
        outputValues.put(OUTPUT_RIDGE_PATH_ID, ridgePath);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable Double resolveBoundedRatio(
        String portId,
        double fallback,
        double minInclusive,
        double maxInclusive,
        String label
    ) {
        Double value = OptionalPortDrive.resolveOptionalDouble(this, portId, fallback);
        if (value == null || !(value >= minInclusive) || !(value <= maxInclusive) || !Double.isFinite(value)) {
            writeInvalid(label + " must be a finite number in [" + minInclusive + ", " + maxInclusive + "]");
            return null;
        }
        return value;
    }

    private GeometryData buildSpecialtyRoof(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        Vector3d eaveCenter,
        double roofWidth,
        double roofDepth,
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
        return switch (roofType) {
            case "asymmetric_gable" -> buildAsymmetricGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection, ridgeRatio,
                asymmetricLeftHeightRatio, asymmetricRightHeightRatio);
            case "hip" -> buildHipRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection, ridgeRatio, inset);
            case "cross_gable" -> buildCrossGableRoof(
                frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection,
                crossGableRatio, secondaryHeightRatio, crossGableOffset);
            case "m" -> buildMRoof(frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection, mPeakRatio, valleyDrop);
            default -> null;
        };
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
        GeometryData primary = RoofGeometrySupport.buildGableRoof(
            frame, eaveCenter, roofWidth, roofDepth, height, ridgeDirection);
        String secondaryDirection = "y".equals(ridgeDirection) ? "x" : "y";
        Vector3d secondaryCenter = new Vector3d(eaveCenter);
        if ("y".equals(ridgeDirection)) {
            secondaryCenter.fma(crossGableOffset * roofDepth * 0.5d, frame.yAxis());
        } else {
            secondaryCenter.fma(crossGableOffset * roofWidth * 0.5d, frame.xAxis());
        }
        GeometryData secondary = RoofGeometrySupport.buildGableRoof(
            frame,
            secondaryCenter,
            roofWidth * crossGableRatio,
            roofDepth * crossGableRatio,
            height * secondaryHeightRatio,
            secondaryDirection
        );
        return GeometryOutputUtils.packGeometry(List.of(primary, secondary));
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

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_EAVE_PATH_ID, null);
        outputValues.put(OUTPUT_RIDGE_PATH_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
