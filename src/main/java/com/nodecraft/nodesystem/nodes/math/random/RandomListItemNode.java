package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.RandomInputResolver;
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
    description = "Deterministically selects one or more items from a list (sample without replacement when duplicates disallowed).",
    category = "math.random",
    order = 2
)
public class RandomListItemNode extends RandomSamplingNode {

    private static final String LIST_T = "T";

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_ALLOW_DUPLICATES_ID = "input_allow_duplicates";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_ITEMS_ID = "output_items";

    private int defaultCount = 1;
    private boolean defaultAllowDuplicates = false;

    public RandomListItemNode() {
        super(UUID.randomUUID(), "math.random.random_list_item");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of items to select", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ALLOW_DUPLICATES_ID, "Allow Duplicates", "When false, sample without replacement (index-based, not value-unique)", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "First selected item", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_ITEMS_ID, "Items", "Selected items list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
    }

    @Override
    public String getDescription() {
        return "Deterministically selects items from a list. Same list order and seed produce the same picks. "
            + "When duplicates are disallowed, sampling is without replacement by index (not value-unique).";
    }

    @Override
    public String getDisplayName() {
        return "Random List Item";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        RandomInputResolver.IntegerResolveResult count = RandomInputResolver.resolveCount(
                resolveValue(INPUT_COUNT_ID), defaultCount, isDriven(INPUT_COUNT_ID));
        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));
        Boolean allowDuplicates = RandomInputResolver.resolveBoolean(
                resolveValue(INPUT_ALLOW_DUPLICATES_ID), defaultAllowDuplicates, isDriven(INPUT_ALLOW_DUPLICATES_ID));

        if (!count.valid()) {
            emitListItemFailure(OUTPUT_ITEM_ID, OUTPUT_ITEMS_ID, "Count must be an exact Integer");
            return;
        }
        if (!seed.valid()) {
            emitListItemFailure(OUTPUT_ITEM_ID, OUTPUT_ITEMS_ID, "Seed must be an exact Integer");
            return;
        }
        if (allowDuplicates == null) {
            emitListItemFailure(OUTPUT_ITEM_ID, OUTPUT_ITEMS_ID, "Allow Duplicates must be an exact Boolean");
            return;
        }

        Object listValue = resolveValue(INPUT_LIST_ID);
        if (isDriven(INPUT_LIST_ID) && !(listValue instanceof List<?>)) {
            emitListItemFailure(OUTPUT_ITEM_ID, OUTPUT_ITEMS_ID, "List must be a LIST");
            return;
        }

        int effectiveCount = count.value();
        if (!(listValue instanceof List<?> inputList) || inputList.isEmpty() || effectiveCount <= 0) {
            emitListItemSuccess(OUTPUT_ITEM_ID, null, OUTPUT_ITEMS_ID, Collections.emptyList());
            return;
        }

        if (!allowDuplicates && effectiveCount > inputList.size()) {
            effectiveCount = inputList.size();
        }

        Random random = RandomOps.rng(seed.value());
        Object singleItem = null;
        List<Object> selectedItems = new ArrayList<>(effectiveCount);

        if (allowDuplicates) {
            for (int i = 0; i < effectiveCount; i++) {
                Object selectedItem = inputList.get(random.nextInt(inputList.size()));
                selectedItems.add(selectedItem);
                if (i == 0) {
                    singleItem = selectedItem;
                }
            }
        } else {
            List<Object> shuffledList = new ArrayList<>(inputList);
            Collections.shuffle(shuffledList, random);
            for (int i = 0; i < effectiveCount; i++) {
                Object selectedItem = shuffledList.get(i);
                selectedItems.add(selectedItem);
                if (i == 0) {
                    singleItem = selectedItem;
                }
            }
        }

        emitListItemSuccess(OUTPUT_ITEM_ID, singleItem, OUTPUT_ITEMS_ID, Collections.unmodifiableList(selectedItems));
    }
}
