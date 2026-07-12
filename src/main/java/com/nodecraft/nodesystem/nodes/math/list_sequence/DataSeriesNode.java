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
 *                               ?
 */
@NodeInfo(
    id = "math.sequence.series",
    displayName = "Data Series",
    description = "Generates a series of numbers with constant increment",
    category = "math.sequence"
)
public class DataSeriesNode extends BaseNode {
    
    // ---       ?---
    private boolean useIntegerType = true; //                          ?
    private int defaultCount = 10; //              ?
    private double defaultStart = 0; //        ?
    private double defaultStep = 1; //       
    
    // ---    /      ID ---
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_SERIES_ID = "output_series";
    private static final String OUTPUT_SUM_ID = "output_sum";
    
    /**
     *                   ?
     */
    public DataSeriesNode() {
        //           - data.sequence.series
        super(UUID.randomUUID(), "math.sequence.series");
        
        //          
        IPort startInput = new BasePort(INPUT_START_ID, "Start", 
                "Starting value of the series", NodeDataType.DOUBLE, this);
        addInputPort(startInput);
        
        IPort stepInput = new BasePort(INPUT_STEP_ID, "Step", 
                "Increment between consecutive elements", NodeDataType.DOUBLE, this);
        addInputPort(stepInput);
        
        IPort countInput = new BasePort(INPUT_COUNT_ID, "Count", 
                "Number of elements to generate", NodeDataType.INTEGER, this);
        addInputPort(countInput);
        
        //          
        IPort seriesOutput = new BasePort(OUTPUT_SERIES_ID, "Series", 
                "The generated sequence", NodeDataType.LIST, this);
        addOutputPort(seriesOutput);
        
        IPort sumOutput = new BasePort(OUTPUT_SUM_ID, "Sum", 
                "Sum of all values in the series", NodeDataType.DOUBLE, this);
        addOutputPort(sumOutput);
    }
    
    /**
     *            
     * @param context        ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        //       
        Object startObj = inputValues.get(INPUT_START_ID);
        Object stepObj = inputValues.get(INPUT_STEP_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);
        
        //                
        double start = defaultStart;
        if (startObj instanceof Number) {
            start = ((Number) startObj).doubleValue();
        }
        
        double step = defaultStep;
        if (stepObj instanceof Number) {
            step = ((Number) stepObj).doubleValue();
        }
        
        int count = defaultCount;
        if (countObj instanceof Number) {
            count = ((Number) countObj).intValue();
        }
        count = GenerationLimits.clampNonNegativeCount(count);

        if (!Double.isFinite(start) || !Double.isFinite(step)) {
            outputValues.put(OUTPUT_SERIES_ID, new ArrayList<>());
            outputValues.put(OUTPUT_SUM_ID, useIntegerType ? 0 : 0.0);
            return;
        }
        
        //       
        List<Object> series = new ArrayList<>();
        double sum = 0;
        
        for (int i = 0; i < count; i++) {
            double value = start + i * step;
            Object element;
            
            //                         
            if (useIntegerType) {
                element = (int) Math.round(value);
                sum += (int) Math.round(value);
            } else {
                element = value;
                sum += value;
            }
            
            series.add(element);
        }
        
        //       
        outputValues.put(OUTPUT_SERIES_ID, series);
        outputValues.put(OUTPUT_SUM_ID, useIntegerType ? (int) Math.round(sum) : sum);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseIntegerType() {
        return useIntegerType;
    }
    
    public void setUseIntegerType(boolean useInt) {
        if (this.useIntegerType != useInt) {
            this.useIntegerType = useInt;
            markDirty();
        }
    }
    
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
    
    public double getDefaultStart() {
        return defaultStart;
    }
    
    public void setDefaultStart(double start) {
        if (Double.compare(this.defaultStart, start) != 0) {
            this.defaultStart = start;
            markDirty();
        }
    }
    
    public double getDefaultStep() {
        return defaultStep;
    }
    
    public void setDefaultStep(double step) {
        if (Double.compare(this.defaultStep, step) != 0) {
            this.defaultStep = step;
            markDirty();
        }
    }
    
    // ---             ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useIntegerType", isUseIntegerType());
        state.put("defaultCount", getDefaultCount());
        state.put("defaultStart", getDefaultStart());
        state.put("defaultStep", getDefaultStep());
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
            
            if (stateMap.containsKey("defaultCount")) {
                Object count = stateMap.get("defaultCount");
                if (count instanceof Number) {
                    setDefaultCount(((Number) count).intValue());
                }
            }
            
            if (stateMap.containsKey("defaultStart")) {
                Object start = stateMap.get("defaultStart");
                if (start instanceof Number) {
                    setDefaultStart(((Number) start).doubleValue());
                }
            }
            
            if (stateMap.containsKey("defaultStep")) {
                Object step = stateMap.get("defaultStep");
                if (step instanceof Number) {
                    setDefaultStep(((Number) step).doubleValue());
                }
            }
        }
    }
} 
