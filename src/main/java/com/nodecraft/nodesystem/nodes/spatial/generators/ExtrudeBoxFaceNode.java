package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.generators.extrude_box_face",
    displayName = "Extrude Box Face",
    description = "Extrudes a box face into a new box segment and returns a composite geometry",
    category = "spatial.generators"
)
public class ExtrudeBoxFaceNode extends BaseNode {

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_FACE_INDEX_ID = "input_face_index";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_EXTRUDED_BOX_ID = "output_extruded_box";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_RESOLVED_FACE_INDEX_ID = "output_resolved_face_index";

    public ExtrudeBoxFaceNode() {
        super(UUID.randomUUID(), "spatial.generators.extrude_box_face");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "The source box geometry", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Optional box face to extrude", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_FACE_INDEX_ID, "Face Index", "Fallback face index from 0 to 5", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed extrusion distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Composite geometry containing the source and extruded segment", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_EXTRUDED_BOX_ID, "Extruded Box", "The generated extrusion segment as box geometry", NodeDataType.BOX_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the requested face was resolved", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_FACE_INDEX_ID, "Resolved Face Index", "Resolved face index used for the extrusion", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Extrudes a box face into a new box segment and returns a composite geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object faceIndexObj = inputValues.get(INPUT_FACE_INDEX_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        GeometryData resultGeometry = null;
        BoxGeometryData extrudedBox = null;
        boolean found = false;
        Integer resolvedFaceIndex = null;

        if (geometryObj instanceof BoxGeometryData sourceGeometry) {
            int faceIndex = resolveFaceIndex(faceObj, faceIndexObj);
            if (faceIndex >= 0 && faceIndex <= 5) {
                double distance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;
                extrudedBox = createExtrudedBox(sourceGeometry, faceIndex, distance);
                if (extrudedBox != null) {
                    resultGeometry = new CompositeGeometryData(List.of(sourceGeometry, extrudedBox));
                } else {
                    resultGeometry = sourceGeometry;
                }
                found = true;
                resolvedFaceIndex = faceIndex;
            }
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, resultGeometry);
        outputValues.put(OUTPUT_EXTRUDED_BOX_ID, extrudedBox);
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

    private BoxGeometryData createExtrudedBox(BoxGeometryData geometry, int faceIndex, double distance) {
        if (Math.abs(distance) < 1.0e-9d) {
            return null;
        }

        Vector3d sourceCenter = geometry.getCenter();
        Vector3d sourceHalfExtents = geometry.getHalfExtents();
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

        double sourceHalf = getAxisValue(sourceHalfExtents, axis);
        double extrusionHalf = Math.abs(distance) / 2.0d;
        Vector3d extrudedHalfExtents = new Vector3d(sourceHalfExtents);
        setAxisValue(extrudedHalfExtents, axis, extrusionHalf);

        Vector3d localCenterOffset = new Vector3d();
        setAxisValue(localCenterOffset, axis, direction * sourceHalf + (distance / 2.0d));
        orientation.transform(localCenterOffset);

        Vector3d extrudedCenter = new Vector3d(sourceCenter).add(localCenterOffset);
        return new BoxGeometryData(extrudedCenter, extrudedHalfExtents, orientation, geometry.isOriented());
    }

    private double getAxisValue(Vector3d vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            case 2 -> vector.z;
            default -> throw new IllegalArgumentException("Unsupported axis: " + axis);
        };
    }

    private void setAxisValue(Vector3d vector, int axis, double value) {
        switch (axis) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            case 2 -> vector.z = value;
            default -> throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }
}
