package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ListIndexResolver;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.sub_list",
    displayName = "Sub List",
    description = "Inclusive start / exclusive end slice. Negatives from end. Out-of-range → Valid=false.",
    category = "math.list"
)
public class SubListNode extends BaseNode {

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String OUTPUT_SUBLIST_ID = "output_sublist";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SubListNode() {
        super(UUID.randomUUID(), "math.list.sub_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The source list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Start index inclusive (negatives from end)",
                NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "End index exclusive (negatives from end). Undriven → list size.",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUBLIST_ID, "Sub List", "The resulting sub list",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether indexes resolved in range",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);

        if (!(inputObj instanceof List<?> inputList)) {
            writeInvalid();
            return;
        }

        int size = inputList.size();
        ListIndexResolver.IndexResolveResult startResult = ListIndexResolver.resolveOptionalIndex(
                resolveValue(INPUT_START_ID),
                0,
                isDriven(INPUT_START_ID)
        );
        ListIndexResolver.IndexResolveResult endResult = ListIndexResolver.resolveOptionalIndex(
                resolveValue(INPUT_END_ID),
                size,
                isDriven(INPUT_END_ID)
        );

        if (!startResult.valid() || !endResult.valid()) {
            writeInvalid();
            return;
        }

        int start = ListIndexResolver.normalizeNegativeFromEnd(startResult.index(), size);
        int end = ListIndexResolver.normalizeNegativeFromEnd(endResult.index(), size);

        if (start < 0 || end < 0 || end > size || start > end) {
            writeInvalid();
            return;
        }

        List<Object> result = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            result.add(inputList.get(i));
        }
        outputValues.put(OUTPUT_SUBLIST_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_SUBLIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of();
    }

    @Override
    public void setNodeState(Object state) {
    }
}
