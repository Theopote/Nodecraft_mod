package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "math.random.random_list_item",
    displayName = "Random List Item",
    description = "Randomly selects one or more items from a list.",
    category = "math.random"
)
public class RandomListItemNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_ALLOW_DUPLICATES_ID = "input_allow_duplicates";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_ITEMS_ID = "output_items";

    public RandomListItemNode() {
        super(UUID.randomUUID(), "math.random.random_list_item");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of items to select", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALLOW_DUPLICATES_ID, "Allow Duplicates", "Whether repeated picks are allowed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "Selected item when Count = 1", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_ITEMS_ID, "Items", "Selected items list", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Randomly selects one or more items from a list.";
    }

    @Override
    public String getDisplayName() {
        return "Random List Item";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listValue = inputValues.get(INPUT_LIST_ID);
        int count = getValueAsInt(inputValues.get(INPUT_COUNT_ID), 1);
        boolean allowDuplicates = getValueAsBoolean(inputValues.get(INPUT_ALLOW_DUPLICATES_ID), false);
        Object seedValue = inputValues.get(INPUT_SEED_ID);

        if (!(listValue instanceof List)) {
            if (listValue == null) {
                outputValues.put(OUTPUT_ITEM_ID, null);
                outputValues.put(OUTPUT_ITEMS_ID, Collections.emptyList());
                return;
            }
            List<Object> singleItemList = new ArrayList<>(1);
            singleItemList.add(listValue);
            listValue = singleItemList;
        }

        @SuppressWarnings("unchecked")
        List<Object> inputList = (List<Object>) listValue;
        if (inputList.isEmpty()) {
            outputValues.put(OUTPUT_ITEM_ID, null);
            outputValues.put(OUTPUT_ITEMS_ID, Collections.emptyList());
            return;
        }

        count = Math.max(0, count);
        if (!allowDuplicates && count > inputList.size()) {
            count = inputList.size();
        }

        Random random = seedValue instanceof Number
            ? new Random(((Number) seedValue).longValue())
            : new Random();

        Object singleItem = null;
        List<Object> selectedItems = new ArrayList<>(count);

        if (allowDuplicates) {
            for (int i = 0; i < count; i++) {
                Object selectedItem = inputList.get(random.nextInt(inputList.size()));
                selectedItems.add(selectedItem);
                if (i == 0) {
                    singleItem = selectedItem;
                }
            }
        } else {
            List<Object> shuffledList = new ArrayList<>(inputList);
            Collections.shuffle(shuffledList, random);
            for (int i = 0; i < count; i++) {
                Object selectedItem = shuffledList.get(i);
                selectedItems.add(selectedItem);
                if (i == 0) {
                    singleItem = selectedItem;
                }
            }
        }

        outputValues.put(OUTPUT_ITEM_ID, singleItem);
        outputValues.put(OUTPUT_ITEMS_ID, Collections.unmodifiableList(selectedItems));
    }

    private int getValueAsInt(Object value, int defaultValue) {
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private boolean getValueAsBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        } else if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return defaultValue;
    }
}
