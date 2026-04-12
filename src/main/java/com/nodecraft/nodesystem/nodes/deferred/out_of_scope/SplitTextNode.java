package com.nodecraft.nodesystem.nodes.deferred.out_of_scope;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Split Text                                                  ?
 */
@NodeInfo(
    id = "deferred.out_of_scope.split_text",
    displayName = "Split Text",
    description = "Splits text into a list using a delimiter.",
    category = "deferred.out_of_scope"
)
public class SplitTextNode extends BaseNode {
    
    // ---              ?---
    private String defaultDelimiter = ","; //                ?
    private boolean trimResults = true;    //                                     ?
    private boolean skipEmptyResults = false; //                       ?
    private String description = "Splits text into a list using a delimiter"; //              ?
    
    // ---       ?              D ---
    private static final String INPUT_TEXT_ID = "input_text";
    private static final String INPUT_DELIMITER_ID = "input_delimiter";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_COUNT_ID = "output_count";
    
    /**
     *                                         ?
     */
    public SplitTextNode() {
        super(UUID.randomUUID(), "deferred.out_of_scope.split_text");
        
        //                    ?
        IPort textInput = new BasePort(INPUT_TEXT_ID, "Text", 
                "The input text to split", NodeDataType.STRING, this);
        addInputPort(textInput);
        
        IPort delimiterInput = new BasePort(INPUT_DELIMITER_ID, "Delimiter", 
                "The delimiter to split by (default: comma)", NodeDataType.STRING, this);
        addInputPort(delimiterInput);
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "List", 
                "The resulting list of substrings", NodeDataType.LIST, this);
        addOutputPort(listOutput);
        
        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Count", 
                "The number of items in the result", NodeDataType.INTEGER, this);
        addOutputPort(countOutput);
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
        Object textObj = inputValues.get(INPUT_TEXT_ID);
        Object delimiterObj = inputValues.get(INPUT_DELIMITER_ID);
        
        //                    ?
        String text = "";
        if (textObj instanceof String) {
            text = (String) textObj;
        } else if (textObj != null) {
            text = textObj.toString();
        }
        
        //                                          ?
        String delimiter = defaultDelimiter;
        if (delimiterObj instanceof String) {
            delimiter = (String) delimiterObj;
            if (delimiter.isEmpty()) {
                delimiter = defaultDelimiter;
            }
        }
        
        //              ?
        String[] parts = text.split(java.util.regex.Pattern.quote(delimiter), -1);
        List<String> result = new ArrayList<>();
        
        //              ?
        for (String part : parts) {
            //                                   ?
            if (trimResults) {
                part = part.trim();
            }
            
            //                                             ?
            if (skipEmptyResults && part.isEmpty()) {
                continue;
            }
            
            result.add(part);
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, result);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getDefaultDelimiter() {
        return defaultDelimiter;
    }
    
    public void setDefaultDelimiter(String delimiter) {
        this.defaultDelimiter = delimiter != null ? delimiter : ",";
        markDirty();
    }
    
    public boolean isTrimResults() {
        return trimResults;
    }
    
    public void setTrimResults(boolean trim) {
        this.trimResults = trim;
        markDirty();
    }
    
    public boolean isSkipEmptyResults() {
        return skipEmptyResults;
    }
    
    public void setSkipEmptyResults(boolean skip) {
        this.skipEmptyResults = skip;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("defaultDelimiter", getDefaultDelimiter());
        state.put("trimResults", isTrimResults());
        state.put("skipEmptyResults", isSkipEmptyResults());
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
            
            if (stateMap.containsKey("trimResults")) {
                Object trimObj = stateMap.get("trimResults");
                if (trimObj instanceof Boolean) {
                    setTrimResults((Boolean) trimObj);
                }
            }
            
            if (stateMap.containsKey("skipEmptyResults")) {
                Object skipObj = stateMap.get("skipEmptyResults");
                if (skipObj instanceof Boolean) {
                    setSkipEmptyResults((Boolean) skipObj);
                }
            }
        }
    }
} 
