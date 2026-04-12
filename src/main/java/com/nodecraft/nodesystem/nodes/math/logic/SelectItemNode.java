package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Canonical multi-branch selection node for the v1 logic tree.
 */
@NodeInfo(
    id = "math.logic.switch",
    displayName = "Switch",
    description = "Selects one of multiple inputs by index, with a default fallback.",
    category = "math.logic",
    order = 1
)
public class SelectItemNode extends BaseNode {

    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_ITEM_0_ID = "input_item_0";
    private static final String INPUT_ITEM_1_ID = "input_item_1";
    private static final String INPUT_ITEM_2_ID = "input_item_2";
    private static final String INPUT_ITEM_3_ID = "input_item_3";
    private static final String INPUT_DEFAULT_ID = "input_default";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public SelectItemNode() {
        super(UUID.randomUUID(), "math.logic.switch");

        addInputPort(new BasePort(INPUT_INDEX_ID, "Index",
            "Selection index from 0 to 3", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ITEM_0_ID, "Item 0",
            "Value used when index is 0", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ITEM_1_ID, "Item 1",
            "Value used when index is 1", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ITEM_2_ID, "Item 2",
            "Value used when index is 2", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ITEM_3_ID, "Item 3",
            "Value used when index is 3", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_DEFAULT_ID, "Default",
            "Fallback value used when the index is out of range", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result",
            "Selected output value", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Selects one of multiple inputs by index, with a default fallback.";
    }

    @Override
    public String getDisplayName() {
        return "Switch";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        int index = 0;

        if (indexObj instanceof Number value) {
            index = value.intValue();
        } else if (indexObj instanceof String value) {
            try {
                index = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                index = -1;
            }
        } else if (indexObj instanceof Boolean value) {
            index = value ? 0 : 1;
        } else {
            index = -1;
        }

        Object result = switch (index) {
            case 0 -> inputValues.get(INPUT_ITEM_0_ID);
            case 1 -> inputValues.get(INPUT_ITEM_1_ID);
            case 2 -> inputValues.get(INPUT_ITEM_2_ID);
            case 3 -> inputValues.get(INPUT_ITEM_3_ID);
            default -> inputValues.get(INPUT_DEFAULT_ID);
        };

        outputValues.put(OUTPUT_RESULT_ID, result);
    }
}
