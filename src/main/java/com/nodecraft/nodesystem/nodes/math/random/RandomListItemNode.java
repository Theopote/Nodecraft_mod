package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_list_item",
    displayName = "Random List Item",
    description = "Deterministically selects one or more items from a list.",
    category = "math.random",
    order = 2
)
public class RandomListItemNode extends BaseNode {

    private static final String LIST_T = "T";

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_ALLOW_DUPLICATES_ID = "input_allow_duplicates";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_ITEMS_ID = "output_items";

    private int defaultCount = 1;

    public RandomListItemNode() {
        super(UUID.randomUUID(), "math.random.random_list_item");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of items to select", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALLOW_DUPLICATES_ID, "Allow Duplicates", "Whether repeated picks are allowed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "First selected item", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_ITEMS_ID, "Items", "Selected items list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
    }

    @Override
    public String getDescription() {
        return "Deterministically selects one or more items from a list. Item is the first pick; Items is the full selection.";
    }

    @Override
    public String getDisplayName() {
        return "Random List Item";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listValue = inputValues.get(INPUT_LIST_ID);
        int count = RandomOps.resolveCount(inputValues.get(INPUT_COUNT_ID), defaultCount);
        boolean allowDuplicates = inputValues.get(INPUT_ALLOW_DUPLICATES_ID) instanceof Boolean b && b;
        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));

        if (!(listValue instanceof List<?> inputList) || inputList.isEmpty() || count <= 0) {
            outputValues.put(OUTPUT_ITEM_ID, null);
            outputValues.put(OUTPUT_ITEMS_ID, Collections.emptyList());
            return;
        }

        if (!allowDuplicates && count > inputList.size()) {
            count = inputList.size();
        }

        Random random = RandomOps.rng(seed);
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
}
