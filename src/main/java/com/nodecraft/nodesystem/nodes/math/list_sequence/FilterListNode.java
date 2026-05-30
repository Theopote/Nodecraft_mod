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

/**
 *                                                                                  ?
 */
@NodeInfo(
    id = "math.list.filter_list",
    displayName = "Filter List",
    description = "Filters a list based on boolean conditions",
    category = "math.list"
)
public class FilterListNode extends BaseNode {
    
    // ---              ?---
    private boolean invert = false; //                                                  lse         ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_COUNT_ID = "output_count";
    
    /**
     *                                         ?
     */
    public FilterListNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.filter_list");
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to filter", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort conditionInput = new BasePort(INPUT_CONDITION_ID, "Condition", 
                "Boolean list or single boolean value", NodeDataType.ANY, this);
        addInputPort(conditionInput);
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Filtered List", 
                "The list after filtering", NodeDataType.LIST, this);
        addOutputPort(listOutput);
        
        IPort removedOutput = new BasePort(OUTPUT_REMOVED_ID, "Removed Items", 
                "Items that were filtered out", NodeDataType.LIST, this);
        addOutputPort(removedOutput);
        
        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Count", 
                "Number of items after filtering", NodeDataType.INTEGER, this);
        addOutputPort(countOutput);
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        //              ?
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object conditionObj = inputValues.get(INPUT_CONDITION_ID);
        
        List<Object> filteredList = new ArrayList<>();
        List<Object> removedList = new ArrayList<>();
        
        //              ?
        if (listObj instanceof List) {
            List<?> inputList = (List<?>) listObj;
            
            //                                                    ?
            if (conditionObj instanceof List) {
                List<?> conditionList = (List<?>) conditionObj;
                filterWithConditionList(inputList, conditionList, filteredList, removedList);
            } else if (conditionObj instanceof Boolean) {
                //                ?-                             ?
                boolean condition = (Boolean) conditionObj;
                if (condition != invert) { //         vert                           
                    filteredList.addAll(inputList);
                } else {
                    removedList.addAll(inputList);
                }
            } else {
                //          ?-              ?
                removedList.addAll(inputList);
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, filteredList);
        outputValues.put(OUTPUT_REMOVED_ID, removedList);
        outputValues.put(OUTPUT_COUNT_ID, filteredList.size());
    }
    
    /**
     *                                  ?
     */
    private void filterWithConditionList(List<?> inputList, List<?> conditionList, 
                                       List<Object> filteredList, List<Object> removedList) {
        //                ?
        for (int i = 0; i < inputList.size(); i++) {
            Object item = inputList.get(i);
            
            //              ?
            boolean keep = false;
            if (i < conditionList.size()) {
                Object condObj = conditionList.get(i);
                if (condObj instanceof Boolean) {
                    keep = (Boolean) condObj;
                }
            }
            
            //         vert                    ?
            if (invert) {
                keep = !keep;
            }
            
            //                     ?
            if (keep) {
                filteredList.add(item);
            } else {
                removedList.add(item);
            }
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isInvert() {
        return invert;
    }
    
    public void setInvert(boolean invert) {
        if (this.invert != invert) {
            this.invert = invert;
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("invert", isInvert());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("invert")) {
                Object invertObj = stateMap.get("invert");
                if (invertObj instanceof Boolean) {
                    setInvert((Boolean) invertObj);
                }
            }
        }
    }
} 
