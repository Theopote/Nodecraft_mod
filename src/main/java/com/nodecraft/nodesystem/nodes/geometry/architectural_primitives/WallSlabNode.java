package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ArchitecturalInputUtils;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pure wall host slab from a BOX_FACE footprint (no openings).
 * <p>
 * Slab grows on the <strong>+face normal (outward)</strong> side of the face
 * (0..thickness). Pair with Window/Door Array + Difference when openings are
 * owned by a separate array node.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.architectural_primitives.wall_slab",
    displayName = "Wall Slab",
    description = "Generates a solid wall slab from a box face (no openings; use Window Array + Difference to cut)",
    category = "geometry.architectural_primitives",
    order = 19
)
public class WallSlabNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_WALL_THICKNESS_ID = "input_wall_thickness";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_TOP_EDGE_ID = "output_top_edge";
    private static final String OUTPUT_BOTTOM_EDGE_ID = "output_bottom_edge";
    private static final String OUTPUT_CENTER_LINE_ID = "output_center_line";
    private static final String OUTPUT_EXTERIOR_FACE_ID = "output_exterior_face";
    private static final String OUTPUT_INTERIOR_FACE_ID = "output_interior_face";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public WallSlabNode() {
        super(UUID.randomUUID(), "geometry.architectural_primitives.wall_slab");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face used as the wall footprint", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_WALL_THICKNESS_ID, "Wall Thickness", "Thickness of the wall slab", NodeDataType.DOUBLE, this, false, false));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Wall Geometry", "Solid wall slab", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_TOP_EDGE_ID, "Top Edge", "Top edge path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_BOTTOM_EDGE_ID, "Bottom Edge", "Bottom edge path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_LINE_ID, "Center Line", "Horizontal centerline path of the wall face", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_EXTERIOR_FACE_ID, "Exterior Face", "Exterior face of the wall slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_INTERIOR_FACE_ID, "Interior Face", "Interior face of the wall slab", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid wall could be generated", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Generates a solid wall slab from a box face (no openings; use Window Array + Difference to cut)";
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

        Double wallThickness = ArchitecturalInputUtils.resolveOptionalPositiveFiniteDouble(
            this, INPUT_WALL_THICKNESS_ID, 0.4d);
        if (wallThickness == null) {
            writeInvalid("Wall Thickness must be a positive finite number");
            return;
        }

        GeometryData wallGeometry = WallHostSupport.createWallSlab(frame, wallThickness);
        if (wallGeometry == null) {
            writeInvalid("Could not generate wall slab geometry");
            return;
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, wallGeometry);
        outputValues.put(OUTPUT_TOP_EDGE_ID, WallHostSupport.edgePath(frame, frame.height() / 2.0d));
        outputValues.put(OUTPUT_BOTTOM_EDGE_ID, WallHostSupport.edgePath(frame, -frame.height() / 2.0d));
        outputValues.put(OUTPUT_CENTER_LINE_ID, WallHostSupport.edgePath(frame, 0.0d));
        outputValues.put(OUTPUT_EXTERIOR_FACE_ID, WallHostSupport.exteriorFace(frame));
        outputValues.put(OUTPUT_INTERIOR_FACE_ID, WallHostSupport.interiorFace(frame, wallThickness));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        ArchitecturalNodeOutputs.putNull(outputValues,
            OUTPUT_GEOMETRY_ID, OUTPUT_TOP_EDGE_ID, OUTPUT_BOTTOM_EDGE_ID,
            OUTPUT_CENTER_LINE_ID, OUTPUT_EXTERIOR_FACE_ID, OUTPUT_INTERIOR_FACE_ID);
        ArchitecturalNodeOutputs.markInvalid(outputValues, error);
    }
}
