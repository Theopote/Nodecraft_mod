package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    id = "spatial.analysis.get_box_face",
    displayName = "Get Box Face",
    description = "Gets a single face from box geometry by index",
    category = "spatial.analysis"
)
public class GetBoxFaceNode extends BaseNode {

    @NodeProperty(displayName = "Allow Negative Index", category = "Index", order = 1,
        description = "When enabled, negative indices count backward from the last face")
    private boolean allowNegativeIndex = true;

    @NodeProperty(displayName = "Wrap Index", category = "Index", order = 2,
        description = "When enabled, the face index wraps around the 0-5 range")
    private boolean wrapIndex = false;

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_INDEX_ID = "input_index";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_RESOLVED_INDEX_ID = "output_resolved_index";

    public GetBoxFaceNode() {
        super(UUID.randomUUID(), "spatial.analysis.get_box_face");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to query", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Face Index", "Face index from 0 to 5", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Resolved box face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the face index resolved successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_INDEX_ID, "Resolved Index", "Resolved face index after negative/wrap handling", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Gets a single face from box geometry by index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);

        BoxFaceData face = null;
        boolean found = false;
        String name = null;
        Integer resolvedIndex = null;

        if (geometryObj instanceof BoxGeometryData boxGeometry && indexObj instanceof Number number) {
            List<BoxFaceData> faces = boxGeometry.getFaces();
            int index = number.intValue();
            int faceCount = faces.size();

            if (faceCount > 0) {
                if (index < 0 && allowNegativeIndex) {
                    index = faceCount + index;
                }

                if (wrapIndex) {
                    index = ((index % faceCount) + faceCount) % faceCount;
                }

                if (index >= 0 && index < faceCount) {
                    face = faces.get(index);
                    found = true;
                    name = face.getName();
                    resolvedIndex = index;
                }
            }
        }

        outputValues.put(OUTPUT_FACE_ID, face);
        outputValues.put(OUTPUT_FOUND_ID, found);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, resolvedIndex);
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
