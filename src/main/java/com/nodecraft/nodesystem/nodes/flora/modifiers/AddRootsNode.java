package com.nodecraft.nodesystem.nodes.flora.modifiers;

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

import java.util.Random;
import java.util.UUID;

/**
 * Add Roots 节点: 为植物添加根系
 */
@NodeInfo(
    id = "flora.modifiers.add_roots",
    displayName = "Add Roots",
    description = "Adds root systems to plants",
    category = "flora.modifiers"
)
public class AddRootsNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddRootsNode.class);
    
    /**
     * 根系类型枚举
     */
    public enum RootType {
        TAP_ROOT("Tap Root", "主根系"),
        FIBROUS("Fibrous", "须根系"),
        BUTTRESS("Buttress", "板根系"),
        ADVENTITIOUS("Adventitious", "不定根"),
        SHALLOW("Shallow", "浅根系");
        
        private final String id;
        private final String displayName;
        
        RootType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        
        public static RootType fromString(String str) {
            for (RootType type : values()) {
                if (type.id.equalsIgnoreCase(str) || type.displayName.equals(str)) {
                    return type;
                }
            }
            return TAP_ROOT; // 默认返回主根系
        }
    }
    
    // --- 节点属性 ---
    private RootType rootType = RootType.TAP_ROOT;    // 根系类型
    private int rootDepth = 3;                        // 根系深度
    private int rootSpread = 2;                       // 根系横向扩展
    private float rootDensity = 0.6f;                 // 根系密度（0-1）
    private String rootMaterial = "minecraft:rooted_dirt"; // 根系材质
    private int randomSeed = 12345;                   // 随机种子
    private String description = "为植物添加根系结构";
    
    // --- 输入端口 IDs ---
    private static final String INPUT_PLANT_STRUCTURE_ID = "input_plant_structure";
    private static final String INPUT_ROOT_TYPE_ID = "input_root_type";
    private static final String INPUT_ROOT_DEPTH_ID = "input_root_depth";
    private static final String INPUT_ROOT_SPREAD_ID = "input_root_spread";
    private static final String INPUT_ROOT_DENSITY_ID = "input_root_density";
    private static final String INPUT_ROOT_MATERIAL_ID = "input_root_material";
    private static final String INPUT_RANDOM_SEED_ID = "input_random_seed";
    
    // --- 输出端口 IDs ---
    private static final String OUTPUT_PLANT_STRUCTURE_ID = "output_plant_structure";
    private static final String OUTPUT_ROOT_INFO_ID = "output_root_info";
    
    /**
     * 构造一个新的添加根系节点
     */
    public AddRootsNode() {
        super(UUID.randomUUID(), "flora.modifiers.add_roots");
        
        // 创建并添加输入端口
        addInputPort(new BasePort(INPUT_PLANT_STRUCTURE_ID, "Plant Structure", 
                "要添加根系的植物结构", NodeDataType.PLANT_STRUCTURE, this));
        addInputPort(new BasePort(INPUT_ROOT_TYPE_ID, "Root Type", 
                "根系类型（Tap Root、Fibrous、Buttress等）", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_ROOT_DEPTH_ID, "Root Depth", 
                "根系深度（向下的方块数）", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROOT_SPREAD_ID, "Root Spread", 
                "根系横向扩展半径", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_ROOT_DENSITY_ID, "Root Density", 
                "根系密度（0-1）", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_ROOT_MATERIAL_ID, "Root Material", 
                "根系材质（Minecraft方块ID）", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_RANDOM_SEED_ID, "Random Seed", 
                "用于根系生成的随机种子", NodeDataType.INTEGER, this));
        
        // 创建并添加输出端口
        addOutputPort(new BasePort(OUTPUT_PLANT_STRUCTURE_ID, "Plant with Roots", 
                "添加根系后的植物结构", NodeDataType.PLANT_STRUCTURE, this));
        addOutputPort(new BasePort(OUTPUT_ROOT_INFO_ID, "Root Info", 
                "根系添加操作的详细信息", NodeDataType.STRING, this));
    }
    
    @Override
    public String getDescription() {
        return this.description;
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        // 获取输入值
        PlantStructure inputPlant = getInputValue(INPUT_PLANT_STRUCTURE_ID, null);
        String rootTypeStr = getInputValue(INPUT_ROOT_TYPE_ID, this.rootType.getId());
        Integer rootDepthValue = getInputValue(INPUT_ROOT_DEPTH_ID, this.rootDepth);
        Integer rootSpreadValue = getInputValue(INPUT_ROOT_SPREAD_ID, this.rootSpread);
        Float rootDensityValue = getInputValue(INPUT_ROOT_DENSITY_ID, this.rootDensity);
        String rootMaterialValue = getInputValue(INPUT_ROOT_MATERIAL_ID, this.rootMaterial);
        Integer randomSeedValue = getInputValue(INPUT_RANDOM_SEED_ID, this.randomSeed);
        
        // 默认输出值
        PlantStructure plantWithRoots = new PlantStructure();
        String rootInfo = "No plant to add roots";
        
        if (inputPlant != null && !inputPlant.isEmpty()) {
            try {
                // 验证根系参数
                RootType currentRootType = RootType.fromString(rootTypeStr);
                rootDepthValue = Math.max(0, Math.min(20, rootDepthValue != null ? rootDepthValue : 3));
                rootSpreadValue = Math.max(0, Math.min(15, rootSpreadValue != null ? rootSpreadValue : 2));
                rootDensityValue = Math.max(0.0f, Math.min(1.0f, rootDensityValue != null ? rootDensityValue : 0.6f));
                rootMaterialValue = validateMaterial(rootMaterialValue, "minecraft:rooted_dirt");
                randomSeedValue = randomSeedValue != null ? randomSeedValue : 12345;
                
                // 复制原始植物结构
                plantWithRoots = inputPlant.copy();
                
                // 添加根系
                int rootsAdded = addRoots(plantWithRoots, currentRootType, rootDepthValue, 
                                        rootSpreadValue, rootDensityValue, rootMaterialValue, randomSeedValue);
                
                // 复制原始元数据并添加根系信息
                if (inputPlant.getMetadata() != null) {
                    for (java.util.Map.Entry<String, Object> entry : inputPlant.getMetadata().entrySet()) {
                        plantWithRoots.setMetadata(entry.getKey(), entry.getValue());
                    }
                }
                plantWithRoots.setMetadata("root_type", currentRootType.getId());
                plantWithRoots.setMetadata("root_depth", rootDepthValue);
                plantWithRoots.setMetadata("root_spread", rootSpreadValue);
                plantWithRoots.setMetadata("root_density", rootDensityValue);
                plantWithRoots.setMetadata("root_material", rootMaterialValue);
                plantWithRoots.setMetadata("roots_added", rootsAdded);
                
                // 生成根系信息
                rootInfo = String.format("Added %s: Depth=%d, Spread=%d, Density=%.1f%%, Added %d root blocks",
                    currentRootType.getDisplayName(), rootDepthValue, rootSpreadValue, 
                    rootDensityValue * 100, rootsAdded);
                
            } catch (Exception e) {
                LOGGER.error("Error in Add Roots", e);
                plantWithRoots = inputPlant; // 返回原始植物
                rootInfo = "Error during root addition";
            }
        }
        
        // 设置输出
        outputValues.put(OUTPUT_PLANT_STRUCTURE_ID, plantWithRoots);
        outputValues.put(OUTPUT_ROOT_INFO_ID, rootInfo);
    }
    
    /**
     * 为植物添加根系
     */
    private int addRoots(PlantStructure plant, RootType rootType, int depth, int spread, 
                        float density, String material, int seed) {
        Random random = new Random(seed);
        int rootsAdded = 0;
        
        // 找到植物的基部位置
        BlockPos basePos = calculatePlantBase(plant);
        
        switch (rootType) {
            case TAP_ROOT:
                rootsAdded = generateTapRoot(plant, basePos, depth, spread, density, material, random);
                break;
            case FIBROUS:
                rootsAdded = generateFibrousRoots(plant, basePos, depth, spread, density, material, random);
                break;
            case BUTTRESS:
                rootsAdded = generateButtressRoots(plant, basePos, depth, spread, density, material, random);
                break;
            case ADVENTITIOUS:
                rootsAdded = generateAdventitiousRoots(plant, basePos, depth, spread, density, material, random);
                break;
            case SHALLOW:
                rootsAdded = generateShallowRoots(plant, basePos, depth, spread, density, material, random);
                break;
        }
        
        return rootsAdded;
    }
    
    /**
     * 生成主根系
     */
    private int generateTapRoot(PlantStructure plant, BlockPos base, int depth, int spread, 
                               float density, String material, Random random) {
        int rootsAdded = 0;
        
        // 主根向下生长
        for (int y = 1; y <= depth; y++) {
            BlockPos rootPos = new BlockPos(base.getX(), base.getY() - y, base.getZ());
            plant.addRootBlock(rootPos, material);
            rootsAdded++;
            
            // 添加侧根
            if (y > 1 && random.nextFloat() < density) {
                int sideRootCount = random.nextInt(4) + 1;
                for (int i = 0; i < sideRootCount; i++) {
                    int dx = random.nextInt(spread * 2 + 1) - spread;
                    int dz = random.nextInt(spread * 2 + 1) - spread;
                    if (dx != 0 || dz != 0) {
                        BlockPos sideRootPos = new BlockPos(base.getX() + dx, base.getY() - y, base.getZ() + dz);
                        plant.addRootBlock(sideRootPos, material);
                        rootsAdded++;
                    }
                }
            }
        }
        
        return rootsAdded;
    }
    
    /**
     * 生成须根系
     */
    private int generateFibrousRoots(PlantStructure plant, BlockPos base, int depth, int spread, 
                                    float density, String material, Random random) {
        int rootsAdded = 0;
        
        // 多个细根从基部向四周伸展
        int rootCount = (int) (8 * density);
        for (int i = 0; i < rootCount; i++) {
            double angle = (2 * Math.PI * i) / rootCount + random.nextGaussian() * 0.3;
            int rootLength = random.nextInt(spread) + 1;
            int rootDepth = random.nextInt(depth) + 1;
            
            for (int j = 1; j <= rootLength; j++) {
                int x = (int) (base.getX() + Math.cos(angle) * j);
                int z = (int) (base.getZ() + Math.sin(angle) * j);
                int y = base.getY() - random.nextInt(rootDepth + 1);
                
                BlockPos rootPos = new BlockPos(x, y, z);
                plant.addRootBlock(rootPos, material);
                rootsAdded++;
            }
        }
        
        return rootsAdded;
    }
    
    /**
     * 生成板根系
     */
    private int generateButtressRoots(PlantStructure plant, BlockPos base, int depth, int spread, 
                                     float density, String material, Random random) {
        int rootsAdded = 0;
        
        // 4-6个大型支撑根
        int buttressCount = random.nextInt(3) + 4;
        for (int i = 0; i < buttressCount; i++) {
            double angle = (2 * Math.PI * i) / buttressCount;
            
            // 生成板状根
            for (int r = 1; r <= spread; r++) {
                for (int h = 0; h <= Math.min(depth, 2); h++) {
                    int x = (int) (base.getX() + Math.cos(angle) * r);
                    int z = (int) (base.getZ() + Math.sin(angle) * r);
                    int y = base.getY() - h;
                    
                    if (random.nextFloat() < density) {
                        BlockPos rootPos = new BlockPos(x, y, z);
                        plant.addRootBlock(rootPos, material);
                        rootsAdded++;
                    }
                }
            }
        }
        
        return rootsAdded;
    }
    
    /**
     * 生成不定根
     */
    private int generateAdventitiousRoots(PlantStructure plant, BlockPos base, int depth, int spread, 
                                         float density, String material, Random random) {
        int rootsAdded = 0;
        
        // 从茎部多个位置长出根系
        java.util.List<PlantStructure.PlantBlock> trunkBlocks = plant.getTrunkBlocks();
        
        for (PlantStructure.PlantBlock trunkBlock : trunkBlocks) {
            if (trunkBlock.getPosition().getY() <= base.getY() + 2 && random.nextFloat() < density * 0.3f) {
                BlockPos trunkPos = trunkBlock.getPosition();
                
                // 从这个位置生成几条根
                int rootCount = random.nextInt(3) + 1;
                for (int i = 0; i < rootCount; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    int rootLength = random.nextInt(spread) + 1;
                    
                    for (int j = 1; j <= rootLength; j++) {
                        int x = (int) (trunkPos.getX() + Math.cos(angle) * j);
                        int z = (int) (trunkPos.getZ() + Math.sin(angle) * j);
                        int y = trunkPos.getY() - random.nextInt(depth + 1);
                        
                        BlockPos rootPos = new BlockPos(x, y, z);
                        plant.addRootBlock(rootPos, material);
                        rootsAdded++;
                    }
                }
            }
        }
        
        return rootsAdded;
    }
    
    /**
     * 生成浅根系
     */
    private int generateShallowRoots(PlantStructure plant, BlockPos base, int depth, int spread, 
                                    float density, String material, Random random) {
        int rootsAdded = 0;
        
        // 主要在表层扩展
        int shallowDepth = Math.min(depth, 2);
        
        for (int y = 0; y <= shallowDepth; y++) {
            int currentSpread = spread - y; // 深度越深，扩展越小
            
            for (int x = -currentSpread; x <= currentSpread; x++) {
                for (int z = -currentSpread; z <= currentSpread; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance <= currentSpread && distance > 0 && random.nextFloat() < density * 0.4f) {
                        BlockPos rootPos = new BlockPos(base.getX() + x, base.getY() - y, base.getZ() + z);
                        plant.addRootBlock(rootPos, material);
                        rootsAdded++;
                    }
                }
            }
        }
        
        return rootsAdded;
    }
    
    /**
     * 计算植物的底部中心点
     */
    private BlockPos calculatePlantBase(PlantStructure plant) {
        java.util.List<PlantStructure.PlantBlock> allBlocks = plant.getAllBlocks();
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
     * 验证材质名称
     */
    private String validateMaterial(String material, String defaultMaterial) {
        if (material == null || material.trim().isEmpty()) {
            return defaultMaterial;
        }
        
        String trimmed = material.trim();
        
        // 如果没有命名空间前缀，添加minecraft:
        if (!trimmed.contains(":")) {
            return "minecraft:" + trimmed;
        }
        
        return trimmed;
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
    
    public RootType getRootType() {
        return rootType;
    }
    
    public void setRootType(RootType rootType) {
        this.rootType = rootType != null ? rootType : RootType.TAP_ROOT;
        markDirty();
    }
    
    public void setRootType(String rootTypeStr) {
        setRootType(RootType.fromString(rootTypeStr));
    }
    
    public int getRootDepth() {
        return rootDepth;
    }
    
    public void setRootDepth(int rootDepth) {
        this.rootDepth = Math.max(0, Math.min(20, rootDepth));
        markDirty();
    }
    
    public int getRootSpread() {
        return rootSpread;
    }
    
    public void setRootSpread(int rootSpread) {
        this.rootSpread = Math.max(0, Math.min(15, rootSpread));
        markDirty();
    }
    
    public float getRootDensity() {
        return rootDensity;
    }
    
    public void setRootDensity(float rootDensity) {
        this.rootDensity = Math.max(0.0f, Math.min(1.0f, rootDensity));
        markDirty();
    }
    
    public String getRootMaterial() {
        return rootMaterial;
    }
    
    public void setRootMaterial(String rootMaterial) {
        this.rootMaterial = validateMaterial(rootMaterial, "minecraft:rooted_dirt");
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
        state.put("rootType", getRootType().getId());
        state.put("rootDepth", getRootDepth());
        state.put("rootSpread", getRootSpread());
        state.put("rootDensity", getRootDensity());
        state.put("rootMaterial", getRootMaterial());
        state.put("randomSeed", getRandomSeed());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("rootType")) {
                Object rootTypeObj = stateMap.get("rootType");
                if (rootTypeObj instanceof String) {
                    setRootType((String) rootTypeObj);
                }
            }
            
            if (stateMap.containsKey("rootDepth")) {
                Object rootDepthObj = stateMap.get("rootDepth");
                if (rootDepthObj instanceof Number) {
                    setRootDepth(((Number) rootDepthObj).intValue());
                }
            }
            
            if (stateMap.containsKey("rootSpread")) {
                Object rootSpreadObj = stateMap.get("rootSpread");
                if (rootSpreadObj instanceof Number) {
                    setRootSpread(((Number) rootSpreadObj).intValue());
                }
            }
            
            if (stateMap.containsKey("rootDensity")) {
                Object rootDensityObj = stateMap.get("rootDensity");
                if (rootDensityObj instanceof Number) {
                    setRootDensity(((Number) rootDensityObj).floatValue());
                }
            }
            
            if (stateMap.containsKey("rootMaterial")) {
                Object rootMaterialObj = stateMap.get("rootMaterial");
                if (rootMaterialObj instanceof String) {
                    setRootMaterial((String) rootMaterialObj);
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