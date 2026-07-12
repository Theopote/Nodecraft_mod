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

@NodeInfo(
    id = "math.list.flatten_list",
    displayName = "Flatten List",
    description = "Flattens a nested list structure into a single-level list",
    category = "math.list"
)
public class FlattenListNode extends BaseNode {
    
    private int maxDepth = -1;
    private boolean preserveTypes = false; //                                         
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String OUTPUT_LIST_ID = "output_list";
    
    public FlattenListNode() {
        super(UUID.randomUUID(), "math.list.flatten_list");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The nested list to flatten", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort depthInput = new BasePort(INPUT_DEPTH_ID, "Depth", 
                "Maximum flattening depth (optional)", NodeDataType.INTEGER, this);
        addInputPort(depthInput);
        
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Flattened List", 
                "The resulting flattened list", NodeDataType.LIST, this);
        addOutputPort(listOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object depthObj = inputValues.get(INPUT_DEPTH_ID);
        
        int depth = maxDepth;
        if (depthObj instanceof Number) {
            depth = ((Number) depthObj).intValue();
        }
        
        List<Object> resultList = new ArrayList<>();
        
        if (listObj instanceof List) {
            flatten((List<?>) listObj, resultList, 0, depth);
        } else if (listObj != null) {
            resultList.add(listObj);
        }
        
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    /**
     *                    ?
     * @param input              ?
     * @param output              ?
     * @param currentDepth              
     * @param maxDepth             ?
     */
    private void flatten(List<?> input, List<Object> output, int currentDepth, int maxDepth) {
        if (maxDepth >= 0 && currentDepth >= maxDepth) {
            output.addAll(input);
            return;
        }
        
        for (Object item : input) {
            if (item instanceof List) {
                flatten((List<?>) item, output, currentDepth + 1, maxDepth);
            } else if (preserveTypes || item == null) {
                output.add(item);
            } else {
                try {
                    Object[] array = (Object[]) item;
                    for (Object arrayItem : array) {
                        output.add(arrayItem);
                    }
                } catch (ClassCastException e) {
                    output.add(item);
                }
            }
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public int getMaxDepth() {
        return maxDepth;
    }
    
    public void setMaxDepth(int depth) {
        if (this.maxDepth != depth) {
            this.maxDepth = depth;
            markDirty();
        }
    }
    
    public boolean isPreserveTypes() {
        return preserveTypes;
    }
    
    public void setPreserveTypes(boolean preserve) {
        if (this.preserveTypes != preserve) {
            this.preserveTypes = preserve;
            markDirty();
        }
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("maxDepth", getMaxDepth());
        state.put("preserveTypes", isPreserveTypes());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("maxDepth")) {
                Object depth = stateMap.get("maxDepth");
                if (depth instanceof Number) {
                    setMaxDepth(((Number) depth).intValue());
                }
            }
            
            if (stateMap.containsKey("preserveTypes")) {
                Object preserve = stateMap.get("preserveTypes");
                if (preserve instanceof Boolean) {
                    setPreserveTypes((Boolean) preserve);
                }
            }
        }
    }
} 
