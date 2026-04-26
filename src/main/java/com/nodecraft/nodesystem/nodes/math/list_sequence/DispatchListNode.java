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
 *                                                                                     ?
 */
@NodeInfo(
    id = "math.list.dispatch_list",
    displayName = "Dispatch List",
    description = "Splits a list into two based on boolean conditions",
    category = "math.list"
)
public class DispatchListNode extends BaseNode {
    
    // ---              ?---
    private boolean useDefaultValue = false; //                                                         ?
    private boolean defaultValue = false; //                ?
    private String description; //                    ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String OUTPUT_TRUE_LIST_ID = "output_true";
    private static final String OUTPUT_FALSE_LIST_ID = "output_false";
    
    /**
     *                                         ?
     */
    public DispatchListNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.dispatch_list");
        
        //                    ?
        this.description = "Splits a list into two based on boolean conditions";
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to split", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort conditionInput = new BasePort(INPUT_CONDITION_ID, "Condition", 
                "Boolean list or single boolean value", NodeDataType.ANY, this);
        addInputPort(conditionInput);
        
        //                    ?
        IPort trueOutput = new BasePort(OUTPUT_TRUE_LIST_ID, "True List", 
                "Items for which the condition was true", NodeDataType.LIST, this);
        addOutputPort(trueOutput);
        
        IPort falseOutput = new BasePort(OUTPUT_FALSE_LIST_ID, "False List", 
                "Items for which the condition was false", NodeDataType.LIST, this);
        addOutputPort(falseOutput);
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
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object conditionObj = inputValues.get(INPUT_CONDITION_ID);
        
        List<Object> trueList = new ArrayList<>();
        List<Object> falseList = new ArrayList<>();
        
        //              ?
        if (listObj instanceof List) {
            List<?> inputList = (List<?>) listObj;
            
            //                                                    ?
            if (conditionObj instanceof List) {
                List<?> conditionList = (List<?>) conditionObj;
                processWithConditionList(inputList, conditionList, trueList, falseList);
            } else if (conditionObj instanceof Boolean) {
                //                ?-                           ?
                boolean condition = (Boolean) conditionObj;
                if (condition) {
                    trueList.addAll(inputList);
                } else {
                    falseList.addAll(inputList);
                }
            } else {
                //          ?-                       ?
                if (useDefaultValue && defaultValue) {
                    trueList.addAll(inputList);
                } else {
                    falseList.addAll(inputList);
                }
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_TRUE_LIST_ID, trueList);
        outputValues.put(OUTPUT_FALSE_LIST_ID, falseList);
    }
    
    /**
     *                                  ?
     */
    private void processWithConditionList(List<?> inputList, List<?> conditionList, 
                                         List<Object> trueList, List<Object> falseList) {
        //                ?
        for (int i = 0; i < inputList.size(); i++) {
            Object item = inputList.get(i);
            
            //              ?
            boolean condition;
            if (i < conditionList.size()) {
                Object condObj = conditionList.get(i);
                if (condObj instanceof Boolean) {
                    condition = (Boolean) condObj;
                } else {
                    //                           lse
                    condition = false;
                }
            } else {
                //                            ?
                condition = useDefaultValue ? defaultValue : false;
            }
            
            //                     ?
            if (condition) {
                trueList.add(item);
            } else {
                falseList.add(item);
            }
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseDefaultValue() {
        return useDefaultValue;
    }
    
    public void setUseDefaultValue(boolean use) {
        this.useDefaultValue = use;
        markDirty();
    }
    
    public boolean getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(boolean value) {
        this.defaultValue = value;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useDefaultValue", isUseDefaultValue());
        state.put("defaultValue", getDefaultValue());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useDefaultValue")) {
                Object use = stateMap.get("useDefaultValue");
                if (use instanceof Boolean) {
                    setUseDefaultValue((Boolean) use);
                }
            }
            
            if (stateMap.containsKey("defaultValue")) {
                Object value = stateMap.get("defaultValue");
                if (value instanceof Boolean) {
                    setDefaultValue((Boolean) value);
                }
            }
        }
    }
} 
