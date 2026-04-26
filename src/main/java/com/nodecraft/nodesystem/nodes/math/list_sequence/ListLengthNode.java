package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 *                                                                     ?
 */
@NodeInfo(
    id = "math.list.list_length",
    displayName = "List Length",
    description = "Returns the number of items in a list.",
    category = "math.list"
)
public class ListLengthNode extends BaseNode {
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private String description; //                    ?
    
    /**
     *                                         ?
     */
    public ListLengthNode() {
        //                                        UID.randomUUID()              D
        super(UUID.randomUUID(), "math.list.list_length");
        
        //                    ?
        this.description = "Returns the number of items in a list";
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to get the length of", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        //                    ?
        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length", 
                "The number of items in the list", NodeDataType.INTEGER, this);
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
        //                    ?
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        int length = 0;
        
        //                      
        if (inputObj instanceof List) {
            List<?> list = (List<?>) inputObj;
            length = list.size();
        }
        
        //              ?
        outputValues.put(OUTPUT_LENGTH_ID, length);
    }
    
    //                                                       ?
    @Override
    public Object getNodeState() {
        return null;
    }
    
    @Override
    public void setNodeState(Object state) {
        //                            ?
    }
} 
