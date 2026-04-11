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
    id = "transform.basic_transforms.inset_face",
    displayName = "Inset Box Face",
    description = "Creates an inset or outset reference face boundary from a box face without modifying the source box",
    category = "transform.basic_transforms"
)
public class InsetBoxFaceNode extends BaseNode {

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
    private static final String OUTPUT_EFFECTIVE_DISTANCE_ID = "output_effective_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public InsetBoxFaceNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.inset_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face to inset or outset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Signed inset distance; negative values expand the face outward", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Inset reference face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_POLYLINE_ID, "Polyline", "Closed boundary polyline of the inset face", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Closed ordered point list of the inset boundary", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CORNERS_ID, "Corners", "Inset face corners in winding order", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Center of the inset face", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Face normal", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Plane containing the inset face", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_EDGES_ID, "Edges", "Inset face edges", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_DISTANCE_ID, "Effective Distance", "Distance actually applied after clamping", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether a valid inset face was produced", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Creates an inset or outset reference face boundary from a box face without modifying the source box";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object distanceObj = inputValues.get(INPUT_DISTANCE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            outputValues.put(OUTPUT_FACE_ID, null);
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, null);
            outputValues.put(OUTPUT_PLANE_ID, null);
            outputValues.put(OUTPUT_EDGES_ID, List.of());
            outputValues.put(OUTPUT_EFFECTIVE_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Vector3d> corners = face.getCorners();
        if (corners.size() != 4) {
            outputValues.put(OUTPUT_FACE_ID, null);
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
            outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
            outputValues.put(OUTPUT_EDGES_ID, List.of());
            outputValues.put(OUTPUT_EFFECTIVE_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double requestedDistance = distanceObj instanceof Number number ? number.doubleValue() : 0.0d;

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d uAxis = new Vector3d(c1).sub(c0);
        Vector3d vAxis = new Vector3d(c3).sub(c0);
        double width = uAxis.length();
        double height = vAxis.length();

        if (width <= 1.0e-9d || height <= 1.0e-9d) {
            outputValues.put(OUTPUT_FACE_ID, null);
            outputValues.put(OUTPUT_POLYLINE_ID, null);
            outputValues.put(OUTPUT_POINTS_ID, List.of());
            outputValues.put(OUTPUT_CORNERS_ID, List.of());
            outputValues.put(OUTPUT_CENTER_ID, null);
            outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
            outputValues.put(OUTPUT_PLANE_ID, face.getPlane());
            outputValues.put(OUTPUT_EDGES_ID, List.of());
            outputValues.put(OUTPUT_EFFECTIVE_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        uAxis.normalize();
        vAxis.normalize();

        double maxInset = Math.min(width, height) * 0.5d;
        double effectiveDistance = Math.max(-Double.MAX_VALUE, Math.min(requestedDistance, maxInset));

        List<Vector3d> insetCorners = List.of(
            new Vector3d(c0).add(new Vector3d(uAxis).mul(effectiveDistance)).add(new Vector3d(vAxis).mul(effectiveDistance)),
            new Vector3d(corners.get(1)).sub(new Vector3d(uAxis).mul(effectiveDistance)).add(new Vector3d(vAxis).mul(effectiveDistance)),
            new Vector3d(corners.get(2)).sub(new Vector3d(uAxis).mul(effectiveDistance)).sub(new Vector3d(vAxis).mul(effectiveDistance)),
            new Vector3d(corners.get(3)).add(new Vector3d(uAxis).mul(effectiveDistance)).sub(new Vector3d(vAxis).mul(effectiveDistance))
        );

        Vector3d insetCenter = new Vector3d();
        for (Vector3d corner : insetCorners) {
            insetCenter.add(corner);
        }
        insetCenter.div(4.0d);

        BoxFaceData insetFace = new BoxFaceData(
            face.getIndex(),
            face.getName(),
            face.getCornerIndices(),
            insetCorners,
            insetCenter,
            face.getNormal()
        );

        List<Vec3d> polylinePoints = new ArrayList<>(5);
        List<Vector3d> closedPoints = new ArrayList<>(5);
        List<LineData> edges = new ArrayList<>(4);
        for (int i = 0; i < insetCorners.size(); i++) {
            Vector3d start = insetCorners.get(i);
            Vector3d end = insetCorners.get((i + 1) % insetCorners.size());
            polylinePoints.add(new Vec3d(start.x, start.y, start.z));
            closedPoints.add(new Vector3d(start));
            edges.add(new LineData(
                new Vec3d(start.x, start.y, start.z),
                new Vec3d(end.x, end.y, end.z)
            ));
        }
        Vector3d first = insetCorners.get(0);
        polylinePoints.add(new Vec3d(first.x, first.y, first.z));
        closedPoints.add(new Vector3d(first));

        PolylineData polyline = new PolylineData(polylinePoints);

        outputValues.put(OUTPUT_FACE_ID, insetFace);
        outputValues.put(OUTPUT_POLYLINE_ID, polyline);
        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(closedPoints));
        outputValues.put(OUTPUT_CORNERS_ID, insetCorners);
        outputValues.put(OUTPUT_CENTER_ID, insetCenter);
        outputValues.put(OUTPUT_NORMAL_ID, face.getNormal());
        outputValues.put(OUTPUT_PLANE_ID, new PlaneData(insetCenter, face.getNormal()));
        outputValues.put(OUTPUT_EDGES_ID, edges);
        outputValues.put(OUTPUT_EFFECTIVE_DISTANCE_ID, effectiveDistance);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
