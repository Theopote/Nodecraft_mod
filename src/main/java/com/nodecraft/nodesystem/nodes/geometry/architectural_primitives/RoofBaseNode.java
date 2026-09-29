package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core roof host: flat / shed / gable only.
 * Use Roof Generator for advanced specialty shapes.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.roof_base",
    displayName = "Roof Base",
    description = "Generates a core roof (flat, shed, or gable) from a box face footprint",
    category = "geometry.architectural_primitives",
    order = 4
)
public class RoofBaseNode extends BaseNode {

    private static final Set<String> ROOF_TYPES = Set.of("flat", "shed", "gable");
    private static final Set<String> RIDGE_DIRECTIONS = Set.of("x", "y");

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_ROOF_TYPE_ID = "input_roof_type";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_THICKNESS_ID = "input_thickness";
    private static final String INPUT_OVERHANG_ID = "input_overhang";
    private static final String INPUT_RIDGE_DIRECTION_ID = "input_ridge_direction";
    private static final String INPUT_EAVE_DROP_ID = "input_eave_drop";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_EAVE_PATH_ID = "output_eave_path";
    private static final String OUTPUT_RIDGE_PATH_ID = "output_ridge_path";
    private static final String OUTPUT_EAVES_ID = "output_eaves";
    private static final String OUTPUT_RIDGES_ID = "output_ridges";
    private static final String OUTPUT_VALLEYS_ID = "output_valleys";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RoofBaseNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.roof_base");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the roof footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_ROOF_TYPE_ID, "Roof Type",
            "Core roof type: flat, shed, or gable", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Roof peak height above the footprint", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness used for flat roof slabs", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OVERHANG_ID, "Overhang", "Extra overhang beyond the footprint edges", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RIDGE_DIRECTION_ID, "Ridge Direction", "Ridge direction: x or y", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_EAVE_DROP_ID, "Eave Drop", "Drops the eave edge below the footprint plane", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Core roof geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_EAVE_PATH_ID, "Primary Eave Path", "Primary eave edge path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_RIDGE_PATH_ID, "Ridge Path", "Ridge path for gable roofs", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_EAVES_ID, "Eaves", "All eave edge paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RIDGES_ID, "Ridges", "All ridge paths", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALLEYS_ID, "Valleys", "Valley paths when applicable", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid roof could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a core roof (flat, shed, or gable) from a box face footprint";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BoxFaceData face = ArchitecturalInputUtils.resolveRequiredFace(this, INPUT_FACE_ID);
        if (face == null) {
            writeInvalid(ArchitecturalInputUtils.isConnected(this, INPUT_FACE_ID)
                ? "Face must be a valid BOX_FACE with a resolvable frame"
                : "Face is required");
            return;
        }

        ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
        if (frame == null) {
            writeInvalid("Face must be a valid BOX_FACE with a resolvable frame");
            return;
        }

        String roofType = ArchitecturalInputUtils.resolveKnownStringEnum(this, INPUT_ROOF_TYPE_ID, "gable", ROOF_TYPES);
        if (roofType == null) {
            writeInvalid("Roof Type must be one of: flat, shed, gable");
            return;
        }
        Double height = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_HEIGHT_ID, 2.0d);
        if (height == null) {
            writeInvalid("Height must be a finite positive DOUBLE");
            return;
        }
        Double thickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_THICKNESS_ID, 0.25d);
        if (thickness == null) {
            writeInvalid("Thickness must be a finite positive DOUBLE");
            return;
        }
        Double overhang = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_OVERHANG_ID, 0.0d);
        if (overhang == null) {
            writeInvalid("Overhang must be a finite non-negative DOUBLE");
            return;
        }
        String ridgeDirection = ArchitecturalInputUtils.resolveKnownStringEnum(
            this, INPUT_RIDGE_DIRECTION_ID, "x", RIDGE_DIRECTIONS);
        if (ridgeDirection == null) {
            writeInvalid("Ridge Direction must be one of: x, y");
            return;
        }
        Double eaveDrop = ArchitecturalInputUtils.resolveOptionalNonNegativeFiniteDouble(this, INPUT_EAVE_DROP_ID, 0.0d);
        if (eaveDrop == null) {
            writeInvalid("Eave Drop must be a finite non-negative DOUBLE");
            return;
        }

        RoofGeometrySupport.RoofResult result = RoofGeometrySupport.buildCoreRoof(
            new RoofGeometrySupport.RoofLayout(
                frame, roofType, height, thickness, overhang, ridgeDirection, eaveDrop
            )
        );
        if (result.geometry() == null) {
            writeInvalid("Unable to generate roof geometry for the given face and parameters");
            return;
        }

        writeSuccess(result);
    }

    private void writeSuccess(RoofGeometrySupport.RoofResult result) {
        RoofGeometrySupport.RoofTopology topology = result.topology();
        outputValues.put(OUTPUT_GEOMETRY_ID, result.geometry());
        outputValues.put(OUTPUT_EAVE_PATH_ID, topology.primaryEave());
        outputValues.put(OUTPUT_RIDGE_PATH_ID, topology.primaryRidge());
        outputValues.put(OUTPUT_EAVES_ID, topology.eaves());
        outputValues.put(OUTPUT_RIDGES_ID, topology.ridges());
        outputValues.put(OUTPUT_VALLEYS_ID, topology.valleys());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_EAVE_PATH_ID, null);
        outputValues.put(OUTPUT_RIDGE_PATH_ID, null);
        outputValues.put(OUTPUT_EAVES_ID, List.of());
        outputValues.put(OUTPUT_RIDGES_ID, List.of());
        outputValues.put(OUTPUT_VALLEYS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
