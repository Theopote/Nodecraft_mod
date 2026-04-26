package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 *                                                  ?
 */
@NodeInfo(
    id = "math.list.shuffle_list",
    displayName = "Shuffle List",
    description = "Randomly reorders elements in a list",
    category = "math.list"
)
public class ShuffleListNode extends BaseNode {
    
    // ---              ?---
    private long seed = 0; //                      ?                           
    private boolean preserveInput = false; //                            ?
    private String description; //                    ?
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_LIST_ID = "output_list";
    
    /**
     *                                         ?
     */
    public ShuffleListNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.shuffle_list");
        
        //                    ?
        this.description = "Randomly reorders elements in a list";
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to shuffle", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort seedInput = new BasePort(INPUT_SEED_ID, "Seed", 
                "Optional random seed (integer)", NodeDataType.INTEGER, this);
        addInputPort(seedInput);
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Shuffled List", 
                "The list with elements in random order", NodeDataType.LIST, this);
        addOutputPort(listOutput);
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
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object seedObj = inputValues.get(INPUT_SEED_ID);
        
        List<Object> resultList = new ArrayList<>();
        
        //              ?
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            
            //                                                            ?
            resultList.addAll(inputList);
            
            //                       ?
            long actualSeed = seed;
            if (seedObj instanceof Number) {
                actualSeed = ((Number) seedObj).longValue();
            }
            
            //              ?
            if (!resultList.isEmpty()) {
                if (actualSeed != 0) {
                    //                    ?
                    Collections.shuffle(resultList, new Random(actualSeed));
                } else {
                    //                                   ?
                    Collections.shuffle(resultList);
                }
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    // --- Getters/Setters for Properties ---
    
    public long getSeed() {
        return seed;
    }
    
    public void setSeed(long seed) {
        this.seed = seed;
        markDirty();
    }
    
    public boolean isPreserveInput() {
        return preserveInput;
    }
    
    public void setPreserveInput(boolean preserve) {
        this.preserveInput = preserve;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("seed", getSeed());
        state.put("preserveInput", isPreserveInput());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("seed")) {
                Object seedObj = stateMap.get("seed");
                if (seedObj instanceof Number) {
                    setSeed(((Number) seedObj).longValue());
                }
            }
            
            if (stateMap.containsKey("preserveInput")) {
                Object preserve = stateMap.get("preserveInput");
                if (preserve instanceof Boolean) {
                    setPreserveInput((Boolean) preserve);
                }
            }
        }
    }
} 
