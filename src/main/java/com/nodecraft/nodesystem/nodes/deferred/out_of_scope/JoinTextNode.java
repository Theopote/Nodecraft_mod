package com.nodecraft.nodesystem.nodes.deferred.out_of_scope;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Join Text                                                               
 */
@NodeInfo(
    id = "deferred.out_of_scope.join_text",
    displayName = "Join Text",
    description = "Joins a list of strings into a single string using a delimiter.",
    category = "deferred.out_of_scope"
)
public class JoinTextNode extends BaseNode {
    
    // ---              ?---
    private String defaultDelimiter = ", "; //                ?
    private String description = "Joins a list of strings into a single string with a delimiter"; //              ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_DELIMITER_ID = "input_delimiter";
    private static final String OUTPUT_TEXT_ID = "output_text";
    
    /**
     *                                         ?
     */
    public JoinTextNode() {
        super(UUID.randomUUID(), "deferred.out_of_scope.join_text");
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list of strings to join", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort delimiterInput = new BasePort(INPUT_DELIMITER_ID, "Delimiter", 
                "The delimiter to join with (default: comma+space)", NodeDataType.STRING, this);
        addInputPort(delimiterInput);
        
        //                    ?
        IPort textOutput = new BasePort(OUTPUT_TEXT_ID, "Text", 
                "The joined string", NodeDataType.STRING, this);
        addOutputPort(textOutput);
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
        Object delimiterObj = inputValues.get(INPUT_DELIMITER_ID);
        
        //                                          ?
        String delimiter = defaultDelimiter;
        if (delimiterObj instanceof String) {
            delimiter = (String) delimiterObj;
        }
        
        //                       ?
        String result = "";
        
        if (listObj instanceof List) {
            List<?> list = (List<?>) listObj;
            StringBuilder sb = new StringBuilder();
            
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(delimiter);
                }
                
                //                                       ?
                if (item != null) {
                    sb.append(item.toString());
                }
                
                first = false;
            }
            
            result = sb.toString();
        }
        
        //              ?
        outputValues.put(OUTPUT_TEXT_ID, result);
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getDefaultDelimiter() {
        return defaultDelimiter;
    }
    
    public void setDefaultDelimiter(String delimiter) {
        this.defaultDelimiter = delimiter != null ? delimiter : ", ";
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("defaultDelimiter", getDefaultDelimiter());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("defaultDelimiter")) {
                Object delimiterObj = stateMap.get("defaultDelimiter");
                if (delimiterObj instanceof String) {
                    setDefaultDelimiter((String) delimiterObj);
                }
            }
        }
    }
} 
