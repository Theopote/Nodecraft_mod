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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Host floor slab from a BOX_FACE footprint (no beams).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.floor_slab",
    displayName = "Floor Slab",
    description = "Generates a floor slab from a box face footprint",
    category = "geometry.architectural_primitives",
    order = 12
)
public class FloorSlabNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_THICKNESS_ID = "input_thickness";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_TOP_FACE_ID = "output_top_face";
    private static final String OUTPUT_BOTTOM_FACE_ID = "output_bottom_face";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FloorSlabNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.floor_slab");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the slab footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_THICKNESS_ID, "Thickness", "Thickness of the floor slab", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Floor slab solid", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_TOP_FACE_ID, "Top Face", "Top face of the slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_FACE_ID, "Bottom Face", "Bottom face of the slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid slab could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a floor slab from a box face footprint";
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

        Double thickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(this, INPUT_THICKNESS_ID, 0.3d);
        if (thickness == null) {
            writeInvalid("Thickness must be a positive finite number");
            return;
        }

        BoxGeometryData slab = FloorStructureSupport.createSlab(frame, thickness);
        GeometryData geometry = GeometryOutputUtils.packGeometry(List.of(slab));
        if (geometry == null) {
            writeInvalid("Could not generate floor slab geometry");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_TOP_FACE_ID, FloorStructureSupport.topFace(frame, thickness));
        outputValues.put(OUTPUT_BOTTOM_FACE_ID, FloorStructureSupport.bottomFace(frame));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues, OUTPUT_GEOMETRY_ID, OUTPUT_TOP_FACE_ID, OUTPUT_BOTTOM_FACE_ID);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}
