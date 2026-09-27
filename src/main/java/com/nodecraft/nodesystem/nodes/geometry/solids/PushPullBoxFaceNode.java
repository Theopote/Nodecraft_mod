package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.push_pull_face",
    displayName = "Push/Pull Box Face",
    description = "Moves one box face along its normal and outputs a new box geometry",
    category = "geometry.solids",
    order = 4
)
public class PushPullBoxFaceNode extends AbstractSolidNode {

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_FACE_INDEX_ID = "input_face_index";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_RESOLVED_FACE_INDEX_ID = "output_resolved_face_index";

    public PushPullBoxFaceNode() {
        super(UUID.randomUUID(), "geometry.solids.push_pull_face");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "The source box geometry", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Optional box face to modify", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_FACE_INDEX_ID, "Face Index", "Fallback face index from 0 to 5", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed push/pull distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Modified box geometry", NodeDataType.BOX_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the requested face was resolved", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_FACE_INDEX_ID, "Resolved Face Index", "Resolved face index used for the operation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when push/pull succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Moves one box face along its normal and outputs a new box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        if (!(geometryObj instanceof BoxGeometryData geometry)) {
            invalidate("Box geometry is missing or invalid");
            return;
        }

        int faceIndex = SolidNodeUtils.resolveBoxFaceIndex(
            inputValues.get(INPUT_FACE_ID),
            inputValues.get(INPUT_FACE_INDEX_ID)
        );
        if (faceIndex < 0 || faceIndex > 5) {
            invalidate("Face index must be between 0 and 5");
            return;
        }

        Double distanceObj = resolveFiniteDouble(INPUT_DISTANCE_ID, 0.0d);
        if (distanceObj == null) {
            invalidate("Distance is connected but invalid (must be finite)");
            return;
        }

        BoxGeometryData result = pushPullFace(geometry, faceIndex, distanceObj);

        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_RESOLVED_FACE_INDEX_ID, faceIndex);
        markSuccess();
    }

    private BoxGeometryData pushPullFace(BoxGeometryData geometry, int faceIndex, double distance) {
        Vector3d center = geometry.getCenter();
        Vector3d halfExtents = geometry.getHalfExtents();
        Matrix3d orientation = geometry.getOrientationMatrix();

        SolidNodeUtils.BoxFaceMapping mapping = SolidNodeUtils.resolveBoxFaceMapping(faceIndex);

        double oldHalfExtent = SolidNodeUtils.getAxisValue(halfExtents, mapping.axis());
        double newHalfExtent = Math.max(0.0d, oldHalfExtent + (distance / 2.0d));
        double appliedHalfDelta = newHalfExtent - oldHalfExtent;

        SolidNodeUtils.setAxisValue(halfExtents, mapping.axis(), newHalfExtent);

        Vector3d localCenterOffset = new Vector3d();
        SolidNodeUtils.setAxisValue(localCenterOffset, mapping.axis(), mapping.direction() * appliedHalfDelta);
        orientation.transform(localCenterOffset);
        center.add(localCenterOffset);

        return new BoxGeometryData(center, halfExtents, orientation, geometry.isOriented());
    }

    private void invalidate(String error) {
        putNullOutputs(OUTPUT_GEOMETRY_ID, OUTPUT_BOX_GEOMETRY_ID);
        outputValues.put(OUTPUT_FOUND_ID, false);
        putIntOutputs(-1, OUTPUT_RESOLVED_FACE_INDEX_ID);
        markInvalid(error);
    }
}
