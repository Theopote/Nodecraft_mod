package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "math.list.insert_item",
    displayName = "Insert Item",
    description = "Inserts an item into a list at a specified index",
    category = "math.list"
)
public class InsertItemNode extends BaseNode {
    
    private boolean allowNegativeIndex = true;
    private boolean append = true;
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    
    public InsertItemNode() {
        super(UUID.randomUUID(), "math.list.insert_item");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to insert into", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort indexInput = new BasePort(INPUT_INDEX_ID, "Index", 
                "The index where to insert the item (0-based)", NodeDataType.INTEGER, this);
        addInputPort(indexInput);
        
        IPort valueInput = new BasePort(INPUT_VALUE_ID, "Value", 
                "The value to insert at the specified index", NodeDataType.ANY, this);
        addInputPort(valueInput);
        
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Modified List", 
                "The list with the inserted item", NodeDataType.LIST, this);
        addOutputPort(listOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        boolean hasValueInput = inputValues.containsKey(INPUT_VALUE_ID);
        
        List<Object> resultList = new ArrayList<>();
        
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            
            for (Object item : inputList) {
                resultList.add(item);
            }
            
            int listSize = resultList.size();
            
            int insertIndex = listSize;
            
            if (indexObj instanceof Number) {
                int index = ((Number) indexObj).intValue();
                
                if (index < 0 && allowNegativeIndex) {
                    index = listSize + index;
                }
                
                if (index >= 0 && index <= listSize) {
                    insertIndex = index;
                } else if (!append) {
                    outputValues.put(OUTPUT_LIST_ID, resultList);
                    return;
                }
            }
            
            resultList.add(insertIndex, valueObj);
        } else if (hasValueInput) {
            resultList.add(valueObj);
        }
        
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isAllowNegativeIndex() {
        return allowNegativeIndex;
    }
    
    public void setAllowNegativeIndex(boolean allow) {
        if (this.allowNegativeIndex != allow) {
            this.allowNegativeIndex = allow;
            markDirty();
        }
    }
    
    public boolean isAppend() {
        return append;
    }
    
    public void setAppend(boolean append) {
        if (this.append != append) {
            this.append = append;
            markDirty();
        }
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("allowNegativeIndex", isAllowNegativeIndex());
        state.put("append", isAppend());
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
            
            if (stateMap.containsKey("append")) {
                Object appendValue = stateMap.get("append");
                if (appendValue instanceof Boolean) {
                    setAppend((Boolean) appendValue);
                }
            }
        }
    }
} 
