package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *                                                                       ?
 */
@NodeInfo(
    id = "math.list.group_list",
    displayName = "Group List",
    description = "Groups items in a list based on a key list",
    category = "math.list"
)
public class GroupListNode extends BaseNode {
    
    // ---              ?---
    private boolean skipInvalidKeys = false; //                            
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_KEYS_ID = "input_keys";
    private static final String OUTPUT_GROUPS_ID = "output_groups";
    private static final String OUTPUT_KEYS_ID = "output_unique_keys";
    private static final String OUTPUT_COUNT_ID = "output_group_count";
    
    /**
     *                                         ?
     */
    public GroupListNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.group_list");
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to group", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort keysInput = new BasePort(INPUT_KEYS_ID, "Keys", 
                "List of keys to group by", NodeDataType.LIST, this);
        addInputPort(keysInput);
        
        //                    ?
        IPort groupsOutput = new BasePort(OUTPUT_GROUPS_ID, "Groups", 
                "List of grouped items (lists)", NodeDataType.LIST, this);
        addOutputPort(groupsOutput);
        
        IPort keysOutput = new BasePort(OUTPUT_KEYS_ID, "Unique Keys", 
                "List of unique keys found", NodeDataType.LIST, this);
        addOutputPort(keysOutput);
        
        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Group Count", 
                "Number of groups created", NodeDataType.INTEGER, this);
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
        Object keysObj = inputValues.get(INPUT_KEYS_ID);
        
        //                ?
        Map<Object, List<Object>> groups = new HashMap<>();
        List<Object> uniqueKeys = new ArrayList<>();
        
        //              ?
        if (listObj instanceof List && keysObj instanceof List) {
            List<?> inputList = (List<?>) listObj;
            List<?> keysList = (List<?>) keysObj;
            
            //                             ?
            for (int i = 0; i < inputList.size(); i++) {
                Object item = inputList.get(i);
                
                //                              ?
                Object key = null;
                if (i < keysList.size()) {
                    key = keysList.get(i);
                }
                
                //                ?
                if (key == null && skipInvalidKeys) {
                    continue;
                }
                
                //                                   ?
                if (!groups.containsKey(key)) {
                    //                        ?
                    List<Object> group = new ArrayList<>();
                    group.add(item);
                    groups.put(key, group);
                    uniqueKeys.add(key);
                } else {
                    //                                  ?
                    groups.get(key).add(item);
                }
            }
        }
        
        //                            ?
        List<Object> groupsList = new ArrayList<>();
        for (Object key : uniqueKeys) {
            groupsList.add(groups.get(key));
        }
        
        //              ?
        outputValues.put(OUTPUT_GROUPS_ID, groupsList);
        outputValues.put(OUTPUT_KEYS_ID, uniqueKeys);
        outputValues.put(OUTPUT_COUNT_ID, uniqueKeys.size());
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isSkipInvalidKeys() {
        return skipInvalidKeys;
    }
    
    public void setSkipInvalidKeys(boolean skip) {
        if (this.skipInvalidKeys != skip) {
            this.skipInvalidKeys = skip;
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("skipInvalidKeys", isSkipInvalidKeys());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("skipInvalidKeys")) {
                Object skip = stateMap.get("skipInvalidKeys");
                if (skip instanceof Boolean) {
                    setSkipInvalidKeys((Boolean) skip);
                }
            }
        }
    }
} 
