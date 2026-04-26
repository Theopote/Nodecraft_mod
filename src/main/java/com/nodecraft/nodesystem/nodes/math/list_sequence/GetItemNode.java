package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import com.nodecraft.nodesystem.api.NodeInfo;

import java.util.List;
import java.util.UUID;

/**
 *                                                                             ?
 */
@NodeInfo(
    id = "math.list.get_item",
    displayName = "Get Item",
    description = "Gets an item from a list at a specified index.",
    category = "math.list"
)
public class GetItemNode extends BaseNode {
    
    // ---              ?---
    private boolean allowNegativeIndex = true; //                                                              ?
    private boolean wrapIndex = false; //                                     ?
    private String description = "Gets an item from a list at a specified index"; //              ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_FOUND_ID = "output_found";
    
    /**
     *                                             ?
     */
    public GetItemNode() {
        //                    ?- data.lists.get_item
        super(UUID.randomUUID(), "math.list.get_item");
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to get an item from", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort indexInput = new BasePort(INPUT_INDEX_ID, "Index", 
                "The index of the item (0-based)", NodeDataType.INTEGER, this);
        addInputPort(indexInput);
        
        //                    ?
        IPort itemOutput = new BasePort(OUTPUT_ITEM_ID, "Item", 
                "The item at the specified index", NodeDataType.ANY, this);
        addOutputPort(itemOutput);
        
        IPort foundOutput = new BasePort(OUTPUT_FOUND_ID, "Found", 
                "Whether an item was found at the specified index", NodeDataType.BOOLEAN, this);
        addOutputPort(foundOutput);
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
        //                               ?
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        
        Object item = null;
        boolean found = false;
        
        //                 ?
        if (inputObj instanceof List && indexObj instanceof Number) {
            List<?> list = (List<?>) inputObj;
            int listSize = list.size();
            
            if (listSize > 0) {
                int index = ((Number) indexObj).intValue();
                
                //                                                       ?
                if (index < 0 && allowNegativeIndex) {
                    index = listSize + index;
                }
                
                //                    ?
                if (wrapIndex && listSize > 0) {
                    //                                                        ?
                    index = ((index % listSize) + listSize) % listSize;
                    found = true;
                    item = list.get(index);
                } else if (index >= 0 && index < listSize) {
                    //                    ?
                    found = true;
                    item = list.get(index);
                }
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_ITEM_ID, item);
        outputValues.put(OUTPUT_FOUND_ID, found);
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
    
    // ---                         ?---
    
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
