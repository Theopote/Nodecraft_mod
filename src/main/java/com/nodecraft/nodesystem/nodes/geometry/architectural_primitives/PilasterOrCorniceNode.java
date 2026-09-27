package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import com.nodecraft.nodesystem.util.GeometryOutputUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates pilasters and a cornice from a box face.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.pilaster_cornice",
    displayName = "Pilaster / Cornice",
    description = "Generates pilasters and a cornice along a box face",
    category = "geometry.architectural_primitives",
    order = 10
)
public class PilasterOrCorniceNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_PILASTER_WIDTH_ID = "input_pilaster_width";
    private static final String INPUT_PILASTER_DEPTH_ID = "input_pilaster_depth";
    private static final String INPUT_PILASTER_HEIGHT_ID = "input_pilaster_height";
    private static final String INPUT_CORNICE_HEIGHT_ID = "input_cornice_height";
    private static final String INPUT_MARGIN_ID = "input_margin";
    private static final String INPUT_INCLUDE_SIDE_ID = "input_include_side";
    private static final String INPUT_INCLUDE_CORNICE_ID = "input_include_cornice";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PilasterOrCorniceNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.pilaster_cornice");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the facade surface", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_PILASTER_WIDTH_ID, "Pilaster Width", "Pilaster width across the facade", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PILASTER_DEPTH_ID, "Pilaster Depth", "Pilaster projection from the face", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PILASTER_HEIGHT_ID, "Pilaster Height", "Pilaster height along the face", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CORNICE_HEIGHT_ID, "Cornice Height", "Height of the top cornice band", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MARGIN_ID, "Margin", "Margin from the face edges to the pilasters", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_INCLUDE_SIDE_ID, "Include Side Pilasters", "Whether to generate side pilasters", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_CORNICE_ID, "Include Cornice", "Whether to generate a top cornice", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Pilaster and cornice geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of geometry pieces created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid pilaster/cornice could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates pilasters and a cornice along a box face";
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

        Double pilasterWidth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_PILASTER_WIDTH_ID, 0.5d);
        if (pilasterWidth == null) {
            writeInvalid("Pilaster Width must be a positive finite number");
            return;
        }
        Double pilasterDepth = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_PILASTER_DEPTH_ID, 0.2d);
        if (pilasterDepth == null) {
            writeInvalid("Pilaster Depth must be a positive finite number");
            return;
        }
        Double pilasterHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_PILASTER_HEIGHT_ID, frame.height());
        if (pilasterHeight == null) {
            writeInvalid("Pilaster Height must be a positive finite number");
            return;
        }
        Double corniceHeight = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_CORNICE_HEIGHT_ID, 0.25d);
        if (corniceHeight == null) {
            writeInvalid("Cornice Height must be a positive finite number");
            return;
        }
        Double margin = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_MARGIN_ID, 0.0d);
        if (margin == null) {
            writeInvalid("Margin must be a non-negative finite number");
            return;
        }
        Boolean includeSide = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INCLUDE_SIDE_ID, true);
        if (includeSide == null) {
            writeInvalid("Include Side Pilasters must be a boolean");
            return;
        }
        Boolean includeCornice = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_INCLUDE_CORNICE_ID, true);
        if (includeCornice == null) {
            writeInvalid("Include Cornice must be a boolean");
            return;
        }
        if (!includeSide && !includeCornice) {
            writeInvalid("Enable Include Side Pilasters and/or Include Cornice");
            return;
        }

        List<GeometryData> pieces = buildPieces(
            frame, pilasterWidth, pilasterDepth, pilasterHeight, corniceHeight, margin, includeSide, includeCornice);
        if (pieces.isEmpty()) {
            writeInvalid("Could not generate pilaster/cornice geometry for the given parameters");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, GeometryOutputUtils.packGeometry(pieces));
        outputValues.put(OUTPUT_COUNT_ID, pieces.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private List<GeometryData> buildPieces(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double pilasterWidth,
        double pilasterDepth,
        double pilasterHeight,
        double corniceHeight,
        double margin,
        boolean includeSide,
        boolean includeCornice
    ) {
        List<GeometryData> pieces = new ArrayList<>();
        double inset = pilasterDepth / 2.0d;

        if (includeSide) {
            double leftX = -frame.width() / 2.0d + margin + pilasterWidth / 2.0d;
            double rightX = frame.width() / 2.0d - margin - pilasterWidth / 2.0d;
            double centerY = -frame.height() / 2.0d + pilasterHeight / 2.0d;

            pieces.add(createPilaster(frame, leftX, centerY, pilasterWidth, pilasterHeight, pilasterDepth, inset));
            if (rightX > leftX + 1.0e-6d) {
                pieces.add(createPilaster(frame, rightX, centerY, pilasterWidth, pilasterHeight, pilasterDepth, inset));
            }
        }

        if (includeCornice) {
            double corniceWidth = frame.width() - 2.0d * margin;
            if (corniceWidth > pilasterWidth) {
                Vector3d center = new Vector3d(frame.center())
                    .fma((frame.height() / 2.0d) - corniceHeight / 2.0d, frame.yAxis())
                    .fma(inset, frame.zAxis());
                Vector3d halfExtents = new Vector3d(corniceWidth / 2.0d, corniceHeight / 2.0d, pilasterDepth / 2.0d);
                pieces.add(ArchitecturalPrimitiveSupport.createOrientedBox(
                    center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
            }
        }

        return List.copyOf(pieces);
    }

    private BoxGeometryData createPilaster(
        ArchitecturalPrimitiveSupport.FaceFrame frame,
        double offsetX,
        double offsetY,
        double width,
        double height,
        double depth,
        double inset
    ) {
        Vector3d center = new Vector3d(frame.center())
            .fma(offsetX, frame.xAxis())
            .fma(offsetY, frame.yAxis())
            .fma(inset, frame.zAxis());
        Vector3d halfExtents = new Vector3d(width / 2.0d, height / 2.0d, depth / 2.0d);
        return ArchitecturalPrimitiveSupport.createOrientedBox(
            center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
