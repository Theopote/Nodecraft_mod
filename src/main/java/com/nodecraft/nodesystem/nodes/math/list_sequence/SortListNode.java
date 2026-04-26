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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 *                                                         
 */
@NodeInfo(
    id = "math.list.sort_list",
    displayName = "Sort List",
    description = "Sorts elements of a list",
    category = "math.list"
)
public class SortListNode extends BaseNode {
    
    // ---              ?---
    private boolean descending = false; //                     ?
    private SortType sortType = SortType.AUTO; //              ?
    private String description = "Sorts elements of a list"; //              ?
    
    // ---                       ---
    public enum SortType {
        AUTO("Auto"), //                                
        NUMERIC("Numeric"), //               ?
        TEXT("Text"), //              ?
        LENGTH("Length"); //                                                             ?
        
        private final String label;
        
        SortType(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    // ---       ?              D ---
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_LIST_ID = "output_list";
    
    /**
     *                                         ?
     */
    public SortListNode() {
        //                    ?- data.lists.sort_list
        super(UUID.randomUUID(), "math.list.sort_list");
        
        //                    ?
        IPort listInput = new BasePort(INPUT_LIST_ID, "List", 
                "The list to sort", NodeDataType.LIST, this);
        addInputPort(listInput);
        
        //                    ?
        IPort listOutput = new BasePort(OUTPUT_LIST_ID, "Sorted List", 
                "The sorted list", NodeDataType.LIST, this);
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
        
        List<Object> resultList = new ArrayList<>();
        
        //              ?
        if (inputObj instanceof List) {
            List<?> inputList = (List<?>) inputObj;
            
            //                                                            ?
            resultList.addAll(inputList);
            
            //                                      ?
            if (resultList.isEmpty()) {
                outputValues.put(OUTPUT_LIST_ID, resultList);
                return;
            }
            
            //             ?
            sort(resultList);
        }
        
        //              ?
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }
    
    /**
     *                       ?
     * @param list                    ?
     */
    @SuppressWarnings("unchecked")
    private void sort(List<Object> list) {
        //                                           ?
        Object firstNonNull = null;
        for (Object item : list) {
            if (item != null) {
                firstNonNull = item;
                break;
            }
        }
        
        //                                                    
        if (firstNonNull == null) {
            return;
        }
        
        //                                     ?
        Comparator<Object> comparator = null;
        
        switch (sortType) {
            case NUMERIC:
                comparator = createNumericComparator();
                break;
                
            case TEXT:
                comparator = createTextComparator();
                break;
                
            case LENGTH:
                comparator = createLengthComparator();
                break;
                
            case AUTO:
            default:
                //                    ?
                if (firstNonNull instanceof Number) {
                    comparator = createNumericComparator();
                } else if (firstNonNull instanceof String) {
                    comparator = createTextComparator();
                } else if (firstNonNull instanceof List || firstNonNull instanceof String) {
                    comparator = createLengthComparator();
                } else {
                    //                                                      Comparable         ?
                    try {
                        //                                                       ?
                        comparator = new Comparator<Object>() {
                            @SuppressWarnings("unchecked")
                            @Override
                            public int compare(Object o1, Object o2) {
                                //              ?
                                if (o1 == null && o2 == null) return 0;
                                if (o1 == null) return descending ? 1 : -1;
                                if (o2 == null) return descending ? -1 : 1;
                                
                                //                           mparable      ?
                                if (o1 instanceof Comparable && o2 instanceof Comparable) {
                                    int result = ((Comparable<Object>) o1).compareTo(o2);
                                    return descending ? -result : result;
                                } else {
                                    //               Comparable                            ?
                                    int result = o1.toString().compareTo(o2.toString());
                                    return descending ? -result : result;
                                }
                            }
                        };
                    } catch (Exception e) {
                        //                                    String                   ?
                        comparator = createTextComparator();
                    }
                }
                break;
        }
        
        //              ?
        if (comparator != null) {
            Collections.sort(list, comparator);
        }
    }
    
    /**
     *                         ?
     */
    private Comparator<Object> createNumericComparator() {
        return new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                //              ?
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return descending ? 1 : -1;
                if (o2 == null) return descending ? -1 : 1;
                
                //                                  ?
                double v1 = toDouble(o1);
                double v2 = toDouble(o2);
                
                //       ?
                int result = Double.compare(v1, v2);
                return descending ? -result : result;
            }
            
            //                     ouble
            private double toDouble(Object obj) {
                if (obj instanceof Number) {
                    return ((Number) obj).doubleValue();
                } else {
                    try {
                        return Double.parseDouble(obj.toString());
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            }
        };
    }
    
    /**
     *                       ?
     */
    private Comparator<Object> createTextComparator() {
        return new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                //              ?
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return descending ? 1 : -1;
                if (o2 == null) return descending ? -1 : 1;
                
                //                         ?
                int result = o1.toString().compareTo(o2.toString());
                return descending ? -result : result;
            }
        };
    }
    
    /**
     *                       ?
     */
    private Comparator<Object> createLengthComparator() {
        return new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                //              ?
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return descending ? 1 : -1;
                if (o2 == null) return descending ? -1 : 1;
                
                //              ?
                int len1 = getLength(o1);
                int len2 = getLength(o2);
                
                //       ?
                int result = Integer.compare(len1, len2);
                return descending ? -result : result;
            }
            
            //                 ?       ?
            private int getLength(Object obj) {
                if (obj instanceof String) {
                    return ((String) obj).length();
                } else if (obj instanceof List) {
                    return ((List<?>) obj).size();
                } else if (obj instanceof Object[]) {
                    return ((Object[]) obj).length;
                } else {
                    return obj.toString().length();
                }
            }
        };
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isDescending() {
        return descending;
    }
    
    public void setDescending(boolean descending) {
        this.descending = descending;
        markDirty();
    }
    
    public SortType getSortType() {
        return sortType;
    }
    
    public void setSortType(SortType sortType) {
        this.sortType = sortType;
        markDirty();
    }
    
    // ---                         ?---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("descending", isDescending());
        state.put("sortType", getSortType().name());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("descending")) {
                Object desc = stateMap.get("descending");
                if (desc instanceof Boolean) {
                    setDescending((Boolean) desc);
                }
            }
            
            if (stateMap.containsKey("sortType")) {
                Object type = stateMap.get("sortType");
                if (type instanceof String) {
                    try {
                        setSortType(SortType.valueOf((String) type));
                    } catch (IllegalArgumentException e) {
                        //                                            ?
                        setSortType(SortType.AUTO);
                    }
                }
            }
        }
    }
} 
