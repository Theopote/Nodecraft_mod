package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "geometry.curves.face_boundary_curve",
    displayName = "Box Face Boundary Path",
    description = "Builds a closed boundary path from a box face for preview and downstream path workflows",
    category = "geometry.curves",
    order = 3
)
public class BoxFaceBoundaryPathNode extends AbstractCurveNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CORNER_INDICES_ID = "output_corner_indices";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public BoxFaceBoundaryPathNode() {
        super(UUID.randomUUID(), "geometry.curves.face_boundary_curve");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face to convert into a closed boundary path", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Closed boundary polyline of the face", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed ordered point list of the face boundary", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_INDICES_ID, "Corner Indices", "Corner indices in winding order for the face boundary", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Face index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid face was provided", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_CORNER_INDICES_ID, List.of());
            outputValues.put(OUTPUT_NAME_ID, null);
            outputValues.put(OUTPUT_INDEX_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Vector3d> corners = face.getCorners();
        List<Vector3d> closedPoints = new ArrayList<>(corners.size() + 1);
        List<Vec3d> polylinePoints = new ArrayList<>(corners.size() + 1);
        for (Vector3d corner : corners) {
            closedPoints.add(new Vector3d(corner));
            polylinePoints.add(new Vec3d(corner.x, corner.y, corner.z));
        }
        if (!corners.isEmpty()) {
            Vector3d first = corners.get(0);
            closedPoints.add(new Vector3d(first));
            polylinePoints.add(new Vec3d(first.x, first.y, first.z));
        }

        PolylineData polyline = polylinePoints.size() >= 2 ? new PolylineData(polylinePoints) : null;

        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(closedPoints));
        outputValues.put(OUTPUT_CORNER_INDICES_ID, face.getCornerIndices());
        outputValues.put(OUTPUT_NAME_ID, face.getName());
        outputValues.put(OUTPUT_INDEX_ID, face.getIndex());
        outputValues.put(OUTPUT_VALID_ID, polyline != null);
    }
}
