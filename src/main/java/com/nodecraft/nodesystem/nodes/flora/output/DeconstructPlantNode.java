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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deconstruct Plant 节点: 解构植物结构为其组成部分
 */
@NodeInfo(
    id = "flora.output.deconstruct_plant",
    displayName = "Deconstruct Plant",
    description = "Deconstructs plant structures into component parts",
    category = "flora.output"
)
public class DeconstructPlantNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeconstructPlantNode.class);
    
    // --- 节点属性 ---
    private boolean outputTrunk = true;              // 是否输出主干
    private boolean outputBranches = true;           // 是否输出分支
    private boolean outputLeaves = true;             // 是否输出叶子
    private boolean outputFlowers = true;            // 是否输出花朵
    private boolean outputRoots = true;              // 是否输出根系
    private boolean includePositions = true;        // 是否包含位置信息
    private boolean includeNBT = false;              // 是否包含NBT数据
    private boolean includeMetadata = true;          // 是否包含元数据
    private String description = "解构植物结构为其组成部分";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_PLANT_STRUCTURE_ID = "input_plant_structure";
    private static final String INPUT_OUTPUT_TRUNK_ID = "input_output_trunk";
    private static final String INPUT_OUTPUT_BRANCHES_ID = "input_output_branches";
    private static final String INPUT_OUTPUT_LEAVES_ID = "input_output_leaves";
    private static final String INPUT_OUTPUT_FLOWERS_ID = "input_output_flowers";
    private static final String INPUT_OUTPUT_ROOTS_ID = "input_output_roots";
    private static final String INPUT_INCLUDE_POSITIONS_ID = "input_include_positions";
    private static final String INPUT_INCLUDE_NBT_ID = "input_include_nbt";
    private static final String INPUT_INCLUDE_METADATA_ID = "input_include_metadata";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_TRUNK_BLOCKS_ID = "output_trunk_blocks";
    private static final String OUTPUT_BRANCH_BLOCKS_ID = "output_branch_blocks";
    private static final String OUTPUT_LEAF_BLOCKS_ID = "output_leaf_blocks";
    private static final String OUTPUT_FLOWER_BLOCKS_ID = "output_flower_blocks";
    private static final String OUTPUT_ROOT_BLOCKS_ID = "output_root_blocks";
    private static final String OUTPUT_POSITIONS_ID = "output_positions";
    private static final String OUTPUT_MATERIALS_ID = "output_materials";
    private static final String OUTPUT_STATISTICS_ID = "output_statistics";
    private static final String OUTPUT_METADATA_ID = "output_metadata";
    private static final String OUTPUT_DECONSTRUCT_INFO_ID = "output_deconstruct_info";
    
    /**
     * 构造一个新的解构植物节点
     */
    public DeconstructPlantNode() {
        super(UUID.randomUUID(), "flora.output.deconstruct_plant");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "要解构的植物结构", NodeDataType.PLANT_STRUCTURE, this));
        addInputPort(new BasePort(INPUT_OUTPUT_TRUNK_ID, "Output Trunk", 
                "是否输出主干方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_OUTPUT_BRANCHES_ID, "Output Branches", 
                "是否输出分支方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_OUTPUT_LEAVES_ID, "Output Leaves", 
                "是否输出叶子方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_OUTPUT_FLOWERS_ID, "Output Flowers", 
                "是否输出花朵方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_OUTPUT_ROOTS_ID, "Output Roots", 
                "是否输出根系方块", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_POSITIONS_ID, "Include Positions", 
                "是否包含位置信息", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_NBT_ID, "Include NBT", 
                "是否包含NBT数据", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INCLUDE_METADATA_ID, "Include Metadata", 
                "是否包含元数据", NodeDataType.BOOLEAN, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_TRUNK_BLOCKS_ID, "Trunk Blocks", 
                "主干方块列表", NodeDataType.PLANT_BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_BLOCKS_ID, "Branch Blocks", 
                "分支方块列表", NodeDataType.PLANT_BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LEAF_BLOCKS_ID, "Leaf Blocks", 
                "叶子方块列表", NodeDataType.PLANT_BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_FLOWER_BLOCKS_ID, "Flower Blocks", 
                "花朵方块列表", NodeDataType.PLANT_BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ROOT_BLOCKS_ID, "Root Blocks", 
                "根系方块列表", NodeDataType.PLANT_BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_POSITIONS_ID, "All Positions", 
                "所有方块位置列表", NodeDataType.COORDINATE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_MATERIALS_ID, "Materials", 
                "使用的材质列表", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_STATISTICS_ID, "Statistics", 
                "植物统计信息", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_METADATA_ID, "Metadata", 
                "植物元数据", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_DECONSTRUCT_INFO_ID, "Deconstruct Info", 
                "解构操作的详细信息", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        PlantStructure inputPlant = getInputValue(INPUT_PLANT_STRUCTURE_ID, null);
        Boolean outputTrunkValue = getInputValue(INPUT_OUTPUT_TRUNK_ID, this.outputTrunk);
        Boolean outputBranchesValue = getInputValue(INPUT_OUTPUT_BRANCHES_ID, this.outputBranches);
        Boolean outputLeavesValue = getInputValue(INPUT_OUTPUT_LEAVES_ID, this.outputLeaves);
        Boolean outputFlowersValue = getInputValue(INPUT_OUTPUT_FLOWERS_ID, this.outputFlowers);
        Boolean outputRootsValue = getInputValue(INPUT_OUTPUT_ROOTS_ID, this.outputRoots);
        Boolean includePositionsValue = getInputValue(INPUT_INCLUDE_POSITIONS_ID, this.includePositions);
        Boolean includeNBTValue = getInputValue(INPUT_INCLUDE_NBT_ID, this.includeNBT);
        Boolean includeMetadataValue = getInputValue(INPUT_INCLUDE_METADATA_ID, this.includeMetadata);
        
        // 默认输出值
        List<PlantStructure.PlantBlock> trunkBlocks = new ArrayList<>();
        List<PlantStructure.PlantBlock> branchBlocks = new ArrayList<>();
        List<PlantStructure.PlantBlock> leafBlocks = new ArrayList<>();
        List<PlantStructure.PlantBlock> flowerBlocks = new ArrayList<>();
        List<PlantStructure.PlantBlock> rootBlocks = new ArrayList<>();
        List<BlockPos> allPositions = new ArrayList<>();
        List<String> materials = new ArrayList<>();
        String statistics = "No plant to deconstruct";
        String metadata = "";
        String deconstructInfo = "No plant to deconstruct";
        
        if (inputPlant != null && !inputPlant.isEmpty()) {
            try {
                // 验证解构参数
                outputTrunkValue = outputTrunkValue != null ? outputTrunkValue : true;
                outputBranchesValue = outputBranchesValue != null ? outputBranchesValue : true;
                outputLeavesValue = outputLeavesValue != null ? outputLeavesValue : true;
                outputFlowersValue = outputFlowersValue != null ? outputFlowersValue : true;
                outputRootsValue = outputRootsValue != null ? outputRootsValue : true;
                includePositionsValue = includePositionsValue != null ? includePositionsValue : true;
                includeNBTValue = includeNBTValue != null ? includeNBTValue : false;
                includeMetadataValue = includeMetadataValue != null ? includeMetadataValue : true;
                
                // 解构植物结构
                if (outputTrunkValue) {
                    trunkBlocks = inputPlant.getTrunkBlocks();
                }
                if (outputBranchesValue) {
                    branchBlocks = inputPlant.getBranchBlocks();
                }
                if (outputLeavesValue) {
                    leafBlocks = inputPlant.getLeafBlocks();
                }
                if (outputFlowersValue) {
                    flowerBlocks = inputPlant.getFlowerBlocks();
                }
                if (outputRootsValue) {
                    rootBlocks = inputPlant.getRootBlocks();
                }
                
                // 收集位置信息
                if (includePositionsValue) {
                    allPositions = inputPlant.getAllPositions();
                }
                
                // 收集材质信息
                materials = extractMaterials(inputPlant);
                
                // 生成统计信息
                statistics = generateStatistics(inputPlant);
                
                // 生成元数据信息
                if (includeMetadataValue) {
                    metadata = formatMetadata(inputPlant.getMetadata());
                }
                
                // 生成解构信息
                int totalOutputBlocks = 0;
                if (outputTrunkValue) totalOutputBlocks += trunkBlocks.size();
                if (outputBranchesValue) totalOutputBlocks += branchBlocks.size();
                if (outputLeavesValue) totalOutputBlocks += leafBlocks.size();
                if (outputFlowersValue) totalOutputBlocks += flowerBlocks.size();
                if (outputRootsValue) totalOutputBlocks += rootBlocks.size();
                
                deconstructInfo = String.format("Deconstructed: %d total blocks, %d materials, Trunk=%s, Branches=%s, Leaves=%s, Flowers=%s, Roots=%s",
                    inputPlant.getTotalBlockCount(), materials.size(), 
                    outputTrunkValue ? "Yes" : "No", outputBranchesValue ? "Yes" : "No",
                    outputLeavesValue ? "Yes" : "No", outputFlowersValue ? "Yes" : "No", 
                    outputRootsValue ? "Yes" : "No");
                
            } catch (Exception e) {
                LOGGER.error("Error in Deconstruct Plant", e);
                deconstructInfo = "Error during deconstruction";
            }
        }
        
        // 设置输出
        outputValues.put(OUTPUT_TRUNK_BLOCKS_ID, trunkBlocks);
        outputValues.put(OUTPUT_BRANCH_BLOCKS_ID, branchBlocks);
        outputValues.put(OUTPUT_LEAF_BLOCKS_ID, leafBlocks);
        outputValues.put(OUTPUT_FLOWER_BLOCKS_ID, flowerBlocks);
        outputValues.put(OUTPUT_ROOT_BLOCKS_ID, rootBlocks);
        outputValues.put(OUTPUT_POSITIONS_ID, allPositions);
        outputValues.put(OUTPUT_MATERIALS_ID, materials);
        outputValues.put(OUTPUT_STATISTICS_ID, statistics);
        outputValues.put(OUTPUT_METADATA_ID, metadata);
        outputValues.put(OUTPUT_DECONSTRUCT_INFO_ID, deconstructInfo);
    }
    
    /**
     * 提取植物中使用的所有材质
     */
    private List<String> extractMaterials(PlantStructure plant) {
        java.util.Set<String> materialSet = new java.util.HashSet<>();
        
        for (PlantStructure.PlantBlock block : plant.getAllBlocks()) {
            materialSet.add(block.getBlockType());
        }
        
        return new ArrayList<>(materialSet);
    }
    
    /**
     * 生成植物统计信息
     */
    private String generateStatistics(PlantStructure plant) {
        StringBuilder stats = new StringBuilder();
        
        stats.append("Plant Statistics:\n");
        stats.append(String.format("Total Blocks: %d\n", plant.getTotalBlockCount()));
        stats.append(String.format("- Trunk: %d blocks\n", plant.getTrunkBlockCount()));
        stats.append(String.format("- Branches: %d blocks\n", plant.getBranchBlockCount()));
        stats.append(String.format("- Leaves: %d blocks\n", plant.getLeafBlockCount()));
        stats.append(String.format("- Flowers: %d blocks\n", plant.getFlowerBlockCount()));
        stats.append(String.format("- Roots: %d blocks\n", plant.getRootBlockCount()));
        
        // 计算边界框
        BoundingBox bounds = calculateBoundingBox(plant);
        if (bounds != null) {
            stats.append(String.format("Bounding Box: %dx%dx%d\n", 
                bounds.width, bounds.height, bounds.depth));
            stats.append(String.format("Center: (%d, %d, %d)\n", 
                bounds.centerX, bounds.centerY, bounds.centerZ));
        }
        
        // 材质统计
        List<String> materials = extractMaterials(plant);
        stats.append(String.format("Materials Used: %d types\n", materials.size()));
        for (String material : materials) {
            int count = countMaterialUsage(plant, material);
            stats.append(String.format("- %s: %d blocks\n", material, count));
        }
        
        return stats.toString();
    }
    
    /**
     * 计算植物的边界框
     */
    private BoundingBox calculateBoundingBox(PlantStructure plant) {
        List<PlantStructure.PlantBlock> allBlocks = plant.getAllBlocks();
        if (allBlocks.isEmpty()) {
            return null;
        }
        
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (PlantStructure.PlantBlock block : allBlocks) {
            BlockPos pos = block.getPosition();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * 计算特定材质的使用数量
     */
    private int countMaterialUsage(PlantStructure plant, String material) {
        int count = 0;
        for (PlantStructure.PlantBlock block : plant.getAllBlocks()) {
            if (material.equals(block.getBlockType())) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 格式化元数据信息
     */
    private String formatMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "No metadata available";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Plant Metadata:\n");
        
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
        }
        
        return sb.toString();
    }
    
    /**
     * 边界框辅助类
     */
    private static class BoundingBox {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final int width, height, depth;
        final int centerX, centerY, centerZ;
        
        BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            
            this.width = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.depth = maxZ - minZ + 1;
            
            this.centerX = (minX + maxX) / 2;
            this.centerY = (minY + maxY) / 2;
            this.centerZ = (minZ + maxZ) / 2;
        }
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
    
    public boolean isOutputTrunk() {
        return outputTrunk;
    }
    
    public void setOutputTrunk(boolean outputTrunk) {
        this.outputTrunk = outputTrunk;
        markDirty();
    }
    
    public boolean isOutputBranches() {
        return outputBranches;
    }
    
    public void setOutputBranches(boolean outputBranches) {
        this.outputBranches = outputBranches;
        markDirty();
    }
    
    public boolean isOutputLeaves() {
        return outputLeaves;
    }
    
    public void setOutputLeaves(boolean outputLeaves) {
        this.outputLeaves = outputLeaves;
        markDirty();
    }
    
    public boolean isOutputFlowers() {
        return outputFlowers;
    }
    
    public void setOutputFlowers(boolean outputFlowers) {
        this.outputFlowers = outputFlowers;
        markDirty();
    }
    
    public boolean isOutputRoots() {
        return outputRoots;
    }
    
    public void setOutputRoots(boolean outputRoots) {
        this.outputRoots = outputRoots;
        markDirty();
    }
    
    public boolean isIncludePositions() {
        return includePositions;
    }
    
    public void setIncludePositions(boolean includePositions) {
        this.includePositions = includePositions;
        markDirty();
    }
    
    public boolean isIncludeNBT() {
        return includeNBT;
    }
    
    public void setIncludeNBT(boolean includeNBT) {
        this.includeNBT = includeNBT;
        markDirty();
    }
    
    public boolean isIncludeMetadata() {
        return includeMetadata;
    }
    
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
        markDirty();
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("outputTrunk", isOutputTrunk());
        state.put("outputBranches", isOutputBranches());
        state.put("outputLeaves", isOutputLeaves());
        state.put("outputFlowers", isOutputFlowers());
        state.put("outputRoots", isOutputRoots());
        state.put("includePositions", isIncludePositions());
        state.put("includeNBT", isIncludeNBT());
        state.put("includeMetadata", isIncludeMetadata());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map) {
            Map<?, ?> stateMap = (Map<?, ?>) state;
            
            if (stateMap.containsKey("outputTrunk")) {
                Object outputTrunkObj = stateMap.get("outputTrunk");
                if (outputTrunkObj instanceof Boolean) {
                    setOutputTrunk((Boolean) outputTrunkObj);
                }
            }
            
            if (stateMap.containsKey("outputBranches")) {
                Object outputBranchesObj = stateMap.get("outputBranches");
                if (outputBranchesObj instanceof Boolean) {
                    setOutputBranches((Boolean) outputBranchesObj);
                }
            }
            
            if (stateMap.containsKey("outputLeaves")) {
                Object outputLeavesObj = stateMap.get("outputLeaves");
                if (outputLeavesObj instanceof Boolean) {
                    setOutputLeaves((Boolean) outputLeavesObj);
                }
            }
            
            if (stateMap.containsKey("outputFlowers")) {
                Object outputFlowersObj = stateMap.get("outputFlowers");
                if (outputFlowersObj instanceof Boolean) {
                    setOutputFlowers((Boolean) outputFlowersObj);
                }
            }
            
            if (stateMap.containsKey("outputRoots")) {
                Object outputRootsObj = stateMap.get("outputRoots");
                if (outputRootsObj instanceof Boolean) {
                    setOutputRoots((Boolean) outputRootsObj);
                }
            }
            
            if (stateMap.containsKey("includePositions")) {
                Object includePositionsObj = stateMap.get("includePositions");
                if (includePositionsObj instanceof Boolean) {
                    setIncludePositions((Boolean) includePositionsObj);
                }
            }
            
            if (stateMap.containsKey("includeNBT")) {
                Object includeNBTObj = stateMap.get("includeNBT");
                if (includeNBTObj instanceof Boolean) {
                    setIncludeNBT((Boolean) includeNBTObj);
                }
            }
            
            if (stateMap.containsKey("includeMetadata")) {
                Object includeMetadataObj = stateMap.get("includeMetadata");
                if (includeMetadataObj instanceof Boolean) {
                    setIncludeMetadata((Boolean) includeMetadataObj);
                }
            }
        }
    }
} 