package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxFaceValidator;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.get_box_face",
    displayName = "Get Box Face",
    description = "Gets a single face from box geometry by semantic name or index",
    category = "reference.points",
    order = 15
)
public class GetBoxFaceNode extends BaseNode {

    @NodeProperty(displayName = "Allow Negative Index", category = "Index", order = 1,
        description = "When enabled, negative indices count backward from the last face")
    private boolean allowNegativeIndex = true;

    @NodeProperty(displayName = "Wrap Index", category = "Index", order = 2,
        description = "When enabled, the face index wraps around the 0-5 range")
    private boolean wrapIndex = false;

    @NodeProperty(displayName = "Default Face Name", category = "Selection", order = 3,
        description = "Fallback semantic face name when no face name or index input is connected")
    private String defaultFaceName = "";

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_FACE_NAME_ID = "input_face_name";
    private static final String INPUT_INDEX_ID = "input_index";

    private static final String OUTPUT_FACE_ID = "output_face";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_RESOLVED_INDEX_ID = "output_resolved_index";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBoxFaceNode() {
        super(UUID.randomUUID(), "reference.points.get_box_face");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to query", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_FACE_NAME_ID, "Face Name", "Semantic face name such as top, bottom, left, right, front, or back", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Face Index", "Face index from 0 to 5", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FACE_ID, "Face", "Resolved box face", NodeDataType.BOX_FACE, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the face index resolved successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved face name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_INDEX_ID, "Resolved Index", "Resolved face index after negative/wrap handling", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether query inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets a single face from box geometry by semantic name or index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        boolean faceNameConnected = OptionalPortDrive.isConnected(this, INPUT_FACE_NAME_ID);
        boolean indexConnected = OptionalPortDrive.isConnected(this, INPUT_INDEX_ID);

        if (!(geometryObj instanceof BoxGeometryData boxGeometry)) {
            writeInvalid("Box Geometry must be BOX_GEOMETRY");
            return;
        }

        List<BoxFaceData> faces = boxGeometry.getFaces();
        BoxFaceData face = null;

        if (faceNameConnected) {
            String rawName = OptionalPortDrive.resolveOptionalString(this, INPUT_FACE_NAME_ID, null);
            if (rawName == null) {
                writeInvalid("Face Name connected but null or invalid");
                return;
            }
            String normalized = normalizeFaceName(rawName);
            if (normalized == null) {
                writeNotFound();
                return;
            }
            face = resolveBySemanticName(faces, normalized);
            if (face == null) {
                writeNotFound();
                return;
            }
        } else if (indexConnected) {
            Integer index = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_INDEX_ID));
            if (index == null) {
                writeInvalid("Face Index connected but not exact INTEGER");
                return;
            }
            face = resolveByIndex(faces, index);
            if (face == null) {
                writeNotFound();
                return;
            }
        } else if (!defaultFaceName.isBlank()) {
            String normalized = normalizeFaceName(defaultFaceName);
            if (normalized == null) {
                writeNotFound();
                return;
            }
            face = resolveBySemanticName(faces, normalized);
            if (face == null) {
                writeNotFound();
                return;
            }
        } else {
            writeInvalid("Face Name or Face Index is required");
            return;
        }

        String validationError = BoxFaceValidator.validate(face);
        if (validationError != null) {
            writeInvalid(validationError);
            return;
        }

        writeSuccess(face);
    }

    private void writeSuccess(BoxFaceData face) {
        outputValues.put(OUTPUT_FACE_ID, face);
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_NAME_ID, face.getName());
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, face.getIndex());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FACE_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_NAME_ID, null);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_FACE_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_NAME_ID, null);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private BoxFaceData resolveBySemanticName(List<BoxFaceData> faces, String normalized) {
        for (BoxFaceData candidate : faces) {
            if (normalized.equals(normalizeFaceName(candidate.getName()))) {
                return candidate;
            }
        }
        return null;
    }

    private @Nullable BoxFaceData resolveByIndex(List<BoxFaceData> faces, int index) {
        int faceCount = faces.size();
        if (faceCount <= 0) {
            return null;
        }

        if (index < 0 && allowNegativeIndex) {
            index = faceCount + index;
        }

        if (wrapIndex) {
            index = ((index % faceCount) + faceCount) % faceCount;
        }

        if (index < 0 || index >= faceCount) {
            return null;
        }

        return faces.get(index);
    }

    private String normalizeFaceName(String name) {
        String normalized = name.trim().toLowerCase()
            .replace('_', ' ')
            .replace('-', ' ');

        return switch (normalized) {
            case "top", "up", "upper" -> "top";
            case "bottom", "down", "lower" -> "bottom";
            case "left", "west" -> "left";
            case "right", "east" -> "right";
            case "front", "south" -> "front";
            case "back", "north", "rear" -> "back";
            default -> null;
        };
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

    public String getDefaultFaceName() {
        return defaultFaceName;
    }

    public void setDefaultFaceName(String defaultFaceName) {
        String resolved = defaultFaceName == null ? "" : defaultFaceName.trim();
        if (!this.defaultFaceName.equals(resolved)) {
            this.defaultFaceName = resolved;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("allowNegativeIndex", allowNegativeIndex);
        state.put("wrapIndex", wrapIndex);
        state.put("defaultFaceName", defaultFaceName);
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
        if (stateMap.get("defaultFaceName") instanceof String defaultFaceNameValue) {
            setDefaultFaceName(defaultFaceNameValue);
        }
    }
}
