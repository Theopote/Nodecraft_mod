package com.nodecraft.nodesystem.nodes.deferred.out_of_scope;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Text Length                                     ?
 */
@NodeInfo(
    id = "deferred.out_of_scope.text_length",
    displayName = "Text Length",
    description = "Gets the length of a text string, optionally ignoring whitespace.",
    category = "deferred.out_of_scope"
)
public class TextLengthNode extends BaseNode {
    
    // ---              ?---
    private boolean ignoreWhitespace = false; //                           ?
    private String description = "Gets the length of a string"; //              ?
    
    // ---       ?              D ---
    private static final String INPUT_TEXT_ID = "input_text";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    
    /**
     *                                         ?
     */
    public TextLengthNode() {
        super(UUID.randomUUID(), "deferred.out_of_scope.text_length");
        
        //                    ?
        IPort textInput = new BasePort(INPUT_TEXT_ID, "Text", 
                "The input text", NodeDataType.STRING, this);
        addInputPort(textInput);
        
        //                    ?
        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length", 
                "The length of the text", NodeDataType.INTEGER, this);
        addOutputPort(lengthOutput);
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
        
        //                ?
        int length = 0;
        
        //                       ?
        if (textObj != null) {
            String text = textObj.toString();
            
            if (ignoreWhitespace) {
                //                                              ?
                text = text.replaceAll("\\s", "");
            }
            
            length = text.length();
        }
        
        //              ?
        outputValues.put(OUTPUT_LENGTH_ID, length);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isIgnoreWhitespace() {
        return ignoreWhitespace;
    }
    
    public void setIgnoreWhitespace(boolean ignore) {
        this.ignoreWhitespace = ignore;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("ignoreWhitespace", isIgnoreWhitespace());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("ignoreWhitespace")) {
                Object ignoreObj = stateMap.get("ignoreWhitespace");
                if (ignoreObj instanceof Boolean) {
                    setIgnoreWhitespace((Boolean) ignoreObj);
                }
            }
        }
    }
} 
