package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.deconstruct_face",
    displayName = "Deconstruct Box Face",
    description = "Extracts corners, edges, plane, center, and normal from a box face",
    category = "reference.points",
    order = 17
)
public class DeconstructBoxFaceNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";

    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_CORNERS_ID = "output_corners";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_EDGES_ID = "output_edges";
    private static final String OUTPUT_CORNER_INDICES_ID = "output_corner_indices";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructBoxFaceNode() {
        super(UUID.randomUUID(), "reference.points.deconstruct_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to deconstruct", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Face index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Face corners in winding order", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Face center point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Face normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane containing the face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_EDGES_ID, "Edges", "Face edge segments", NodeDataType.LINE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_INDICES_ID, "Corner Indices", "Indices into the parent box corner list", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when face input is a valid box face", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts corners, edges, plane, center, and normal from a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            writeInvalid();
            return;
        }

        List<Vector3d> corners = face.getCorners();
        List<LineData> edges = new ArrayList<>(corners.size());
        for (int i = 0; i < corners.size(); i++) {
            Vector3d start = corners.get(i);
            Vector3d end = corners.get((i + 1) % corners.size());
            edges.add(new LineData(
                new Vec3d(start.x, start.y, start.z),
                new Vec3d(end.x, end.y, end.z)
            ));
        }

        outputValues.put(OUTPUT_NAME_ID, face.getName());
        outputValues.put(OUTPUT_INDEX_ID, face.getIndex());
        outputValues.put(OUTPUT_CORNERS_ID, SpatialValueResolver.toPointDataList(corners));
        outputValues.put(OUTPUT_CENTER_ID, new PointData(face.getCenter()));
        outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
        outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
        outputValues.put(OUTPUT_EDGES_ID, List.copyOf(edges));
        outputValues.put(OUTPUT_CORNER_INDICES_ID, List.copyOf(face.getCornerIndices()));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_NAME_ID, null);
        outputValues.put(OUTPUT_INDEX_ID, null);
        outputValues.put(OUTPUT_CORNERS_ID, List.of());
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_EDGES_ID, List.of());
        outputValues.put(OUTPUT_CORNER_INDICES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
