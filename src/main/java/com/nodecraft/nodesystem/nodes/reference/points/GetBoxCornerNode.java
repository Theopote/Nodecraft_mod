package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.get_box_corner",
    displayName = "Get Box Corner",
    description = "Gets a single corner from box geometry by index",
    category = "reference.points",
    order = 14
)
public class GetBoxCornerNode extends BaseNode {

    @NodeProperty(displayName = "Allow Negative Index", category = "Index", order = 1,
        description = "When enabled, negative indices count backward from the last corner")
    private boolean allowNegativeIndex = true;

    @NodeProperty(displayName = "Wrap Index", category = "Index", order = 2,
        description = "When enabled, the corner index wraps around the 0-7 range")
    private boolean wrapIndex = false;

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";
    private static final String INPUT_INDEX_ID = "input_index";

    private static final String OUTPUT_CORNER_ID = "output_corner";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_RESOLVED_INDEX_ID = "output_resolved_index";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBoxCornerNode() {
        super(UUID.randomUUID(), "reference.points.get_box_corner");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to query", NodeDataType.BOX_GEOMETRY, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Corner Index", "Corner index from 0 to 7", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_CORNER_ID, "Corner",
            "Resolved box corner as geometric point", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the corner index resolved successfully", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESOLVED_INDEX_ID, "Resolved Index", "Resolved corner index after negative/wrap handling", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether query inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Gets a single corner from box geometry by index";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);

        if (!(geometryObj instanceof BoxGeometryData boxGeometry)) {
            writeInvalid("Box Geometry must be BOX_GEOMETRY");
            return;
        }

        Integer index = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_INDEX_ID));
        if (index == null) {
            if (OptionalPortDrive.isConnected(this, INPUT_INDEX_ID)) {
                writeInvalid("Corner Index connected but not exact INTEGER");
            } else {
                writeInvalid("Corner Index is required");
            }
            return;
        }

        List<Vector3d> corners = boxGeometry.getCorners();
        int cornerCount = corners.size();
        if (cornerCount <= 0) {
            writeNotFound();
            return;
        }

        int resolved = index;
        if (resolved < 0 && allowNegativeIndex) {
            resolved = cornerCount + resolved;
        }

        if (wrapIndex) {
            resolved = ((resolved % cornerCount) + cornerCount) % cornerCount;
        }

        if (resolved < 0 || resolved >= cornerCount) {
            writeNotFound();
            return;
        }

        Vector3d corner = new Vector3d(corners.get(resolved));
        if (!PointUtils.isFinite(corner)) {
            writeInvalid("Box corner is not finite");
            return;
        }

        outputValues.put(OUTPUT_CORNER_ID, new PointData(corner));
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, resolved);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_CORNER_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_RESOLVED_INDEX_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_CORNER_ID, null);
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
