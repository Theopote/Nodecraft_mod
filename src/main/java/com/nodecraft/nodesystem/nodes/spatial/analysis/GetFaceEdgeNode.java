package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.get_face_edge",
    displayName = "Get Face Edge",
    description = "Gets a single edge from a face by index",
    category = "spatial.analysis"
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
    private static final String OUTPUT_START_ID = "output_start";
    private static final String OUTPUT_END_ID = "output_end";

    public GetFaceEdgeNode() {
        super(UUID.randomUUID(), "spatial.analysis.get_face_edge");

        addInputPort(new BasePort(INPUT_FACE_ID, "Face", "The face to query", NodeDataType.BOX_FACE, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Edge Index", "Edge index from 0 to 3", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_EDGE_ID, "Edge", "Resolved face edge", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the edge index resolved successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_INDEX_ID, "Resolved Index", "Resolved edge index after negative/wrap handling", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_START_ID, "Start", "Edge start point", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_END_ID, "End", "Edge end point", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Gets a single edge from a face by index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object faceObj = inputValues.get(INPUT_FACE_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);

        LineData edge = null;
        boolean found = false;
        Integer resolvedIndex = null;
        Vector3d start = null;
        Vector3d end = null;

        if (faceObj instanceof BoxFaceData face && indexObj instanceof Number number) {
            List<Vector3d> corners = face.getCorners();
            int index = number.intValue();
            int edgeCount = corners.size();

            if (edgeCount > 0) {
                if (index < 0 && allowNegativeIndex) {
                    index = edgeCount + index;
                }

                if (wrapIndex) {
                    index = ((index % edgeCount) + edgeCount) % edgeCount;
                }

                if (index >= 0 && index < edgeCount) {
                    start = new Vector3d(corners.get(index));
                    end = new Vector3d(corners.get((index + 1) % edgeCount));
                    edge = new LineData(
                        new Vec3d(start.x, start.y, start.z),
                        new Vec3d(end.x, end.y, end.z)
                    );
                    found = true;
                    resolvedIndex = index;
                }
            }
        }

        outputValues.put(OUTPUT_EDGE_ID, edge);
        outputValues.put(OUTPUT_FOUND_ID, found);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, resolvedIndex);
        outputValues.put(OUTPUT_START_ID, start);
        outputValues.put(OUTPUT_END_ID, end);
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
