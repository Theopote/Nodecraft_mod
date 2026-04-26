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
 *                                                                           ?
 */
@NodeInfo(
    id = "math.list.set_item",
    displayName = "Set Item",
    description = "Sets an item in a list at a specified index",
    category = "math.list"
)
public class SetItemNode extends BaseNode {
    
    // ---              ?---
    private boolean allowNegativeIndex = true; //                                                              ?
    private boolean wrapIndex = false; //                                     ?
    private boolean expandList = false; //                                                   ?
    private String description; //                    ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    
    /**
     *                                              
     */
    public SetItemNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.set_item");
        
        //                    ?
        this.description = "Sets an item in a list at a specified index";
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to modify", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort indexInput = new BasePort(INPUT_INDEX_ID, "Index", 
                "The index of the item to set (0-based)", NodeDataType.INTEGER, this);
        addInputPort(indexInput);
        
        IPort valueInput = new BasePort(INPUT_VALUE_ID, "Value", 
                "The new value to set at the specified index", NodeDataType.ANY, this);
        addInputPort(valueInput);
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Modified List", 
                "The modified list with the new value set", NodeDataType.LIST, this);
        addOutputPort(listOutput);
        
        IPort successOutput = new BasePort(OUTPUT_SUCCESS_ID, "Success", 
                "Whether the operation was successful", NodeDataType.BOOLEAN, this);
        addOutputPort(successOutput);
    }
    
    /**
     *         ode            tDescription      ?
     * @return              ?
     */
    @Override
    public String getDescription() {
        return this.description;
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        //              ?
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        
        List<Object> resultList = new ArrayList<>();
        boolean success = false;
        
        //              ?
        if (inputObj instanceof List && indexObj instanceof Number) {
            List<?> inputList = (List<?>) inputObj;
            
            //                                       ?
            for (Object item : inputList) {
                resultList.add(item);
            }
            
            int listSize = resultList.size();
            int index = ((Number) indexObj).intValue();
            
            //                                                       ?
            if (index < 0 && allowNegativeIndex) {
                index = listSize + index;
            }
            
            //                    ?
            if (wrapIndex && listSize > 0) {
                //                                                        ?
                index = ((index % listSize) + listSize) % listSize;
                resultList.set(index, valueObj);
                success = true;
            } else if (index >= 0 && index < listSize) {
                //                    ?
                resultList.set(index, valueObj);
                success = true;
            } else if (expandList && index >= 0) {
                //                                ?
                while (resultList.size() <= index) {
                    resultList.add(null);
                }
                resultList.set(index, valueObj);
                success = true;
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, resultList);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isAllowNegativeIndex() {
        return allowNegativeIndex;
    }
    
    public void setAllowNegativeIndex(boolean allow) {
        this.allowNegativeIndex = allow;
        markDirty();
    }
    
    public boolean isWrapIndex() {
        return wrapIndex;
    }
    
    public void setWrapIndex(boolean wrap) {
        this.wrapIndex = wrap;
        markDirty();
    }
    
    public boolean isExpandList() {
        return expandList;
    }
    
    public void setExpandList(boolean expand) {
        this.expandList = expand;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("allowNegativeIndex", isAllowNegativeIndex());
        state.put("wrapIndex", isWrapIndex());
        state.put("expandList", isExpandList());
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
            
            if (stateMap.containsKey("expandList")) {
                Object expand = stateMap.get("expandList");
                if (expand instanceof Boolean) {
                    setExpandList((Boolean) expand);
                }
            }
        }
    }
} 
