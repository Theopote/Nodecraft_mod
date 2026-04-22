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
 *                                                                        ?
 */
@NodeInfo(
    id = "math.list.range_legacy",
    displayName = "Sequence Range (Legacy)",
    description = "Deprecated legacy range node. Use Range (math.sequence.range) instead.",
    category = "math.list",
    order = 999
)
@Deprecated
public class SequenceRangeNode extends BaseNode {
    
    // ---              ?---
    private boolean useIntegerType = true; //                                                          ?
    private boolean includeEnd = false; //                        ?
    private String description; //                    ?
    
    // ---       ?              D ---
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String OUTPUT_RANGE_ID = "output_range";
    private static final String OUTPUT_COUNT_ID = "output_count";
    
    /**
     *                                          ?
     */
    public SequenceRangeNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.range_legacy");
        
        //                    ?
        this.description = "Generates a sequence of numbers within a specified range";
        
        //                    ?
        IPort startInput = new BasePort(INPUT_START_ID, "Start", 
                "Starting value (inclusive)", NodeDataType.DOUBLE, this);
        addInputPort(startInput);
        
        IPort endInput = new BasePort(INPUT_END_ID, "End", 
                "Ending value (exclusive by default)", NodeDataType.DOUBLE, this);
        addInputPort(endInput);
        
        IPort stepInput = new BasePort(INPUT_STEP_ID, "Step", 
                "Increment between values", NodeDataType.DOUBLE, this);
        addInputPort(stepInput);
        
        //                    ?
        IPort rangeOutput = new BasePort(OUTPUT_RANGE_ID, "Range", 
                "The generated number sequence", NodeDataType.LIST, this);
        addOutputPort(rangeOutput);
        
        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Count", 
                "The number of elements in the range", NodeDataType.INTEGER, this);
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
        Object startObj = inputValues.get(INPUT_START_ID);
        Object endObj = inputValues.get(INPUT_END_ID);
        Object stepObj = inputValues.get(INPUT_STEP_ID);
        
        //                                  ?
        double start = 0;
        if (startObj instanceof Number) {
            start = ((Number) startObj).doubleValue();
        }
        
        double end = 10;
        if (endObj instanceof Number) {
            end = ((Number) endObj).doubleValue();
        }
        
        double step = 1;
        if (stepObj instanceof Number) {
            step = ((Number) stepObj).doubleValue();
            if (step == 0) {
                step = 1; //                ?                    ?
            }
        }
        
        //              ?
        List<Object> range = new ArrayList<>();
        
        //                             ?
        boolean ascending = step > 0;
        
        //                      
        double limitValue = end;
        if (includeEnd) {
            //                                                                     ?
            if (ascending) {
                limitValue = end + step * 0.5; //               nd                  nd
            } else {
                limitValue = end - step * 0.5; //               nd                  nd
            }
        }
        
        //              ?
        if (ascending) {
            for (double value = start; value < limitValue; value += step) {
                addValueToRange(range, value);
            }
        } else {
            for (double value = start; value > limitValue; value += step) {
                addValueToRange(range, value);
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_RANGE_ID, range);
        outputValues.put(OUTPUT_COUNT_ID, range.size());
    }
    
    /**
     *                                                                   ?
     */
    private void addValueToRange(List<Object> range, double value) {
        if (useIntegerType) {
            range.add((int) Math.round(value));
        } else {
            range.add(value);
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseIntegerType() {
        return useIntegerType;
    }
    
    public void setUseIntegerType(boolean useInt) {
        this.useIntegerType = useInt;
        markDirty();
    }
    
    public boolean isIncludeEnd() {
        return includeEnd;
    }
    
    public void setIncludeEnd(boolean include) {
        this.includeEnd = include;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useIntegerType", isUseIntegerType());
        state.put("includeEnd", isIncludeEnd());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useIntegerType")) {
                Object useInt = stateMap.get("useIntegerType");
                if (useInt instanceof Boolean) {
                    setUseIntegerType((Boolean) useInt);
                }
            }
            
            if (stateMap.containsKey("includeEnd")) {
                Object include = stateMap.get("includeEnd");
                if (include instanceof Boolean) {
                    setIncludeEnd((Boolean) include);
                }
            }
        }
    }
} 