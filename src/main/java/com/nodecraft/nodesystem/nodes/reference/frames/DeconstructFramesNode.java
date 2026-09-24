package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.frames.deconstruct_frames",
    displayName = "Deconstruct Frames",
    description = "Splits a FRAME_LIST into origins, axes, and planes",
    category = "reference.frames",
    order = 6
)
public class DeconstructFramesNode extends BaseNode {

    private static final String INPUT_FRAMES_ID = "input_frames";

    private static final String OUTPUT_ORIGINS_ID = "output_origins";
    private static final String OUTPUT_X_AXES_ID = "output_x_axes";
    private static final String OUTPUT_Y_AXES_ID = "output_y_axes";
    private static final String OUTPUT_Z_AXES_ID = "output_z_axes";
    private static final String OUTPUT_PLANES_ID = "output_planes";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructFramesNode() {
        super(UUID.randomUUID(), "reference.frames.deconstruct_frames");
        addInputPort(new BasePort(INPUT_FRAMES_ID, "Frames", "Frame list to deconstruct", NodeDataType.FRAME_LIST, this));

        addOutputPort(new BasePort(OUTPUT_ORIGINS_ID, "Origins", "Frame origins", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_X_AXES_ID, "X Axes", "Frame X axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Y_AXES_ID, "Y Axes", "Frame Y axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_Z_AXES_ID, "Z Axes", "Frame Z axes", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PLANES_ID, "Planes", "Planes from each frame", NodeDataType.PLANE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of frames", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when input is a non-empty frame list", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object raw = inputValues.get(INPUT_FRAMES_ID);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            writeEmpty();
            return;
        }

        List<Vector3d> xAxes = new ArrayList<>(list.size());
        List<Vector3d> yAxes = new ArrayList<>(list.size());
        List<Vector3d> zAxes = new ArrayList<>(list.size());
        List<PlaneData> planes = new ArrayList<>(list.size());
        List<Vector3d> origins = new ArrayList<>(list.size());

        for (Object item : list) {
            if (!(item instanceof FrameData frame)) {
                writeEmpty();
                return;
            }
            origins.add(new Vector3d(frame.getOrigin()));
            xAxes.add(new Vector3d(frame.getXAxis()));
            yAxes.add(new Vector3d(frame.getYAxis()));
            zAxes.add(new Vector3d(frame.getZAxis()));
            planes.add(frame.toPlane());
        }

        outputValues.put(OUTPUT_ORIGINS_ID, SpatialValueResolver.toPointDataList(origins));
        outputValues.put(OUTPUT_X_AXES_ID, List.copyOf(xAxes));
        outputValues.put(OUTPUT_Y_AXES_ID, List.copyOf(yAxes));
        outputValues.put(OUTPUT_Z_AXES_ID, List.copyOf(zAxes));
        outputValues.put(OUTPUT_PLANES_ID, List.copyOf(planes));
        outputValues.put(OUTPUT_COUNT_ID, list.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmpty() {
        outputValues.put(OUTPUT_ORIGINS_ID, List.of());
        outputValues.put(OUTPUT_X_AXES_ID, List.of());
        outputValues.put(OUTPUT_Y_AXES_ID, List.of());
        outputValues.put(OUTPUT_Z_AXES_ID, List.of());
        outputValues.put(OUTPUT_PLANES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
