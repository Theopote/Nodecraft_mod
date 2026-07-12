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
import java.util.UUID;

@NodeInfo(
    id = "math.list.reverse_list",
    displayName = "Reverse List",
    description = "Reverses the order of elements in a list.",
    category = "math.list"
)
public class ReverseListNode extends BaseNode {
    
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_LIST_ID = "output_list";
    
    public ReverseListNode() {
        super(UUID.randomUUID(), "math.list.reverse_list");
        
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to reverse", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Reversed List", 
                "The list with elements in reverse order", NodeDataType.LIST, this);
        addOutputPort(listOutput);
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        
        List<Object> resultList = new ArrayList<>();
        
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            
            resultList.addAll(inputList);
            
            Collections.reverse(resultList);
        }
        
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
} 
