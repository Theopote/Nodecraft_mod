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
import java.util.Objects;
import java.util.UUID;

@NodeInfo(
    id = "math.list.remove_item",
    displayName = "Remove Item",
    description = "Removes an item from a list by index or value",
    category = "math.list"
)
public class RemoveItemNode extends BaseNode {
    
    private boolean useIndex = true;
    private boolean allowNegativeIndex = true;
    private boolean removeAllMatches = false;
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_COUNT_ID = "output_remove_count";
    
    public RemoveItemNode() {
        super(UUID.randomUUID(), "math.list.remove_item");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to remove from", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort indexInput = new BasePort(INPUT_INDEX_ID, "Index", 
                "The index of the item to remove (0-based)", NodeDataType.INTEGER, this);
        addInputPort(indexInput);
        
        IPort valueInput = new BasePort(INPUT_VALUE_ID, "Value", 
                "The value to remove (used if 'Use Index' is false)", NodeDataType.ANY, this);
        addInputPort(valueInput);
        
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Modified List", 
                "The list with item(s) removed", NodeDataType.LIST, this);
        addOutputPort(listOutput);
        
        IPort removedOutput = new BasePort(OUTPUT_REMOVED_ID, "Removed Item", 
                "The removed item (first one if multiple)", NodeDataType.ANY, this);
        addOutputPort(removedOutput);
        
        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Remove Count", 
                "The number of items removed", NodeDataType.INTEGER, this);
        addOutputPort(countOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        boolean hasValueInput = inputValues.containsKey(INPUT_VALUE_ID);
        
        List<Object> resultList = new ArrayList<>();
        Object removedItem = null;
        int removeCount = 0;
        
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            
            for (Object item : inputList) {
                resultList.add(item);
            }
            
            if (useIndex && indexObj instanceof Number) {
                int index = ((Number) indexObj).intValue();
                int listSize = resultList.size();
                
                if (index < 0 && allowNegativeIndex) {
                    index = listSize + index;
                }
                
                if (index >= 0 && index < listSize) {
                    removedItem = resultList.remove(index);
                    removeCount = 1;
                }
            } 
            else if (!useIndex && hasValueInput) {
                if (removeAllMatches) {
                    List<Object> toRemove = new ArrayList<>();
                    
                    for (Object item : resultList) {
                        if (Objects.equals(item, valueObj)) {
                            toRemove.add(item);
                            removeCount++;
                            
                            if (removedItem == null) {
                                removedItem = item;
                            }
                        }
                    }
                    
                    resultList.removeAll(toRemove);
                } 
                else {
                    int index = resultList.indexOf(valueObj);
                    if (index >= 0) {
                        removedItem = resultList.remove(index);
                        removeCount = 1;
                    }
                }
            }
        }
        
        outputValues.put(OUTPUT_LIST_ID, resultList);
        outputValues.put(OUTPUT_REMOVED_ID, removedItem);
        outputValues.put(OUTPUT_COUNT_ID, removeCount);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseIndex() {
        return useIndex;
    }
    
    public void setUseIndex(boolean useIndex) {
        if (this.useIndex != useIndex) {
            this.useIndex = useIndex;
            markDirty();
        }
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
    
    public boolean isRemoveAllMatches() {
        return removeAllMatches;
    }
    
    public void setRemoveAllMatches(boolean removeAll) {
        if (this.removeAllMatches != removeAll) {
            this.removeAllMatches = removeAll;
            markDirty();
        }
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useIndex", isUseIndex());
        state.put("allowNegativeIndex", isAllowNegativeIndex());
        state.put("removeAllMatches", isRemoveAllMatches());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useIndex")) {
                Object useIdx = stateMap.get("useIndex");
                if (useIdx instanceof Boolean) {
                    setUseIndex((Boolean) useIdx);
                }
            }
            
            if (stateMap.containsKey("allowNegativeIndex")) {
                Object allow = stateMap.get("allowNegativeIndex");
                if (allow instanceof Boolean) {
                    setAllowNegativeIndex((Boolean) allow);
                }
            }
            
            if (stateMap.containsKey("removeAllMatches")) {
                Object removeAll = stateMap.get("removeAllMatches");
                if (removeAll instanceof Boolean) {
                    setRemoveAllMatches((Boolean) removeAll);
                }
            }
        }
    }
} 
