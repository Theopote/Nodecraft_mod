package com.nodecraft.nodesystem.nodes.spatial.modeling;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "spatial.modeling.push_pull_box_face",
    displayName = "Push/Pull Box Face",
    description = "Moves one box face along its normal and outputs a new box geometry",
    category = "spatial.modeling"
)
public class PushPullBoxFaceNode extends BaseNode {

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_FACE_INDEX_ID = "input_face_index";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_RESOLVED_FACE_INDEX_ID = "output_resolved_face_index";

    public PushPullBoxFaceNode() {
        super(UUID.randomUUID(), "spatial.modeling.push_pull_box_face");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "The source box geometry", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Optional box face to modify", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_FACE_INDEX_ID, "Face Index", "Fallback face index from 0 to 5", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed push/pull distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Modified box geometry", NodeDataType.BOX_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the requested face was resolved", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_FACE_INDEX_ID, "Resolved Face Index", "Resolved face index used for the operation", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Moves one box face along its normal and outputs a new box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object faceIndexObj = inputValues.get(INPUT_FACE_INDEX_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        BoxGeometryData result = null;
        boolean found = false;
        Integer resolvedFaceIndex = null;

        if (geometryObj instanceof BoxGeometryData geometry) {
            int faceIndex = resolveFaceIndex(faceObj, faceIndexObj);
            if (faceIndex >= 0 && faceIndex <= 5) {
                double distance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;
                result = pushPullFace(geometry, faceIndex, distance);
                found = true;
                resolvedFaceIndex = faceIndex;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, result);
        outputValues.put(OUTPUT_FOUND_ID, found);
        outputValues.put(OUTPUT_RESOLVED_FACE_INDEX_ID, resolvedFaceIndex);
    }

    private int resolveFaceIndex(Object faceObj, Object faceIndexObj) {
        if (faceObj instanceof BoxFaceData face) {
            return face.getIndex();
        }
        if (faceIndexObj instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private BoxGeometryData pushPullFace(BoxGeometryData geometry, int faceIndex, double distance) {
        Vector3d center = geometry.getCenter();
        Vector3d halfExtents = geometry.getHalfExtents();
        Matrix3d orientation = geometry.getOrientationMatrix();

        int axis = switch (faceIndex) {
            case 0, 1 -> 1;
            case 2, 3 -> 0;
            case 4, 5 -> 2;
            default -> throw new IllegalArgumentException("Unsupported box face index: " + faceIndex);
        };
        int direction = switch (faceIndex) {
            case 1, 3, 5 -> 1;
            default -> -1;
        };

        double oldHalfExtent = getHalfExtent(halfExtents, axis);
        double newHalfExtent = Math.max(0.0d, oldHalfExtent + (distance / 2.0d));
        double appliedHalfDelta = newHalfExtent - oldHalfExtent;

        setHalfExtent(halfExtents, axis, newHalfExtent);

        Vector3d localCenterOffset = new Vector3d();
        setHalfExtent(localCenterOffset, axis, direction * appliedHalfDelta);
        orientation.transform(localCenterOffset);
        center.add(localCenterOffset);

        return new BoxGeometryData(center, halfExtents, orientation, geometry.isOriented());
    }

    private double getHalfExtent(Vector3d vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            case 2 -> vector.z;
            default -> 0.0d;
        };
    }

    private void setHalfExtent(Vector3d vector, int axis, double value) {
        switch (axis) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            case 2 -> vector.z = value;
            default -> throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }
}
