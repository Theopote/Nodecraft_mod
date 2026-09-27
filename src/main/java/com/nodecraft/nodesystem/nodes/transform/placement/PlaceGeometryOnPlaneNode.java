package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Places geometry onto a plane by building a frame (Z = plane normal, X from optional hint).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.place_geometry_on_plane",
    displayName = "Place Geometry On Plane",
    description = "Places geometry onto a plane: builds a FRAME (Z=normal, X from hint) then maps pivot to plane origin",
    category = "transform.placement",
    order = 1
)
public class PlaceGeometryOnPlaneNode extends AbstractPlacementNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_HINT_ID = "input_x_hint";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_FRAME_ID = "output_frame";

    public PlaceGeometryOnPlaneNode() {
        super("transform.placement.place_geometry_on_plane");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to place", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "Local pivot point that maps to the plane origin", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Target plane (origin + normal)", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_HINT_ID, "X Hint", "Optional in-plane X direction hint", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Placed geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Frame built from the plane", NodeDataType.FRAME, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Places geometry onto a plane: builds a FRAME (Z=normal, X from hint) then maps pivot to plane origin";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, null, "Missing geometry input");
            return;
        }
        if (!(planeObj instanceof PlaneData plane)) {
            writeResult(null, null, "Missing plane input");
            return;
        }

        Vector3d pivot = OptionalPortDrive.resolveOptionalPoint(this, INPUT_PIVOT_ID, new Vector3d());
        if (pivot == null) {
            writeResult(null, null, "Pivot connected but invalid");
            return;
        }

        FrameData frame;
        if (OptionalPortDrive.isConnected(this, INPUT_X_HINT_ID)) {
            Vector3d xHint = OptionalPortDrive.resolveOptionalVector(this, INPUT_X_HINT_ID, null);
            if (xHint == null) {
                writeResult(null, null, "X Hint connected but invalid");
                return;
            }
            frame = FrameUtils.fromPlaneRequireHint(plane, xHint);
            if (frame == null) {
                writeResult(null, null, "X Hint has zero length when projected onto the plane");
                return;
            }
        } else {
            frame = FrameUtils.fromPlane(plane, null);
            if (frame == null) {
                writeResult(null, null, "Could not build orthonormal frame on plane");
                return;
            }
        }

        GeometryData placed = PlaceGeometryOnFramesNode.placeOnFrame(geometry, pivot, frame);
        writeResult(placed, frame, placed == null ? "Unsupported geometry placement" : null);
    }

    private void writeResult(@Nullable GeometryData geometry, @Nullable FrameData frame, @Nullable String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_FRAME_ID, frame);
        if (error == null || error.isEmpty()) {
            markSuccess();
        } else {
            markInvalid(error);
        }
    }
}
