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
import com.nodecraft.nodesystem.execution.ExecutionContext;
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
    order = 9
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

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing pilasters and cornice", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of geometry pieces created", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid pilaster/cornice could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates pilasters and a cornice along a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        GeometryData geometry = null;
        int count = 0;
        boolean valid = false;

        if (faceObj instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                double pilasterWidth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_PILASTER_WIDTH_ID), 0.5d);
                double pilasterDepth = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_PILASTER_DEPTH_ID), 0.2d);
                double pilasterHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_PILASTER_HEIGHT_ID), frame.height());
                double corniceHeight = ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_CORNICE_HEIGHT_ID), 0.25d);
                double margin = ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_MARGIN_ID), 0.0d);
                boolean includeSide = resolveBoolean(inputValues.get(INPUT_INCLUDE_SIDE_ID), true);
                boolean includeCornice = resolveBoolean(inputValues.get(INPUT_INCLUDE_CORNICE_ID), true);

                List<GeometryData> pieces = buildPieces(frame, pilasterWidth, pilasterDepth, pilasterHeight, corniceHeight, margin, includeSide, includeCornice);
                if (!pieces.isEmpty()) {
                    geometry = new CompositeGeometryData(pieces);
                    count = pieces.size();
                    valid = true;
                }
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
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
                pieces.add(ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis()));
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
        return ArchitecturalPrimitiveSupport.createOrientedBox(center, halfExtents, frame.xAxis(), frame.yAxis(), frame.zAxis());
    }

    private boolean resolveBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return fallback;
    }
}