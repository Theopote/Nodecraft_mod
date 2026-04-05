package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.deconstruct_box_face",
    displayName = "Deconstruct Box Face",
    description = "Extracts corners, edges, plane, center, and normal from a box face",
    category = "spatial.analysis"
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

    public DeconstructBoxFaceNode() {
        super(UUID.randomUUID(), "spatial.analysis.deconstruct_box_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to deconstruct", NodeDataType.BOX_FACE, this));

        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Face index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Face corners in winding order", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Face center point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Face normal vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane containing the face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_EDGES_ID, "Edges", "Face edge segments", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNER_INDICES_ID, "Corner Indices", "Indices into the parent box corner list", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Extracts corners, edges, plane, center, and normal from a box face";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        if (!(faceObj instanceof BoxFaceData face)) {
            outputValues.put(OUTPUT_NAME_ID, null);
            outputValues.put(OUTPUT_INDEX_ID, null);
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, null);
            outputValues.put(OUTPUT_PLANE_ID, null);
            outputValues.put(OUTPUT_EDGES_ID, List.of());
            outputValues.put(OUTPUT_CORNER_INDICES_ID, List.of());
            return;
        }

        List<Vector3d> corners = face.getCorners();
        List<LineData> edges = new ArrayList<>(4);
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
        outputValues.put(OUTPUT_CORNERS_ID, corners);
        outputValues.put(OUTPUT_CENTER_ID, face.getCenter());
        outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
        outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
        outputValues.put(OUTPUT_EDGES_ID, edges);
        outputValues.put(OUTPUT_CORNER_INDICES_ID, face.getCornerIndices());
    }
}
