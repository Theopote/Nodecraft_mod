package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import com.nodecraft.nodesystem.api.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 *                                                                                   ?
 */
@NodeInfo(
    id = "math.list.combine_lists",
    displayName = "Combine Lists",
    description = "Combines multiple lists into a single list by index.",
    category = "math.list"
)
public class CombineListsNode extends BaseNode {
    
    // ---              ?---
    private int inputCount = 2; //                                   ?
    private boolean skipIncomplete = false; //                                                           ?
    private boolean outputAsTuples = true; //                                                               
    
    // ---               D ---
    private static final String OUTPUT_LIST_ID = "output_list";
    
    /**
     *                                         ?
     */
    public CombineListsNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.combine_lists");
        
        //                            ?
        rebuildInputPorts();
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Combined List", 
                "The resulting combined list", NodeDataType.LIST, this);
        addOutputPort(listOutput);
    }
    
    /**
     *                                                        ?
     */
    private void rebuildInputPorts() {
        //                                      ?
        inputPorts.clear();
        
        //                            
        for (int i = 0; i < inputCount; i++) {
            String portId = "input_list_" + i;
            IPort inputPort = new BasePort(portId, "List " + (i + 1), 
                    "Input list " + (i + 1), NodeDataType.LIST, this);
            addInputPort(inputPort);
        }
    }
    
    /**
     *                         ?
     * @param context                ?
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> resultList = new ArrayList<>();
        List<List<?>> inputLists = new ArrayList<>();
        int maxLength = 0;
        
        //                            ?
        for (int i = 0; i < inputCount; i++) {
            String portId = "input_list_" + i;
            Object listObj = inputValues.get(portId);
            
            if (listObj instanceof List) {
                List<?> list = (List<?>) listObj;
                inputLists.add(list);
                maxLength = Math.max(maxLength, list.size());
            } else {
                //                                                        ?
                inputLists.add(new ArrayList<>());
            }
        }
        
        //                       ?
        for (int i = 0; i < maxLength; i++) {
            List<Object> combinedRow = new ArrayList<>();
            boolean rowComplete = true;
            
            //                                                       ?
            for (List<?> list : inputLists) {
                if (i < list.size()) {
                    combinedRow.add(list.get(i));
                } else {
                    combinedRow.add(null);
                    rowComplete = false;
                }
            }
            
            //         ipIncomplete                                          ?
            if (rowComplete || !skipIncomplete) {
                if (outputAsTuples) {
                    //                                   ?
                    resultList.add(combinedRow);
                } else {
                    //                       ?
                    resultList.addAll(combinedRow);
                }
            }
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    /**
     *                           ?
     */
    public void increaseInputCount() {
        if (inputCount < 10) { //                                 ?
            inputCount++;
            rebuildInputPorts();
            markDirty();
        }
    }
    
    /**
     *                            
     */
    public void decreaseInputCount() {
        if (inputCount > 2) { //              ?         ?
            inputCount--;
            rebuildInputPorts();
            markDirty();
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public int getInputCount() {
        return inputCount;
    }
    
    public void setInputCount(int count) {
        if (count >= 2 && count <= 10 && count != inputCount) {
            inputCount = count;
            rebuildInputPorts();
            markDirty();
        }
    }
    
    public boolean isSkipIncomplete() {
        return skipIncomplete;
    }
    
    public void setSkipIncomplete(boolean skip) {
        if (this.skipIncomplete != skip) {
            this.skipIncomplete = skip;
            markDirty();
        }
    }
    
    public boolean isOutputAsTuples() {
        return outputAsTuples;
    }
    
    public void setOutputAsTuples(boolean asTuples) {
        if (this.outputAsTuples != asTuples) {
            this.outputAsTuples = asTuples;
            markDirty();
        }
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("inputCount", getInputCount());
        state.put("skipIncomplete", isSkipIncomplete());
        state.put("outputAsTuples", isOutputAsTuples());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("skipIncomplete")) {
                Object skip = stateMap.get("skipIncomplete");
                if (skip instanceof Boolean) {
                    setSkipIncomplete((Boolean) skip);
                }
            }
            
            if (stateMap.containsKey("outputAsTuples")) {
                Object tuples = stateMap.get("outputAsTuples");
                if (tuples instanceof Boolean) {
                    setOutputAsTuples((Boolean) tuples);
                }
            }
            
            //                                                                         ?
            if (stateMap.containsKey("inputCount")) {
                Object count = stateMap.get("inputCount");
                if (count instanceof Number) {
                    setInputCount(((Number) count).intValue());
                }
            }
        }
    }
} 
