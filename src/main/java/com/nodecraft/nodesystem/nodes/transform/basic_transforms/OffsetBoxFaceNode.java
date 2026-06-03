package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.offset_face",
    displayName = "Offset Box Face",
    description = "Offsets a box face along its normal without modifying the source box geometry",
    category = "transform.basic_transforms",
    order = 5
)
public class OffsetBoxFaceNode extends BaseNode {

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_POLYLINE_ID = "output_polyline";
    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_CORNERS_ID = "output_corners";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_EDGES_ID = "output_edges";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetBoxFaceNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.offset_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The box face to offset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed offset distance along the face normal", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Offset face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Closed boundary polyline of the offset face", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed ordered point list of the offset boundary", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Offset face corners", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Offset face center", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Offset face normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane of the offset face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_EDGES_ID, "Edges", "Offset face edge segments", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid offset face was produced", NodeDataType.BOOLEAN, this));
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
            writeInvalid();
            return;
        }

        double distance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;
        Vector3d normal = face.getNormal();
        if (!Double.isFinite(distance) || !isUsableVector(normal)) {
            writeInvalid();
            return;
        }
        normal.normalize();
        Vector3d offset = new Vector3d(normal).mul(distance);

        List<Vector3d> sourceCorners = face.getCorners();
        if (sourceCorners.size() < 3) {
            writeInvalid();
            return;
        }

        List<Vector3d> shiftedCorners = new ArrayList<>(sourceCorners.size());
        for (Vector3d corner : sourceCorners) {
            if (!isFinite(corner)) {
                writeInvalid();
                return;
            }
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

        List<Vec3d> polylinePoints = new ArrayList<>(shiftedCorners.size() + 1);
        List<Vector3d> closedPoints = new ArrayList<>(shiftedCorners.size() + 1);
        List<LineData> edges = new ArrayList<>(shiftedCorners.size());
        for (int i = 0; i < shiftedCorners.size(); i++) {
            Vector3d start = shiftedCorners.get(i);
            Vector3d end = shiftedCorners.get((i + 1) % shiftedCorners.size());
            polylinePoints.add(new Vec3d(start.x, start.y, start.z));
            closedPoints.add(new Vector3d(start));
            edges.add(new LineData(
                new Vec3d(start.x, start.y, start.z),
                new Vec3d(end.x, end.y, end.z)
            ));
        }
        Vector3d first = shiftedCorners.get(0);
        polylinePoints.add(new Vec3d(first.x, first.y, first.z));
        closedPoints.add(new Vector3d(first));
        PolylineData polyline = new PolylineData(polylinePoints);

        outputValues.put(OUTPUT_FACE_ID, shiftedFace);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(closedPoints));
        outputValues.put(OUTPUT_CORNERS_ID, shiftedCorners);
        outputValues.put(OUTPUT_CENTER_ID, shiftedCenter);
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_PLANE_ID, new PlaneData(shiftedCenter, normal));
        outputValues.put(OUTPUT_EDGES_ID, edges);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FACE_ID, null);
        outputValues.put(OUTPUT_POLYLINE_ID, null);
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_CORNERS_ID, List.of());
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_EDGES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private boolean isUsableVector(Vector3d vector) {
        return isFinite(vector) && vector.lengthSquared() > 1.0e-12d;
    }

    private boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }
}
