package com.nodecraft.nodesystem.nodes.flora.output;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlantStructure;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Scatter Plants 节点: 在指定区域内散布多个植物实例
 */
@NodeInfo(
    id = "flora.output.scatter_plants",
    displayName = "Scatter Plants",
    description = "Scatters plants within a specified area",
    category = "flora.output"
)
public class ScatterPlantsNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScatterPlantsNode.class);
    
    // --- 节点属性 ---
    private int plantCount = 10;                  // 植物数量
    private float scatterRadius = 20.0f;          // 散布半径
    private float minDistance = 3.0f;             // 植物间最小距离
    private boolean avoidOverlap = true;          // 是否避免重叠
    private int randomSeed = 12345;               // 随机种子
    private String description = "在指定区域内散布多个植物实例";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_PLANT_STRUCTURE_ID = "input_plant_structure";
    private static final String INPUT_CENTER_POSITION_ID = "input_center_position";
    private static final String INPUT_PLANT_COUNT_ID = "input_plant_count";
    private static final String INPUT_SCATTER_RADIUS_ID = "input_scatter_radius";
    private static final String INPUT_MIN_DISTANCE_ID = "input_min_distance";
    private static final String INPUT_AVOID_OVERLAP_ID = "input_avoid_overlap";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_LIST_ID = "output_plant_structure_list";
    private static final String OUTPUT_SCATTER_INFO_ID = "output_scatter_info";
    private static final String OUTPUT_TOTAL_BLOCKS_ID = "output_total_blocks";
    
    /**
     * 构造一个新的散布植物节点
     */
    public ScatterPlantsNode() {
        super(UUID.randomUUID(), "flora.output.scatter_plants");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "要散布的植物结构模板", NodeDataType.PLANT_STRUCTURE, this));
        addInputPort(new BasePort(INPUT_CENTER_POSITION_ID, "Center Position", 
                "散布区域的中心坐标", NodeDataType.COORDINATE, this));
        addInputPort(new BasePort(INPUT_PLANT_COUNT_ID, "Plant Count", 
                "要散布的植物数量", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SCATTER_RADIUS_ID, "Scatter Radius", 
                "散布半径（方块数）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_MIN_DISTANCE_ID, "Min Distance", 
                "植物间最小距离（避免重叠）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_AVOID_OVERLAP_ID, "Avoid Overlap", 
                "是否避免植物重叠", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "用于散布位置的随机种子", NodeDataType.INTEGER, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_LIST_ID, "Plant Structure List", 
                "散布后的植物结构列表", NodeDataType.PLANT_STRUCTURE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SCATTER_INFO_ID, "Scatter Info", 
                "散布操作的详细信息", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_BLOCKS_ID, "Total Blocks", 
                "所有植物的总方块数", NodeDataType.INTEGER, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        PlantStructure templatePlant = getInputValue(INPUT_PLANT_STRUCTURE_ID, null);
        BlockPos centerPosition = getInputValue(INPUT_CENTER_POSITION_ID, new BlockPos(0, 0, 0));
        Integer plantCountValue = getInputValue(INPUT_PLANT_COUNT_ID, this.plantCount);
        Float scatterRadiusValue = getInputValue(INPUT_SCATTER_RADIUS_ID, this.scatterRadius);
        Float minDistanceValue = getInputValue(INPUT_MIN_DISTANCE_ID, this.minDistance);
        Boolean avoidOverlapValue = getInputValue(INPUT_AVOID_OVERLAP_ID, this.avoidOverlap);
        Integer randomSeedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        
        // 默认输出值
        List<PlantStructure> scatteredPlants = new ArrayList<>();
        String scatterInfo = "No plant to scatter";
        int totalBlocks = 0;
        
        if (templatePlant != null && !templatePlant.isEmpty()) {
            try {
                // 验证散布参数
                plantCountValue = Math.max(1, Math.min(1000, plantCountValue != null ? plantCountValue : 10));
                scatterRadiusValue = Math.max(1.0f, Math.min(500.0f, scatterRadiusValue != null ? scatterRadiusValue : 20.0f));
                minDistanceValue = Math.max(0.1f, Math.min(50.0f, minDistanceValue != null ? minDistanceValue : 3.0f));
                avoidOverlapValue = avoidOverlapValue != null ? avoidOverlapValue : true;
                randomSeedValue = randomSeedValue != null ? randomSeedValue : 12345;
                
                if (centerPosition == null) {
                    centerPosition = new BlockPos(0, 0, 0);
                }
                
                // 生成散布位置
                List<BlockPos> scatterPositions = generateScatterPositions(
                    centerPosition, plantCountValue, scatterRadiusValue, minDistanceValue, 
                    avoidOverlapValue, randomSeedValue);
                
                // 为每个位置创建植物实例
                for (BlockPos position : scatterPositions) {
                    PlantStructure scatteredPlant = createScatteredPlant(templatePlant, position);
                    scatteredPlants.add(scatteredPlant);
                    totalBlocks += scatteredPlant.getTotalBlockCount();
                }
                
                // 生成散布信息
                scatterInfo = String.format("Scattered: %d plants, Radius: %.1f, Min Distance: %.1f, Total Blocks: %d",
                    scatteredPlants.size(), scatterRadiusValue, minDistanceValue, totalBlocks);
                
            } catch (Exception e) {
                LOGGER.error("Error in Scatter Plants", e);
                scatterInfo = "Error during scattering";
            }
        }
        
        // 设置输出
        outputValues.put(OUTPUT_PLANT_STRUCTURE_LIST_ID, scatteredPlants);
        outputValues.put(OUTPUT_SCATTER_INFO_ID, scatterInfo);
        outputValues.put(OUTPUT_TOTAL_BLOCKS_ID, totalBlocks);
    }
    
    /**
     * 生成散布位置
     */
    private List<BlockPos> generateScatterPositions(BlockPos center, int count, float radius, 
                                                   float minDistance, boolean avoidOverlap, int seed) {
        List<BlockPos> positions = new ArrayList<>();
        Random random = new Random(seed);
        int maxAttempts = count * 50; // 防止无限循环
        
        for (int i = 0; i < count && maxAttempts > 0; maxAttempts--) {
            // 在圆形区域内生成随机位置
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * radius; // 平方根确保均匀分布
            
            int x = (int) Math.round(center.getX() + distance * Math.cos(angle));
            int z = (int) Math.round(center.getZ() + distance * Math.sin(angle));
            int y = center.getY(); // 使用相同的Y坐标
            
            BlockPos candidatePos = new BlockPos(x, y, z);
            
            // 检查是否与现有位置过近
            if (!avoidOverlap || isValidPosition(candidatePos, positions, minDistance)) {
                positions.add(candidatePos);
                i++;
            }
        }
        
        return positions;
    }
    
    /**
     * 检查位置是否有效（不与现有位置过近）
     */
    private boolean isValidPosition(BlockPos candidate, List<BlockPos> existingPositions, float minDistance) {
        for (BlockPos existing : existingPositions) {
            double distance = Math.sqrt(
                Math.pow(candidate.getX() - existing.getX(), 2) +
                Math.pow(candidate.getZ() - existing.getZ(), 2)
            );
            if (distance < minDistance) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 创建散布的植物实例
     */
    private PlantStructure createScatteredPlant(PlantStructure template, BlockPos position) {
        PlantStructure scattered = new PlantStructure();
        
        // 计算模板植物的底部中心点
        BlockPos templateCenter = calculatePlantBase(template);
        
        // 计算偏移量
        int offsetX = position.getX() - templateCenter.getX();
        int offsetY = position.getY() - templateCenter.getY();
        int offsetZ = position.getZ() - templateCenter.getZ();
        
        // 复制并偏移所有方块
        for (PlantStructure.PlantBlock block : template.getTrunkBlocks()) {
            BlockPos newPos = offsetBlock(block.getPosition(), offsetX, offsetY, offsetZ);
            scattered.addTrunkBlock(newPos, block.getBlockType(), block.getThickness());
        }
        
        for (PlantStructure.PlantBlock block : template.getBranchBlocks()) {
            BlockPos newPos = offsetBlock(block.getPosition(), offsetX, offsetY, offsetZ);
            scattered.addBranchBlock(newPos, block.getBlockType(), block.getThickness());
        }
        
        for (PlantStructure.PlantBlock block : template.getLeafBlocks()) {
            BlockPos newPos = offsetBlock(block.getPosition(), offsetX, offsetY, offsetZ);
            scattered.addLeafBlock(newPos, block.getBlockType());
        }
        
        for (PlantStructure.PlantBlock block : template.getFlowerBlocks()) {
            BlockPos newPos = offsetBlock(block.getPosition(), offsetX, offsetY, offsetZ);
            scattered.addFlowerBlock(newPos, block.getBlockType());
        }
        
        for (PlantStructure.PlantBlock block : template.getRootBlocks()) {
            BlockPos newPos = offsetBlock(block.getPosition(), offsetX, offsetY, offsetZ);
            scattered.addRootBlock(newPos, block.getBlockType());
        }
        
        // 复制元数据
        if (template.getMetadata() != null) {
            for (java.util.Map.Entry<String, Object> entry : template.getMetadata().entrySet()) {
                scattered.setMetadata(entry.getKey(), entry.getValue());
            }
        }
        scattered.setMetadata("scatter_position", position.toString());
        
        return scattered;
    }
    
    /**
     * 计算植物的底部中心点
     */
    private BlockPos calculatePlantBase(PlantStructure plant) {
        List<PlantStructure.PlantBlock> allBlocks = plant.getAllBlocks();
        if (allBlocks.isEmpty()) {
            return new BlockPos(0, 0, 0);
        }
        
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (PlantStructure.PlantBlock block : allBlocks) {
            BlockPos pos = block.getPosition();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        return new BlockPos(
            (minX + maxX) / 2,
            minY, // 使用最低点作为底部
            (minZ + maxZ) / 2
        );
    }
    
    /**
     * 偏移方块位置
     */
    private BlockPos offsetBlock(BlockPos original, int offsetX, int offsetY, int offsetZ) {
        return new BlockPos(
            original.getX() + offsetX,
            original.getY() + offsetY,
            original.getZ() + offsetZ
        );
    }
    
    /**
     * 获取输入值的辅助方法
     */
    @SuppressWarnings("unchecked")
    private <T> T getInputValue(String portId, T defaultValue) {
        Object value = inputValues.get(portId);
        if (value != null) {
            try {
                return (T) value;
            } catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    // --- Getters and Setters ---
    
    public int getPlantCount() {
        return plantCount;
    }
    
    public void setPlantCount(int plantCount) {
        this.plantCount = Math.max(1, Math.min(1000, plantCount));
        markDirty();
    }
    
    public float getScatterRadius() {
        return scatterRadius;
    }
    
    public void setScatterRadius(float scatterRadius) {
        this.scatterRadius = Math.max(1.0f, Math.min(500.0f, scatterRadius));
        markDirty();
    }
    
    public float getMinDistance() {
        return minDistance;
    }
    
    public void setMinDistance(float minDistance) {
        this.minDistance = Math.max(0.1f, Math.min(50.0f, minDistance));
        markDirty();
    }
    
    public boolean isAvoidOverlap() {
        return avoidOverlap;
    }
    
    public void setAvoidOverlap(boolean avoidOverlap) {
        this.avoidOverlap = avoidOverlap;
        markDirty();
    }
    
    public int getRandomSeed() {
        return randomSeed;
    }
    
    public void setRandomSeed(int randomSeed) {
        this.randomSeed = randomSeed;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("plantCount", getPlantCount());
        state.put("scatterRadius", getScatterRadius());
        state.put("minDistance", getMinDistance());
        state.put("avoidOverlap", isAvoidOverlap());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("plantCount")) {
                Object plantCountObj = stateMap.get("plantCount");
                if (plantCountObj instanceof Number) {
                    setPlantCount(((Number) plantCountObj).intValue());
                }
            }
            
            if (stateMap.containsKey("scatterRadius")) {
                Object scatterRadiusObj = stateMap.get("scatterRadius");
                if (scatterRadiusObj instanceof Number) {
                    setScatterRadius(((Number) scatterRadiusObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("minDistance")) {
                Object minDistanceObj = stateMap.get("minDistance");
                if (minDistanceObj instanceof Number) {
                    setMinDistance(((Number) minDistanceObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("avoidOverlap")) {
                Object avoidOverlapObj = stateMap.get("avoidOverlap");
                if (avoidOverlapObj instanceof Boolean) {
                    setAvoidOverlap((Boolean) avoidOverlapObj);
                }
            }
            
            if (stateMap.containsKey("randomSeed")) {
                Object randomSeedObj = stateMap.get("randomSeed");
                if (randomSeedObj instanceof Number) {
                    setRandomSeed(((Number) randomSeedObj).intValue());
                }
            }
        }
    }
} 