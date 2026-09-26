package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Orients geometry to match a frame's axes while keeping the pivot world position fixed.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.orient_geometry_to_frame",
    displayName = "Orient Geometry To Frame",
    description = "Rotates geometry so local axes match FRAME X/Y/Z while keeping the pivot point fixed in world space",
    category = "transform.placement",
    order = 2
)
public class OrientGeometryToFrameNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_FRAME_ID = "input_frame";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OrientGeometryToFrameNode() {
        super(UUID.randomUUID(), "transform.placement.orient_geometry_to_frame");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to orient", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "World-space pivot that stays fixed during orientation", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Target orientation frame", NodeDataType.FRAME, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Oriented geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when orientation fails", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when orientation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rotates geometry so local axes match FRAME X/Y/Z while keeping the pivot point fixed in world space";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, false, "Missing geometry input");
            return;
        }
        if (!(frameObj instanceof FrameData frame)) {
            writeResult(null, false, "Missing frame input");
            return;
        }

        Vector3d pivot = OptionalPortDrive.resolveOptionalPoint(this, INPUT_PIVOT_ID, new Vector3d());
        if (pivot == null) {
            writeResult(null, false, "Pivot connected but invalid");
            return;
        }

        FrameData basis = frame.orthonormalized();
        if (basis == null) {
            writeResult(null, false, "Frame axes are invalid");
            return;
        }

        Matrix3d rotation = basis.toRotationMatrix();
        GeometryData oriented = GeometryTransform.transformAround(geometry, pivot, rotation, 1.0d);
        writeResult(oriented, oriented != null, oriented == null ? "Unsupported geometry orientation" : "");
    }

    private void writeResult(@Nullable GeometryData geometry, boolean valid, String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
