package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 *                                                                        ?
 */
@NodeInfo(
    id = "math.sequence.repeat",
    displayName = "Repeat",
    description = "Repeats a single data item or list multiple times",
    category = "math.sequence"
)
public class RepeatNode extends BaseNode {
    
    // ---              ?---
    private int defaultCount = 3; //                    ?
    
    // ---       ?              D ---
    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    
    /**
     *                                         ?
     */
    public RepeatNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.sequence.repeat");
        
        //                    ?
        IPort dataInput = new BasePort(INPUT_DATA_ID, "Data", 
                "The data to repeat (single value or list)", NodeDataType.ANY, this);
        addInputPort(dataInput);
        
        IPort countInput = new BasePort(INPUT_COUNT_ID, "Count", 
                "Number of times to repeat", NodeDataType.INTEGER, this);
        addInputPort(countInput);
        
        //                    ?
        IPort resultOutput = new BasePort(OUTPUT_RESULT_ID, "Result", 
                "The repeated data as a list", NodeDataType.LIST, this);
        addOutputPort(resultOutput);
        
        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length", 
                "Length of the resulting list", NodeDataType.INTEGER, this);
        addOutputPort(lengthOutput);
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        //              ?
        Object dataObj = inputValues.get(INPUT_DATA_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        
        int count = defaultCount;
        if (countObj instanceof Number) {
            count = ((Number) countObj).intValue();
        }

        //                                         ?
        if (dataObj instanceof List) {
            List<?> inputList = (List<?>) dataObj;
            count = GenerationLimits.clampRepeatCount(count, inputList.size());
            List<Object> result = new ArrayList<>();
            
            //                           ?
            for (int i = 0; i < count; i++) {
                result.addAll(inputList);
            }
            
            //              ?
            outputValues.put(OUTPUT_RESULT_ID, result);
            outputValues.put(OUTPUT_LENGTH_ID, result.size());
        } 
        //                                              
        else {
            count = GenerationLimits.clampNonNegativeCount(count);
            List<Object> result = new ArrayList<>();
            
            //                               ?
            for (int i = 0; i < count; i++) {
                result.add(dataObj);
            }
            
            //              ?
            outputValues.put(OUTPUT_RESULT_ID, result);
            outputValues.put(OUTPUT_LENGTH_ID, result.size());
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public int getDefaultCount() {
        return defaultCount;
    }
    
    public void setDefaultCount(int count) {
        int resolved = GenerationLimits.clampNonNegativeCount(count);
        if (this.defaultCount != resolved) {
            this.defaultCount = resolved;
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("defaultCount", getDefaultCount());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("defaultCount")) {
                Object count = stateMap.get("defaultCount");
                if (count instanceof Number) {
                    setDefaultCount(((Number) count).intValue());
                }
            }
        }
    }
} 
