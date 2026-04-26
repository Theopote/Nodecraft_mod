package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.list.zip",
    displayName = "Zip Lists",
    description = "Pairs two lists by index and outputs tuples as entry maps.",
    category = "math.list",
    order = 19
)
public class ZipListsNode extends BaseNode {

    @NodeProperty(displayName = "Use Longest", category = "Zip", order = 1)
    private boolean useLongest = false;

    @NodeProperty(displayName = "Fill Missing With Null", category = "Zip", order = 2)
    private boolean fillMissingWithNull = true;

    private static final String INPUT_LEFT_ID = "input_left";
    private static final String INPUT_RIGHT_ID = "input_right";

    private static final String OUTPUT_ZIPPED_ID = "output_zipped";
    private static final String OUTPUT_LEFT_REMAINDER_ID = "output_left_remainder";
    private static final String OUTPUT_RIGHT_REMAINDER_ID = "output_right_remainder";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ZipListsNode() {
        super(UUID.randomUUID(), "math.list.zip");

        addInputPort(new BasePort(INPUT_LEFT_ID, "Left", "Left list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_RIGHT_ID, "Right", "Right list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_ZIPPED_ID, "Zipped", "List of zipped entry maps", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_LEFT_REMAINDER_ID, "Left Remainder", "Unpaired left-side values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_RIGHT_REMAINDER_ID, "Right Remainder", "Unpaired right-side values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of paired entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both inputs are valid lists", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Zip Lists";
    }

    @Override
    public String getDescription() {
        return "Pairs two lists by index and outputs tuples as entry maps.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object leftObj = inputValues.get(INPUT_LEFT_ID);
        Object rightObj = inputValues.get(INPUT_RIGHT_ID);

        if (!(leftObj instanceof List<?> left) || !(rightObj instanceof List<?> right)) {
            outputValues.put(OUTPUT_ZIPPED_ID, List.of());
            outputValues.put(OUTPUT_LEFT_REMAINDER_ID, List.of());
            outputValues.put(OUTPUT_RIGHT_REMAINDER_ID, List.of());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int leftSize = left.size();
        int rightSize = right.size();
        int pairCount = useLongest ? Math.max(leftSize, rightSize) : Math.min(leftSize, rightSize);

        List<Object> zipped = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            boolean hasLeft = i < leftSize;
            boolean hasRight = i < rightSize;
            Object leftValue = hasLeft ? left.get(i) : null;
            Object rightValue = hasRight ? right.get(i) : null;

            if (!fillMissingWithNull && (!hasLeft || !hasRight)) {
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("left", leftValue);
            entry.put("right", rightValue);
            zipped.add(entry);
        }

        List<Object> leftRemainder = new ArrayList<>();
        if (leftSize > rightSize) {
            leftRemainder.addAll(left.subList(rightSize, leftSize));
        }

        List<Object> rightRemainder = new ArrayList<>();
        if (rightSize > leftSize) {
            rightRemainder.addAll(right.subList(leftSize, rightSize));
        }

        outputValues.put(OUTPUT_ZIPPED_ID, zipped);
        outputValues.put(OUTPUT_LEFT_REMAINDER_ID, leftRemainder);
        outputValues.put(OUTPUT_RIGHT_REMAINDER_ID, rightRemainder);
        outputValues.put(OUTPUT_COUNT_ID, zipped.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useLongest", useLongest);
        state.put("fillMissingWithNull", fillMissingWithNull);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object useLongestValue = map.get("useLongest");
        if (useLongestValue instanceof Boolean value) {
            useLongest = value;
        }
        Object fillMissingValue = map.get("fillMissingWithNull");
        if (fillMissingValue instanceof Boolean value) {
            fillMissingWithNull = value;
        }
    }
}

