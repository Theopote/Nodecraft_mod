package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryTransform;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Applies FRAME rotation relative to existing geometry coordinates while keeping the pivot fixed.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.orient_geometry_to_frame",
    displayName = "Apply Frame Orientation",
    description = "Applies FRAME rotation relative to existing geometry coordinates while keeping the pivot fixed in world space (not an absolute set-to-frame)",
    category = "transform.placement",
    order = 2
)
public class OrientGeometryToFrameNode extends AbstractPlacementNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_PIVOT_ID = "input_pivot";
    private static final String INPUT_FRAME_ID = "input_frame";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";

    public OrientGeometryToFrameNode() {
        super("transform.placement.orient_geometry_to_frame");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Geometry to rotate by frame orientation", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_PIVOT_ID, "Pivot", "World-space pivot that stays fixed during orientation", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_FRAME_ID, "Frame", "Frame whose rotation is applied relative to the geometry", NodeDataType.FRAME, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Geometry after applying frame rotation about the pivot", NodeDataType.GEOMETRY, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDisplayName() {
        return "Apply Frame Orientation";
    }

    @Override
    public String getDescription() {
        return "Applies FRAME rotation relative to existing geometry coordinates while keeping the pivot fixed in world space (not an absolute set-to-frame)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object frameObj = inputValues.get(INPUT_FRAME_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            writeResult(null, "Missing geometry input");
            return;
        }
        if (!(frameObj instanceof FrameData frame)) {
            writeResult(null, "Missing frame input");
            return;
        }

        Vector3d pivot = OptionalPortDrive.resolveOptionalPoint(this, INPUT_PIVOT_ID, new Vector3d());
        if (pivot == null) {
            writeResult(null, "Pivot connected but invalid");
            return;
        }

        FrameData basis = frame.orthonormalized();
        if (basis == null) {
            writeResult(null, "Frame axes are invalid");
            return;
        }

        Matrix3d rotation = basis.toRotationMatrix();
        GeometryData oriented = GeometryTransform.transformAround(geometry, pivot, rotation, 1.0d);
        writeResult(oriented, oriented == null ? "Unsupported geometry orientation" : null);
    }

    private void writeResult(@Nullable GeometryData geometry, @Nullable String error) {
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        if (error == null || error.isEmpty()) {
            markSuccess();
        } else {
            markInvalid(error);
        }
    }
}
