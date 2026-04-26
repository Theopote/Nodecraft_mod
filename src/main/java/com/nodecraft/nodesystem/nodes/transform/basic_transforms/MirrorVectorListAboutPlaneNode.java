package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryMirror;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Reflects a list of 3D positions about a plane (useful for mesh control points and paths).
 */
@NodeInfo(
    id = "transform.basic_transforms.mirror_vector_list_plane",
    displayName = "Mirror Vector List About Plane",
    description = "Mirrors each point in a list about a plane and outputs Vector3d positions",
    category = "transform.basic_transforms",
    order = 10
)
public class MirrorVectorListAboutPlaneNode extends BaseNode {

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_PLANE_ID = "input_plane";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MirrorVectorListAboutPlaneNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.mirror_vector_list_plane");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points",
            "List of Point, Vector, BlockPos, etc., or a single point value",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane",
            "Mirror plane",
            NodeDataType.PLANE, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points",
            "Mirrored positions as Vector3d list",
            NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of mirrored points",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when mirroring succeeded",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Mirror Vector List About Plane";
    }

    @Override
    public String getDescription() {
        return "Mirrors each point in a list about a plane and outputs Vector3d positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object planeObj = inputValues.get(INPUT_PLANE_ID);
        if (!(planeObj instanceof PlaneData plane)) {
            writeInvalid();
            return;
        }

        List<Vector3d> sources = resolvePoints(inputValues.get(INPUT_POINTS_ID));
        if (sources.isEmpty()) {
            writeInvalid();
            return;
        }

        List<Vector3d> mirrored = new ArrayList<>(sources.size());
        for (Vector3d p : sources) {
            mirrored.add(GeometryMirror.mirrorPoint(p, plane));
        }

        outputValues.put(OUTPUT_POINTS_ID, mirrored);
        outputValues.put(OUTPUT_COUNT_ID, mirrored.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private static List<Vector3d> resolvePoints(Object value) {
        List<Vector3d> out = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                Vector3d p = resolvePoint(entry);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }
        Vector3d single = resolvePoint(value);
        if (single != null) {
            out.add(single);
        }
        return out;
    }

    private static Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pd) {
            return new Vector3d(pd.getPosition());
        }
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof BlockPos bp) {
            return new Vector3d(bp.getX(), bp.getY(), bp.getZ());
        }
        return null;
    }
}
