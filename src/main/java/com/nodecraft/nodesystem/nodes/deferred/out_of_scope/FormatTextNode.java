package com.nodecraft.nodesystem.nodes.deferred.out_of_scope;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Format Text                                           ?
 */
@NodeInfo(
    id = "deferred.out_of_scope.format_text",
    displayName = "Format Text",
    description = "Formats text using placeholders (e.g., 'X: {0}, Y: {1}')",
    category = "deferred.out_of_scope"
)
public class FormatTextNode extends BaseNode {
    
    // ---              ?---
    private String formatTemplate = "Value: {0}"; //                     ?
    private int argCount = 3; //                    ?
    private String description = "Formats text using placeholders (e.g., 'X: {0}, Y: {1}')"; //              ?
    
    // ---       ?              D ---
    private static final String INPUT_FORMAT_ID = "input_format";
    private static final String INPUT_ARG_PREFIX = "input_arg_";
    private static final String OUTPUT_RESULT_ID = "output_result";
    
    /**
     *                                               
     */
    public FormatTextNode() {
        super(UUID.randomUUID(), "deferred.out_of_scope.format_text");
        
        //                    ?-       ?
        IPort formatInput = new BasePort(INPUT_FORMAT_ID, "Format", 
                "Text format with placeholders {0}, {1}, etc.", NodeDataType.STRING, this);
        addInputPort(formatInput);
        
        //                            
        recreateArgInputPorts();
        
        //                    ?
        IPort resultOutput = new BasePort(OUTPUT_RESULT_ID, "Result", 
                "The formatted string", NodeDataType.STRING, this);
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
     *                                         ?
     */
    private void recreateArgInputPorts() {
        //                          ?
        List<IPort> portsToKeep = new ArrayList<>();
        for (IPort port : inputPorts) {
            if (!port.getId().startsWith(INPUT_ARG_PREFIX)) {
                portsToKeep.add(port);
            }
        }
        
        inputPorts.clear();
        for (IPort port : portsToKeep) {
            addInputPort(port);
        }
        
        //                            ?
        for (int i = 0; i < argCount; i++) {
            String portId = INPUT_ARG_PREFIX + i;
            String displayName = "Arg " + i;
            IPort argInput = new BasePort(portId, displayName, 
                    "Argument for placeholder {" + i + "}", NodeDataType.ANY, this);
            addInputPort(argInput);
        }
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        //                        ?
        String format = formatTemplate;
        Object formatObj = inputValues.get(INPUT_FORMAT_ID);
        if (formatObj instanceof String) {
            format = (String) formatObj;
        }
        
        //                    ?
        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            String argPortId = INPUT_ARG_PREFIX + i;
            args[i] = inputValues.get(argPortId);
            
            //               ull            ssageFormat            ll ?
            if (args[i] == null) {
                args[i] = "";
            }
        }
        
        //                ?
        String result;
        try {
            result = MessageFormat.format(format, args);
        } catch (Exception e) {
            //                                                           ?
            result = format;
        }
        
        //              ?
        outputValues.put(OUTPUT_RESULT_ID, result);
    }
    
    // --- Getters/Setters for Properties ---
    
    public String getFormatTemplate() {
        return formatTemplate;
    }
    
    public void setFormatTemplate(String template) {
        this.formatTemplate = template != null ? template : "";
        markDirty();
    }
    
    public int getArgCount() {
        return argCount;
    }
    
    public void setArgCount(int count) {
        //                            
        count = Math.max(0, Math.min(10, count)); //          ?-10      ?
        
        if (this.argCount != count) {
            this.argCount = count;
            recreateArgInputPorts();
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("formatTemplate", getFormatTemplate());
        state.put("argCount", getArgCount());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("formatTemplate")) {
                Object templateObj = stateMap.get("formatTemplate");
                if (templateObj instanceof String) {
                    setFormatTemplate((String) templateObj);
                }
            }
            
            if (stateMap.containsKey("argCount")) {
                Object countObj = stateMap.get("argCount");
                if (countObj instanceof Number) {
                    setArgCount(((Number) countObj).intValue());
                }
            }
        }
    }
} 
