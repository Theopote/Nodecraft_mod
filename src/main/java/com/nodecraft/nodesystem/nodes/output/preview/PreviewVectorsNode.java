package com.nodecraft.nodesystem.nodes.output.preview;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.PreviewOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.preview.preview_vectors",
    displayName = "Preview Vectors",
    description = "Previews vectors and directions before voxelization",
    category = "output.preview",
    order = 3
)
public class PreviewVectorsNode extends BaseNode {

    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_START_POINT_ID = "input_start_point";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIEW_ID_ID = "output_preview_id";

    @NodeProperty(displayName = "Preview Enabled", category = "Preview", order = 1)
    private boolean previewEnabled = true;

    @NodeProperty(displayName = "Length Scale", category = "Preview", order = 2)
    private float lengthScale = 1.0f;

    @NodeProperty(displayName = "Arrow Size", category = "Preview", order = 3)
    private float arrowSize = 0.3f;

    @NodeProperty(displayName = "Show Arrowheads", category = "Preview", order = 4)
    private boolean showArrows = true;

    @NodeProperty(displayName = "Duration", category = "Preview", order = 5)
    private int duration = 30;

    public PreviewVectorsNode() {
        super(UUID.randomUUID(), "output.preview.preview_vectors");
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Vector to preview", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_START_POINT_ID, "Start Point", "Optional vector start point", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the preview was shown", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIEW_ID_ID, "Preview ID", "Active preview identifier", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Previews vectors and directions before voxelization";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String previewId = null;

        if (!previewEnabled) {
            PreviewManager.hideNodePreviews(getId().toString());
        } else if (inputValues.get(INPUT_VECTOR_ID) instanceof Vector3d vector) {
            Vec3d start = inputValues.get(INPUT_START_POINT_ID) instanceof BlockPos pos ? pos.toCenterPos() : Vec3d.ZERO;
            List<Vec3d> vectors = List.of(new Vec3d(vector.x, vector.y, vector.z));
            List<Vec3d> starts = List.of(start);

            PreviewManager.hideNodePreviews(getId().toString());
            PreviewOptions options = PreviewOptions.createVectorArrows().setDuration(duration);
            options.lengthScale = lengthScale;
            options.arrowSize = arrowSize;
            options.showArrows = showArrows;
            previewId = PreviewManager.showVectors(getId().toString(), vectors, starts, options);
            success = previewId != null;
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIEW_ID_ID, previewId);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("previewEnabled", previewEnabled);
        state.put("lengthScale", lengthScale);
        state.put("arrowSize", arrowSize);
        state.put("showArrows", showArrows);
        state.put("duration", duration);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("previewEnabled") instanceof Boolean bool) {
                previewEnabled = bool;
            }
            if (map.get("lengthScale") instanceof Number number) {
                lengthScale = Math.max(0.01f, number.floatValue());
            }
            if (map.get("arrowSize") instanceof Number number) {
                arrowSize = Math.max(0.05f, number.floatValue());
            }
            if (map.get("showArrows") instanceof Boolean bool) {
                showArrows = bool;
            }
            if (map.get("duration") instanceof Number number) {
                duration = Math.max(1, number.intValue());
            }
        }
    }
}
