package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ListIndexResolver;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.get_item",
    displayName = "Get Item",
    description = "Gets an item from a list at a specified index.",
    category = "math.list"
)
public class GetItemNode extends BaseNode {
    
    private boolean allowNegativeIndex = true;
    private boolean wrapIndex = false;
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_FOUND_ID = "output_found";
    
    public GetItemNode() {
        super(UUID.randomUUID(), "math.list.get_item");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to get an item from", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort indexInput = new BasePort(INPUT_INDEX_ID, "Index", 
                "0-based index; negatives count from end (-1 = last). Wrap property applies modulo size.",
                NodeDataType.INTEGER, this);
        addInputPort(indexInput);
        
        IPort itemOutput = new BasePort(OUTPUT_ITEM_ID, "Item", 
                "The item at the specified index", NodeDataType.ANY, this);
        addOutputPort(itemOutput);
        
        IPort foundOutput = new BasePort(OUTPUT_FOUND_ID, "Found", 
                "Whether an item was found at the specified index", NodeDataType.BOOLEAN, this);
        addOutputPort(foundOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        ListIndexResolver.IndexResolveResult indexResult = ListIndexResolver.resolveRequiredIndex(
                resolveValue(INPUT_INDEX_ID),
                isDriven(INPUT_INDEX_ID)
        );
        
        Object item = null;
        boolean found = false;
        
        if (inputObj instanceof List<?> list && indexResult.valid()) {
            int listSize = list.size();
            
            if (listSize > 0) {
                int index = indexResult.index();
                
                if (index < 0 && allowNegativeIndex) {
                    index = ListIndexResolver.normalizeNegativeFromEnd(index, listSize);
                }
                
                if (wrapIndex && listSize > 0) {
                    index = ListIndexResolver.applyWrap(index, listSize);
                    found = true;
                    item = list.get(index);
                } else if (index >= 0 && index < listSize) {
                    found = true;
                    item = list.get(index);
                }
            }
        }
        
        outputValues.put(OUTPUT_ITEM_ID, item);
        outputValues.put(OUTPUT_FOUND_ID, found);
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
    
    public boolean isAllowNegativeIndex() {
        return allowNegativeIndex;
    }
    
    public void setAllowNegativeIndex(boolean allow) {
        if (this.allowNegativeIndex != allow) {
            this.allowNegativeIndex = allow;
            markDirty();
        }
    }
    
    public boolean isWrapIndex() {
        return wrapIndex;
    }
    
    public void setWrapIndex(boolean wrap) {
        if (this.wrapIndex != wrap) {
            this.wrapIndex = wrap;
            markDirty();
        }
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("allowNegativeIndex", isAllowNegativeIndex());
        state.put("wrapIndex", isWrapIndex());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;

            if (stateMap.containsKey("allowNegativeIndex")) {
                Object allow = stateMap.get("allowNegativeIndex");
                if (allow instanceof Boolean) {
                    setAllowNegativeIndex((Boolean) allow);
                }
            }
            
            if (stateMap.containsKey("wrapIndex")) {
                Object wrap = stateMap.get("wrapIndex");
                if (wrap instanceof Boolean) {
                    setWrapIndex((Boolean) wrap);
                }
            }
        }
    }
}
