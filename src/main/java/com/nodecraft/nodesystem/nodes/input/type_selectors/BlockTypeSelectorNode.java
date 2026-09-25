package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.type_selectors.block_type_selector",
    displayName = "Block Type Selector",
    description = "Searches and selects a Minecraft block type.",
    category = "input.type_selectors",
    order = 0
)
public class BlockTypeSelectorNode extends AbstractRegistryTypeSelectorNode {

    private static final String CATEGORY_STONE = "stone";
    private static final String CATEGORY_WOOD = "wood";
    private static final String CATEGORY_NATURAL = "natural";
    private static final String CATEGORY_DECOR = "decor";
    private static final String CATEGORY_REDSTONE = "redstone";
    private static final String CATEGORY_FUNCTIONAL = "functional";
    private static final String CATEGORY_NETHER_END = "nether_end";

    private static final String[] QUICK_BLOCKS = {
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:stone_bricks",
        "minecraft:polished_andesite",
        "minecraft:smooth_stone",
        "minecraft:glass",
        "minecraft:oak_planks",
        "minecraft:quartz_block"
    };

    private static final CategorySpec[] CATEGORIES = {
        new CategorySpec(CATEGORY_ALL, "All"),
        new CategorySpec(CATEGORY_STONE, "Stone"),
        new CategorySpec(CATEGORY_WOOD, "Wood"),
        new CategorySpec(CATEGORY_NATURAL, "Natural"),
        new CategorySpec(CATEGORY_DECOR, "Decor"),
        new CategorySpec(CATEGORY_REDSTONE, "Redstone"),
        new CategorySpec(CATEGORY_FUNCTIONAL, "Functional"),
        new CategorySpec(CATEGORY_NETHER_END, "Nether/End"),
        new CategorySpec(CATEGORY_MODDED, "Modded")
    };

    @NodeProperty(
        displayName = "Selected Block",
        category = "Selection",
        order = 1,
        description = "The currently selected block type id."
    )
    private String selectedBlock = "minecraft:stone";

    @NodeProperty(
        displayName = "Allow Modded Blocks",
        category = "Filter",
        order = 2,
        description = "Whether block ids outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_BLOCK_PATH = "output_block_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    public BlockTypeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.block_type_selector");

        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block Type", "The selected block's full identifier", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected block id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_PATH, "Block Path", "The path part of the selected block id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected block is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addValidOutputPort();

        onSelectionApplied();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft block type.";
    }

    @Override
    protected String getPickerPopupKey() {
        return "block_picker";
    }

    @Override
    protected String getPickerTitle() {
        return "Select Block";
    }

    @Override
    protected String getSearchHint() {
        return "Search block id...";
    }

    @Override
    protected String getOpenButtonIdSuffix() {
        return "##open_block_picker";
    }

    @Override
    protected String readSelectedId() {
        return selectedBlock;
    }

    @Override
    protected void writeSelectedId(String canonicalId) {
        this.selectedBlock = canonicalId;
    }

    @Override
    protected boolean isAllowModded() {
        return allowModded;
    }

    @Override
    protected void applyAllowModdedQuietly(boolean allowModded) {
        this.allowModded = allowModded;
        normalizeFilterState();
    }

    @Override
    protected void setAllowModdedFlag(boolean allowModded) {
        applyAllowModdedQuietly(allowModded);
        updateFilteredListFromSearch();
        onSelectionApplied();
    }

    @Override
    protected String getDefaultId() {
        return "minecraft:stone";
    }

    @Override
    protected String[] getQuickPickIds() {
        return QUICK_BLOCKS;
    }

    @Override
    protected CategorySpec[] getCategorySpecs() {
        return CATEGORIES;
    }

    @Override
    protected void collectRegistryIds(List<String> target) {
        for (Identifier id : Registries.BLOCK.getIds()) {
            target.add(id.toString());
        }
    }

    @Override
    protected boolean isKnownId(String id) {
        if (catalogContains(id)) {
            return true;
        }
        try {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                return false;
            }
            if (Registries.BLOCK.getIds().isEmpty()) {
                return false;
            }
            return Registries.BLOCK.containsId(parsed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    protected boolean matchesCategory(String fullId, String categoryKey) {
        if (CATEGORY_ALL.equals(categoryKey)) {
            return true;
        }
        if (CATEGORY_MODDED.equals(categoryKey)) {
            return matchesModdedCategory(fullId);
        }

        String path = fullId;
        String[] parts = fullId.split(":", 2);
        if (parts.length == 2) {
            if (!"minecraft".equals(parts[0])) {
                return false;
            }
            path = parts[1];
        }

        return switch (categoryKey) {
            case CATEGORY_STONE -> containsAny(path,
                "stone", "deepslate", "cobble", "granite", "diorite", "andesite", "tuff",
                "basalt", "calcite", "dripstone", "blackstone", "sandstone", "ore", "brick");
            case CATEGORY_WOOD -> containsAny(path,
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
                "planks", "_log", "_wood", "stripped", "_stairs", "_slab", "_fence",
                "_door", "_trapdoor", "_button", "_pressure_plate");
            case CATEGORY_NATURAL -> containsAny(path,
                "dirt", "grass", "mud", "clay", "sand", "gravel", "snow", "ice", "leaves", "sapling",
                "flower", "mushroom", "cactus", "vine", "bamboo", "wheat", "carrot", "potato", "melon", "pumpkin");
            case CATEGORY_DECOR -> containsAny(path,
                "glass", "wool", "carpet", "terracotta", "concrete", "banner", "lantern", "glowstone",
                "candle", "amethyst", "shelf", "pot", "bed");
            case CATEGORY_REDSTONE -> containsAny(path,
                "redstone", "repeater", "comparator", "observer", "piston", "lever", "button",
                "pressure_plate", "rail", "hopper", "dispenser", "dropper", "daylight", "tripwire",
                "target", "note_block", "sculk_sensor");
            case CATEGORY_FUNCTIONAL -> containsAny(path,
                "crafting", "furnace", "chest", "barrel", "anvil", "enchant", "beacon", "lectern",
                "loom", "smithing", "grindstone", "cartography", "brewing", "spawner", "bell");
            case CATEGORY_NETHER_END -> containsAny(path,
                "nether", "crimson", "warped", "soul", "basalt", "blackstone", "quartz", "end_", "chorus", "purpur");
            default -> true;
        };
    }

    @Override
    protected String getRegistryNotReadyMessage() {
        return "Block registry not ready";
    }

    @Override
    protected String getRegistryLoadWarningLog() {
        return "Block registry is not ready for BlockTypeSelectorNode yet.";
    }

    @Override
    protected void onSelectionApplied() {
        updateOutputs();
    }

    public void setSelectedBlock(String blockId) {
        commitSelectedId(blockId);
        onSelectionApplied();
    }

    private void updateOutputs() {
        RegistrySelectionOutputs resolved = resolveSelectionOutputs(selectedBlock);
        outputValues.put(OUTPUT_BLOCK_ID, selectedBlock);
        outputValues.put(OUTPUT_NAMESPACE, resolved.namespace());
        outputValues.put(OUTPUT_BLOCK_PATH, resolved.path());
        outputValues.put(OUTPUT_IS_MODDED, resolved.modded());
        outputValues.put(OUTPUT_VALID_ID, resolved.valid());
        syncOutputPorts();
    }

    public String getSelectedBlock() {
        return selectedBlock;
    }

    public void setAllowModded(boolean allowModded) {
        setAllowModdedFlag(allowModded);
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedBlock", getSelectedBlock(),
            "allowModded", isAllowModded(),
            "selectedCategory", getFilterCategory(),
            "minecraftOnly", isMinecraftOnlyFilter()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("selectedBlock") instanceof String value) {
                commitSelectedId(value);
            }
            restoreFilterState(
                map.get("allowModded") instanceof Boolean b ? b : allowModded,
                map.get("selectedCategory") instanceof String c ? c : CATEGORY_ALL,
                map.get("minecraftOnly") instanceof Boolean m && m
            );
            onSelectionApplied();
        }
    }
}
