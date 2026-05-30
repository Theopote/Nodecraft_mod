package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates a simple roof volume from a box face footprint.
 */
@NodeInfo(
    id = "geometry.architectural_primitives.roof_generator",
    displayName = "Roof Generator",
    description = "Generates a simple roof volume from a box face footprint",
    category = "geometry.architectural_primitives",
    order = 5
)
public class RoofGeneratorNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_ROOF_TYPE_ID = "input_roof_type";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OVERHANG_ID = "input_overhang";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RoofGeneratorNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.roof_generator");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the roof footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_ROOF_TYPE_ID, "Roof Type", "Roof type: flat, shed, gable, or hip", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Roof peak height above the footprint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness used for flat roof slabs", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OVERHANG_ID, "Overhang", "Extra overhang beyond the footprint edges", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Generated roof geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid roof could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a simple roof volume from a box face footprint";
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

                geometry = buildRoof(frame, roofType, height, thickness, overhang);
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
        double overhang
    ) {
        double roofWidth = frame.width() + 2.0d * overhang;
        double roofDepth = frame.height() + 2.0d * overhang;
        Vector3d footprintCenter = new Vector3d(frame.center());

        return switch (roofType) {
            case "flat" -> new BoxGeometryData(
                new Vector3d(footprintCenter).fma(thickness / 2.0d, frame.zAxis()),
                new Vector3d(roofWidth / 2.0d, thickness / 2.0d, roofDepth / 2.0d),
                ArchitecturalPrimitiveSupport.createOrientation(frame.xAxis(), frame.yAxis(), frame.zAxis()),
                true
            );
            case "shed" -> new PrismGeometryData(
                List.of(
                    new Vector3d(footprintCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                    new Vector3d(footprintCenter).fma(roofDepth / 2.0d, frame.yAxis()),
                    new Vector3d(footprintCenter).fma(roofDepth / 2.0d, frame.yAxis()).fma(height, frame.zAxis()),
                    new Vector3d(footprintCenter).fma(-roofDepth / 2.0d, frame.yAxis()).fma(height, frame.zAxis())
                ),
                new Vector3d(frame.xAxis()).mul(roofWidth)
            );
            case "gable" -> new PrismGeometryData(
                List.of(
                    new Vector3d(footprintCenter).fma(-roofDepth / 2.0d, frame.yAxis()),
                    new Vector3d(footprintCenter).fma(height, frame.zAxis()),
                    new Vector3d(footprintCenter).fma(roofDepth / 2.0d, frame.yAxis())
                ),
                new Vector3d(frame.xAxis()).mul(roofWidth)
            );
            case "hip" -> new SquarePyramidGeometryData(
                footprintCenter,
                frame.xAxis(),
                frame.yAxis(),
                frame.zAxis(),
                Math.max(roofWidth, roofDepth),
                height
            );
            default -> null;
        };
    }

    private String resolveRoofType(Object value) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return "gable";
    }
}