package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.basic_transforms.inset_face",
    displayName = "Inset Box Face",
    description = "Creates an inset or outset reference face boundary from a box face without modifying the source box",
    category = "transform.basic_transforms",
    order = 8
)
public class InsetBoxFaceNode extends BaseNode {

    private static final double EPS = 1.0e-9d;

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_DISTANCE_ID = "input_distance";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public InsetBoxFaceNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.inset_face");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "Box face to inset or outset", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_DISTANCE_ID, "Distance", "Required signed inset distance; negative values expand the face outward", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Inset reference face", NodeDataType.BOX_FACE, this));
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

        if (!(faceObj instanceof BoxFaceData face) || !(distanceObj instanceof Number number)) {
            writeInvalid();
            return;
        }

        double distance = number.doubleValue();
        if (!Double.isFinite(distance)) {
            writeInvalid();
            return;
        }

        List<Vector3d> corners = face.getCorners();
        if (corners.size() != 4) {
            writeInvalid();
            return;
        }
        for (Vector3d corner : corners) {
            if (!VectorUtils.isFinite(corner)) {
                writeInvalid();
                return;
            }
        }
        if (!VectorUtils.isFinite(face.getCenter())) {
            writeInvalid();
            return;
        }

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d uAxis = new Vector3d(c1).sub(c0);
        Vector3d vAxis = new Vector3d(c3).sub(c0);
        double width = uAxis.length();
        double height = vAxis.length();

        if (width <= EPS || height <= EPS) {
            writeInvalid();
            return;
        }

        // Positive inset must leave a non-degenerate interior; outset (negative) is unrestricted.
        if (distance >= Math.min(width, height) * 0.5d) {
            writeInvalid();
            return;
        }

        uAxis.normalize();
        vAxis.normalize();

        List<Vector3d> insetCorners = List.of(
            new Vector3d(c0).add(new Vector3d(uAxis).mul(distance)).add(new Vector3d(vAxis).mul(distance)),
            new Vector3d(corners.get(1)).sub(new Vector3d(uAxis).mul(distance)).add(new Vector3d(vAxis).mul(distance)),
            new Vector3d(corners.get(2)).sub(new Vector3d(uAxis).mul(distance)).sub(new Vector3d(vAxis).mul(distance)),
            new Vector3d(corners.get(3)).add(new Vector3d(uAxis).mul(distance)).sub(new Vector3d(vAxis).mul(distance))
        );

        Vector3d insetCenter = new Vector3d();
        for (Vector3d corner : insetCorners) {
            insetCenter.add(corner);
        }
        insetCenter.div(4.0d);

        Vector3d normal = new Vector3d(face.getNormal());
        if (!VectorUtils.isFinite(normal) || !VectorUtils.isNonZero(normal)) {
            writeInvalid();
            return;
        }

        outputValues.put(OUTPUT_FACE_ID, new BoxFaceData(
            face.getIndex(),
            face.getName(),
            face.getCornerIndices(),
            insetCorners,
            insetCenter,
            normal
        ));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_FACE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
