package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.sphere_surface_frame",
    displayName = "Sphere Surface Frame",
    description = "Builds a local tangent frame on a sphere at the projected surface point",
    category = "reference.frames",
    order = 1
)
public class SphereSurfaceFrameNode extends BaseNode {

    private static final String INPUT_SPHERE_ID = "input_sphere";
    private static final String INPUT_POINT_ID = "input_point";
    private static final String INPUT_X_HINT_ID = "input_x_hint";

    private static final String OUTPUT_FRAME_ID = "output_frame";
    private static final String OUTPUT_ORIGIN_ID = "output_origin";
    private static final String OUTPUT_NORMAL_ID = "output_normal";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SphereSurfaceFrameNode() {
        super(UUID.randomUUID(), "reference.frames.sphere_surface_frame");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry to evaluate against", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Reference point near or on the sphere surface", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_X_HINT_ID, "X Hint", "Optional tangent X direction hint", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_FRAME_ID, "Frame", "Tangent frame on the sphere surface", NodeDataType.FRAME, this));
        addOutputPort(new BasePort(OUTPUT_ORIGIN_ID, "Surface Point", "Projected surface point used as frame origin", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_NORMAL_ID, "Normal", "Outward sphere normal at the surface point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a valid frame could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds a local tangent frame on a sphere at the projected surface point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        Vector3d point = FrameUtils.resolvePoint(inputValues.get(INPUT_POINT_ID));
        Vector3d xHint = SpatialValueResolver.resolveVector(inputValues.get(INPUT_X_HINT_ID));

        if (!(sphereObj instanceof SphereData sphere) || !FrameUtils.isFinite(point)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d center = sphere.center();
        double radius = sphere.radius();
        if (!FrameUtils.isFinite(center) || !Double.isFinite(radius) || radius <= FrameUtils.EPS) {
            writeEmptyOutputs();
            return;
        }

        Vector3d radial = new Vector3d(point).sub(center);
        if (!FrameUtils.isUsableAxis(radial)) {
            writeEmptyOutputs();
            return;
        }
        Vector3d normal = radial.normalize();
        Vector3d origin = new Vector3d(normal).mul(radius).add(center);

        FrameData frame = FrameUtils.fromNormal(origin, normal, xHint);
        if (frame == null) {
            writeEmptyOutputs();
            return;
        }

        outputValues.put(OUTPUT_FRAME_ID, frame);
        outputValues.put(OUTPUT_ORIGIN_ID, new PointData(origin));
        outputValues.put(OUTPUT_NORMAL_ID, normal);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_FRAME_ID, null);
        outputValues.put(OUTPUT_ORIGIN_ID, null);
        outputValues.put(OUTPUT_NORMAL_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
