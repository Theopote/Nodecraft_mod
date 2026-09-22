package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

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
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        addOutputPort(new BasePort(OUTPUT_EAVE_PATH_ID, "Eave Path", "Primary eave edge path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_RIDGE_PATH_ID, "Ridge Path", "Ridge path for gable roofs", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid roof could be generated", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Generates a core roof (flat, shed, or gable) from a box face footprint";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        GeometryData geometry = null;
        PathData eavePath = null;
        PathData ridgePath = null;
        boolean valid = false;

        if (inputValues.get(INPUT_FACE_ID) instanceof BoxFaceData face) {
            ArchitecturalPrimitiveSupport.FaceFrame frame = ArchitecturalPrimitiveSupport.resolveFaceFrame(face);
            if (frame != null) {
                RoofGeometrySupport.RoofResult result = RoofGeometrySupport.buildCoreRoof(
                    new RoofGeometrySupport.RoofLayout(
                        frame,
                        RoofGeometrySupport.resolveCoreRoofType(inputValues.get(INPUT_ROOF_TYPE_ID)),
                        ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_HEIGHT_ID), 2.0d),
                        ArchitecturalPrimitiveSupport.resolvePositiveDouble(inputValues.get(INPUT_THICKNESS_ID), 0.25d),
                        ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_OVERHANG_ID), 0.0d),
                        RoofGeometrySupport.resolveRidgeDirection(inputValues.get(INPUT_RIDGE_DIRECTION_ID)),
                        ArchitecturalPrimitiveSupport.resolveNonNegativeDouble(inputValues.get(INPUT_EAVE_DROP_ID), 0.0d)
                    )
                );
                geometry = result.geometry();
                eavePath = result.eavePath();
                ridgePath = result.ridgePath();
                valid = geometry != null;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_EAVE_PATH_ID, eavePath);
        outputValues.put(OUTPUT_RIDGE_PATH_ID, ridgePath);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
