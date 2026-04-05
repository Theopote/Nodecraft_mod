package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.offset_box_face",
    displayName = "Offset Box Face",
    description = "Offsets a box face along its normal without modifying the source box geometry",
    category = "spatial.analysis"
)
public class OffsetBoxFaceNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_CORNERS_ID = "output_corners";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_EDGES_ID = "output_edges";

    public OffsetBoxFaceNode() {
        super(UUID.randomUUID(), "spatial.analysis.offset_box_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to offset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Offset face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Offset face corners", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Offset face center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Offset face normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane of the offset face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_EDGES_ID, "Edges", "Offset face edge segments", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a box face along its normal without modifying the source box geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            outputValues.put(OUTPUT_FACE_ID, null);
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, null);
            outputValues.put(OUTPUT_PLANE_ID, null);
            outputValues.put(OUTPUT_EDGES_ID, List.of());
            return;
        }

        double distance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;
        Vector3d normal = face.getNormal();
        Vector3d offset = new Vector3d(normal).mul(distance);

        List<Vector3d> shiftedCorners = new ArrayList<>(4);
        for (Vector3d corner : face.getCorners()) {
            shiftedCorners.add(new Vector3d(corner).add(offset));
        }

        Vector3d shiftedCenter = new Vector3d(face.getCenter()).add(offset);
        BoxFaceData shiftedFace = new BoxFaceData(
            face.getIndex(),
            face.getName(),
            face.getCornerIndices(),
            shiftedCorners,
            shiftedCenter,
            normal
        );

        List<LineData> edges = new ArrayList<>(4);
        for (int i = 0; i < shiftedCorners.size(); i++) {
            Vector3d start = shiftedCorners.get(i);
            Vector3d end = shiftedCorners.get((i + 1) % shiftedCorners.size());
            edges.add(new LineData(
                new Vec3d(start.x, start.y, start.z),
                new Vec3d(end.x, end.y, end.z)
            ));
        }

        outputValues.put(OUTPUT_FACE_ID, shiftedFace);
        outputValues.put(OUTPUT_CORNERS_ID, shiftedCorners);
        outputValues.put(OUTPUT_CENTER_ID, shiftedCenter);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_PLANE_ID, new PlaneData(shiftedCenter, normal));
        outputValues.put(OUTPUT_EDGES_ID, edges);
    }
}
