package com.nodecraft.nodesystem.nodes.utilities.legacy.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Connects a sequence of points into a polyline path of blocks.
 */
@NodeInfo(
    id = "spatial.generators.polyline_blocks",
    displayName = "Polyline Generator",
    description = "Generates blocks along connected line segments between input points.",
    category = "utilities.legacy.spatial.generators"
)
public class PolylineBlocksNode extends BaseNode {

    private boolean useBresenham = true;
    private boolean closedLoop = false;

    // ---           IDs ---
    private static final String INPUT_POINTS_LIST_ID = "input_points_list";

    // ---           IDs ---
    private static final String OUTPUT_POLYLINE_BLOCKS_ID = "output_polyline_blocks";

    public PolylineBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.polyline_blocks");
        
        addInputPort(new BasePort(INPUT_POINTS_LIST_ID, "Points", 
                "The list of points to connect", NodeDataType.BLOCK_LIST, this));

        addOutputPort(new BasePort(OUTPUT_POLYLINE_BLOCKS_ID, "Polyline Blocks", 
                "The blocks along the polyline path", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Generates a path of blocks connecting multiple points";
    }

    @Override
    public String getDisplayName() {
        return "Polyline (Blocks)";
    }

    // ---           ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_LIST_ID);
        
        BlockPosList result = new BlockPosList();
        
        if (pointsObj instanceof BlockPosList) {
            BlockPosList points = (BlockPosList) pointsObj;
            
            //                   
            if (points.size() >= 2) {
                List<BlockPos> pointsList = points.getPositions();
                
                for (int i = 0; i < pointsList.size() - 1; i++) {
                    BlockPos start = pointsList.get(i);
                    BlockPos end = pointsList.get(i + 1);
                    
                    generateLineSegment(start, end, result);
                }
                
                //                                                
                if (closedLoop && pointsList.size() > 2) {
                    BlockPos start = pointsList.get(pointsList.size() - 1);
                    BlockPos end = pointsList.get(0);
                    
                    generateLineSegment(start, end, result);
                }
            }
        }
        
        outputValues.put(OUTPUT_POLYLINE_BLOCKS_ID, result);
    }
    
    /**
     *                   
     */
    private void generateLineSegment(BlockPos start, BlockPos end, BlockPosList result) {
        if (useBresenham) {
            generateBresenhamLine(start, end, result);
        } else {
            generateParametricLine(start, end, result);
        }
    }
    
    /**
     */
    private void generateBresenhamLine(BlockPos start, BlockPos end, BlockPosList result) {
        // Bresenham's 3D         
        int x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        int x2 = end.getX(), y2 = end.getY(), z2 = end.getZ();
        
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);
        
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        
        int dm = Math.max(Math.max(dx, dy), dz);
        if (dm == 0) {
            //                        
            result.add(new BlockPos(x1, y1, z1));
            return;
        }
        
        int x = x1, y = y1, z = z1;
        
        //          
        result.add(new BlockPos(x, y, z));
        
        for (int i = 0; i < dm; i++) {
            int err1 = (i + 1) * dx - dm;
            int err2 = (i + 1) * dy - dm;
            int err3 = (i + 1) * dz - dm;
            
            if (err1 > 0) x += sx;
            if (err2 > 0) y += sy;
            if (err3 > 0) z += sz;
            
            result.add(new BlockPos(x, y, z));
        }
    }
    
    /**
     */
    private void generateParametricLine(BlockPos start, BlockPos end, BlockPosList result) {
        Vector3d startVec = new Vector3d(start.getX(), start.getY(), start.getZ());
        Vector3d endVec = new Vector3d(end.getX(), end.getY(), end.getZ());
        Vector3d dirVec = new Vector3d(endVec).sub(startVec);
        
        int distance = Math.abs(end.getX() - start.getX()) + 
                       Math.abs(end.getY() - start.getY()) + 
                       Math.abs(end.getZ() - start.getZ());
        distance = Math.max(distance, 1);
        
        //                   
        for (int i = 0; i <= distance; i++) {
            double t = (double) i / distance;
            Vector3d pos = new Vector3d(startVec).add(new Vector3d(dirVec).mul(t));
            
            //                                              
            BlockPos blockPos = new BlockPos(
                (int) Math.round(pos.x),
                (int) Math.round(pos.y),
                (int) Math.round(pos.z)
            );
            
            if (i == 0 || !blockPos.equals(result.getPositions().get(result.size() - 1))) {
                result.add(blockPos);
            }
        }
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseBresenham() {
        return useBresenham;
    }
    
    public void setUseBresenham(boolean useBresenham) {
        this.useBresenham = useBresenham;
        markDirty();
    }
    
    public boolean isClosedLoop() {
        return closedLoop;
    }
    
    public void setClosedLoop(boolean closedLoop) {
        this.closedLoop = closedLoop;
        markDirty();
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useBresenham", useBresenham);
        state.put("closedLoop", closedLoop);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useBresenham")) {
                Object useBresObj = stateMap.get("useBresenham");
                if (useBresObj instanceof Boolean) {
                    setUseBresenham((Boolean) useBresObj);
                }
            }
            
            if (stateMap.containsKey("closedLoop")) {
                Object closedLoopObj = stateMap.get("closedLoop");
                if (closedLoopObj instanceof Boolean) {
                    setClosedLoop((Boolean) closedLoopObj);
                }
            }
        }
    }
} 
