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
    id = "math.list.sub_list",
    displayName = "Sub List",
    description = "Gets a portion of a list between start and end indexes.",
    category = "math.list"
)
public class SubListNode extends BaseNode {
    
    private boolean allowNegativeIndex = true;
    private boolean clampToList = true;
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String OUTPUT_SUBLIST_ID = "output_sublist";
    
    public SubListNode() {
        super(UUID.randomUUID(), "math.list.sub_list");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The source list", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort startInput = new BasePort(INPUT_START_ID, "Start Index", 
                "The starting index (inclusive, 0-based)", NodeDataType.INTEGER, this);
        addInputPort(startInput);
        
        IPort endInput = new BasePort(INPUT_END_ID, "End Index", 
                "The ending index (exclusive)", NodeDataType.INTEGER, this);
        addInputPort(endInput);
        
        IPort sublistOutput = new BasePort(OUTPUT_SUBLIST_ID, "Sub List", 
                "The resulting sub list", NodeDataType.LIST, this);
        addOutputPort(sublistOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object startObj = inputValues.get(INPUT_START_ID);
        Object endObj = inputValues.get(INPUT_END_ID);
        
        List<Object> resultList = new ArrayList<>();
        
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            int listSize = inputList.size();
            
            int startIndex = 0;
            int endIndex = listSize;
            
            if (startObj instanceof Number) {
                startIndex = ((Number) startObj).intValue();
                
                if (startIndex < 0 && allowNegativeIndex) {
                    startIndex = listSize + startIndex;
                }
                
                if (clampToList) {
                    startIndex = Math.max(0, Math.min(startIndex, listSize));
                } else if (startIndex < 0 || startIndex > listSize) {
                    outputValues.put(OUTPUT_SUBLIST_ID, resultList);
                    return;
                }
            }
            
            if (endObj instanceof Number) {
                endIndex = ((Number) endObj).intValue();
                
                if (endIndex < 0 && allowNegativeIndex) {
                    endIndex = listSize + endIndex;
                }
                
                if (clampToList) {
                    endIndex = Math.max(startIndex, Math.min(endIndex, listSize));
                } else if (endIndex < startIndex || endIndex > listSize) {
                    outputValues.put(OUTPUT_SUBLIST_ID, resultList);
                    return;
                }
            }
            
            for (int i = startIndex; i < endIndex; i++) {
                resultList.add(inputList.get(i));
            }
        }
        
        outputValues.put(OUTPUT_SUBLIST_ID, resultList);
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
    
    public boolean isClampToList() {
        return clampToList;
    }
    
    public void setClampToList(boolean clamp) {
        if (this.clampToList != clamp) {
            this.clampToList = clamp;
            markDirty();
        }
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("allowNegativeIndex", isAllowNegativeIndex());
        state.put("clampToList", isClampToList());
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
            
            if (stateMap.containsKey("clampToList")) {
                Object clamp = stateMap.get("clampToList");
                if (clamp instanceof Boolean) {
                    setClampToList((Boolean) clamp);
                }
            }
        }
    }
} 
