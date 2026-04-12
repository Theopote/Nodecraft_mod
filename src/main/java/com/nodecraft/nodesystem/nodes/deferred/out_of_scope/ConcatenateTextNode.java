package com.nodecraft.nodesystem.nodes.deferred.out_of_scope;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Concatenate Text                                                       
 */
@NodeInfo(
    id = "deferred.out_of_scope.concatenate_text",
    displayName = "Concatenate Text",
    description = "Concatenates multiple text inputs into a single string.",
    category = "deferred.out_of_scope"
)
public class ConcatenateTextNode extends BaseNode {
    
    // ---              ?---
    private boolean addSpacesBetween = true; //                                                ?
    private int inputCount = 3; //                    ?
    private String description = "Concatenates multiple text inputs into a single string"; //              ?
    
    // ---       ?              D      ?---
    private static final String INPUT_PREFIX = "input_text_";
    private static final String OUTPUT_RESULT_ID = "output_result";
    
    /**
     *                                         ?
     */
    public ConcatenateTextNode() {
        //             ndReplaceTextNode                              ?
        super(UUID.randomUUID(), "deferred.out_of_scope.concatenate_text");
        
        //                            ?
        recreateInputPorts();
        
        //                    ?
        IPort resultOutput = new BasePort(OUTPUT_RESULT_ID, "Result", 
                "The concatenated string", NodeDataType.STRING, this);
        addOutputPort(resultOutput);
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
     *                                 ?
     */
    private void recreateInputPorts() {
        //                            
        inputPorts.clear();
        
        //                            
        for (int i = 0; i < inputCount; i++) {
            String portId = INPUT_PREFIX + i;
            String displayName = "Text " + (i + 1);
            IPort textInput = new BasePort(portId, displayName, 
                    "Text input " + (i + 1), NodeDataType.ANY, this);
            addInputPort(textInput);
        }
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        StringBuilder result = new StringBuilder();
        
        //                               ?
        boolean isFirst = true;
        for (int i = 0; i < inputCount; i++) {
            String portId = INPUT_PREFIX + i;
            Object input = inputValues.get(portId);
            
            if (input != null) {
                String textValue = input.toString();
                
                //                                                ?
                if (!isFirst && addSpacesBetween) {
                    result.append(" ");
                }
                
                result.append(textValue);
                isFirst = false;
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_RESULT_ID, result.toString());
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isAddSpacesBetween() {
        return addSpacesBetween;
    }
    
    public void setAddSpacesBetween(boolean addSpaces) {
        this.addSpacesBetween = addSpaces;
        markDirty();
    }
    
    public int getInputCount() {
        return inputCount;
    }
    
    public void setInputCount(int count) {
        //                            
        count = Math.max(1, Math.min(10, count)); //          ?-10      ?
        
        if (this.inputCount != count) {
            this.inputCount = count;
            recreateInputPorts();
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("addSpacesBetween", isAddSpacesBetween());
        state.put("inputCount", getInputCount());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("addSpacesBetween")) {
                Object addSpacesObj = stateMap.get("addSpacesBetween");
                if (addSpacesObj instanceof Boolean) {
                    setAddSpacesBetween((Boolean) addSpacesObj);
                }
            }
            
            if (stateMap.containsKey("inputCount")) {
                Object countObj = stateMap.get("inputCount");
                if (countObj instanceof Number) {
                    setInputCount(((Number) countObj).intValue());
                }
            }
        }
    }
} 
