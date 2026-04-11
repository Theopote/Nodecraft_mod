package com.nodecraft.nodesystem.nodes.utilities.legacy.spatial.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Random;
import java.util.UUID;

/**
 * Applies randomized offsets to each coordinate in an input list.
 */
@NodeInfo(
    id = "spatial.points.randomize_coordinates",
    displayName = "Randomize Coordinates",
    description = "Applies random offsets to coordinates using uniform range or vector range.",
    category = "utilities.legacy.spatial.points"
)
public class RandomizeCoordinatesNode extends BaseNode {

    private boolean useUniformRange = true; //                   
    private boolean useSeed = false;
    private long seed = 0;

    // ---           IDs ---
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_MIN_RANGE_ID = "input_min_range";
    private static final String INPUT_MAX_RANGE_ID = "input_max_range";
    private static final String INPUT_RANGE_VECTOR_ID = "input_range_vector";
    private static final String INPUT_SEED_ID = "input_seed";

    // ---           IDs ---
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";

    public RandomizeCoordinatesNode() {
        super(UUID.randomUUID(), "spatial.points.randomize_coordinates");
        
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", 
                "The coordinates to randomize", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_MIN_RANGE_ID, "Min Range", 
                "Minimum random offset (uniform)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_RANGE_ID, "Max Range", 
                "Maximum random offset (uniform)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RANGE_VECTOR_ID, "Range Vector", 
                "Maximum random offset vector (XYZ)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", 
                "Random seed (optional)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", 
                "Randomized coordinates", NodeDataType.BLOCK_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Applies random offset to a list of coordinates within a given range";
    }

    @Override
    public String getDisplayName() {
        return "Randomize Coordinates";
    }

    // ---           ---
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object minRangeObj = inputValues.get(INPUT_MIN_RANGE_ID);
        Object maxRangeObj = inputValues.get(INPUT_MAX_RANGE_ID);
        Object rangeVectorObj = inputValues.get(INPUT_RANGE_VECTOR_ID);
        Object seedObj = inputValues.get(INPUT_SEED_ID);
        
        BlockPosList result = new BlockPosList();
        
        if (!(coordinatesObj instanceof BlockPosList)) {
            outputValues.put(OUTPUT_COORDINATES_ID, result);
            return;
        }
        
        BlockPosList coordinates = (BlockPosList) coordinatesObj;
        
        double minRange = 0.0;
        double maxRange = 1.0;
        Vector3d rangeVector = new Vector3d(1, 1, 1);
        
        long randomSeed = this.seed;
        if (useSeed && seedObj instanceof Number) {
            randomSeed = ((Number) seedObj).longValue();
        } else if (!useSeed && seedObj instanceof Number) {
            //                                                   
            randomSeed = ((Number) seedObj).longValue();
            useSeed = true;
        }
        
        //                   
        Random random;
        if (useSeed) {
            random = new Random(randomSeed);
        } else {
            random = new Random();
        }
        
        if (useUniformRange) {
            if (minRangeObj instanceof Number) {
                minRange = ((Number) minRangeObj).doubleValue();
            }
            if (maxRangeObj instanceof Number) {
                maxRange = ((Number) maxRangeObj).doubleValue();
            }
            if (minRange > maxRange) {
                double temp = minRange;
                minRange = maxRange;
                maxRange = temp;
            }
        } else if (rangeVectorObj instanceof Vector3d) {
            rangeVector = (Vector3d) rangeVectorObj;
            rangeVector.x = Math.abs(rangeVector.x);
            rangeVector.y = Math.abs(rangeVector.y);
            rangeVector.z = Math.abs(rangeVector.z);
        }
        
        for (BlockPos pos : coordinates) {
            //              
            int offsetX, offsetY, offsetZ;
            
            if (useUniformRange) {
                double range = maxRange - minRange;
                offsetX = (int) Math.round(minRange + random.nextDouble() * range);
                if (random.nextBoolean()) offsetX = -offsetX;
                
                offsetY = (int) Math.round(minRange + random.nextDouble() * range);
                if (random.nextBoolean()) offsetY = -offsetY;
                
                offsetZ = (int) Math.round(minRange + random.nextDouble() * range);
                if (random.nextBoolean()) offsetZ = -offsetZ;
            } else {
                offsetX = (int) Math.round((random.nextDouble() * 2 - 1) * rangeVector.x);
                offsetY = (int) Math.round((random.nextDouble() * 2 - 1) * rangeVector.y);
                offsetZ = (int) Math.round((random.nextDouble() * 2 - 1) * rangeVector.z);
            }
            
            BlockPos randomizedPos = new BlockPos(
                pos.getX() + offsetX,
                pos.getY() + offsetY,
                pos.getZ() + offsetZ
            );
            
            result.add(randomizedPos);
        }
        
        outputValues.put(OUTPUT_COORDINATES_ID, result);
    }
    
    // --- Getters/Setters for Properties ---
    
    public boolean isUseUniformRange() {
        return useUniformRange;
    }
    
    public void setUseUniformRange(boolean useUniformRange) {
        this.useUniformRange = useUniformRange;
        markDirty();
    }
    
    public boolean isUseSeed() {
        return useSeed;
    }
    
    public void setUseSeed(boolean useSeed) {
        this.useSeed = useSeed;
        markDirty();
    }
    
    public long getSeed() {
        return seed;
    }
    
    public void setSeed(long seed) {
        this.seed = seed;
        markDirty();
    }
    
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("useUniformRange", useUniformRange);
        state.put("useSeed", useSeed);
        state.put("seed", seed);
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("useUniformRange")) {
                Object useUniformObj = stateMap.get("useUniformRange");
                if (useUniformObj instanceof Boolean) {
                    setUseUniformRange((Boolean) useUniformObj);
                }
            }
            
            if (stateMap.containsKey("useSeed")) {
                Object useSeedObj = stateMap.get("useSeed");
                if (useSeedObj instanceof Boolean) {
                    setUseSeed((Boolean) useSeedObj);
                }
            }
            
            if (stateMap.containsKey("seed")) {
                Object seedObj = stateMap.get("seed");
                if (seedObj instanceof Number) {
                    setSeed(((Number) seedObj).longValue());
                }
            }
        }
    }
} 
