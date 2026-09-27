package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.get_face_edge",
    displayName = "Get Face Edge",
    description = "Gets a single edge from a face by index",
    category = "reference.points",
    order = 16
)
public class GetFaceEdgeNode extends BaseNode {

    @NodeProperty(displayName = "Allow Negative Index", category = "Index", order = 1,
        description = "When enabled, negative indices count backward from the last edge")
    private boolean allowNegativeIndex = true;

    @NodeProperty(displayName = "Wrap Index", category = "Index", order = 2,
        description = "When enabled, the edge index wraps around the 0-3 range")
    private boolean wrapIndex = false;

    private static final String INPUT_FACE_ID = "input_face";
    private static final String INPUT_INDEX_ID = "input_index";

    private static final String OUTPUT_EDGE_ID = "output_edge";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_RESOLVED_INDEX_ID = "output_resolved_index";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetFaceEdgeNode() {
        super(UUID.randomUUID(), "reference.points.get_face_edge");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The face to query", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Edge Index", "Edge index in face winding order", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_EDGE_ID, "Edge", "Resolved face edge", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the edge index resolved successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_INDEX_ID, "Resolved Index", "Resolved edge index after negative/wrap handling", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether query inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets a single edge from a face by index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);

        if (!(faceObj instanceof BoxFaceData face)) {
            writeInvalid("Face must be BOX_FACE");
            return;
        }

        String faceError = BoxFaceValidator.validate(face);
        if (faceError != null) {
            writeInvalid(faceError);
            return;
        }

        Integer index = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_INDEX_ID));
        if (index == null) {
            if (OptionalPortDrive.isConnected(this, INPUT_INDEX_ID)) {
                writeInvalid("Edge Index connected but not exact INTEGER");
            } else {
                writeInvalid("Edge Index is required");
            }
            return;
        }

        List<Vector3d> corners = face.getCorners();
        int edgeCount = corners.size();
        if (edgeCount <= 0) {
            writeNotFound();
            return;
        }

        int resolved = index;
        if (resolved < 0 && allowNegativeIndex) {
            resolved = edgeCount + resolved;
        }

        if (wrapIndex) {
            resolved = ((resolved % edgeCount) + edgeCount) % edgeCount;
        }

        if (resolved < 0 || resolved >= edgeCount) {
            writeNotFound();
            return;
        }

        Vector3d start = corners.get(resolved);
        Vector3d end = corners.get((resolved + 1) % edgeCount);
        LineData edge = new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );

        outputValues.put(OUTPUT_EDGE_ID, edge);
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, resolved);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_EDGE_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_EDGE_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    public boolean isAllowNegativeIndex() {
        return allowNegativeIndex;
    }

    public void setAllowNegativeIndex(boolean allowNegativeIndex) {
        if (this.allowNegativeIndex != allowNegativeIndex) {
            this.allowNegativeIndex = allowNegativeIndex;
            markDirty();
        }
    }

    public boolean isWrapIndex() {
        return wrapIndex;
    }

    public void setWrapIndex(boolean wrapIndex) {
        if (this.wrapIndex != wrapIndex) {
            this.wrapIndex = wrapIndex;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("allowNegativeIndex", allowNegativeIndex);
        state.put("wrapIndex", wrapIndex);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }

        if (stateMap.get("allowNegativeIndex") instanceof Boolean allowNegativeIndexValue) {
            setAllowNegativeIndex(allowNegativeIndexValue);
        }
        if (stateMap.get("wrapIndex") instanceof Boolean wrapIndexValue) {
            setWrapIndex(wrapIndexValue);
        }
    }
}
