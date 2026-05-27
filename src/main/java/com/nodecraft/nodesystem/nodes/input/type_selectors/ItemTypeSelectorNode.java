package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.type_selectors.item_type_selector",
    displayName = "Item Type Selector",
    description = "Searches and selects a Minecraft item type.",
    category = "input.type_selectors",
    order = 2
)
public class ItemTypeSelectorNode extends AbstractRegistryTypeSelectorNode {

    private static final String CATEGORY_BLOCKS = "blocks";
    private static final String CATEGORY_TOOLS = "tools";
    private static final String CATEGORY_FOOD = "food";
    private static final String CATEGORY_COMBAT = "combat";

    private static final String[] QUICK_ITEMS = {
        "minecraft:stone",
        "minecraft:oak_planks",
        "minecraft:glass",
        "minecraft:torch",
        "minecraft:bucket",
        "minecraft:water_bucket",
        "minecraft:minecart",
        "minecraft:armor_stand"
    };

    private static final CategorySpec[] CATEGORIES = {
        new CategorySpec(CATEGORY_ALL, "All"),
        new CategorySpec(CATEGORY_BLOCKS, "Blocks"),
        new CategorySpec(CATEGORY_TOOLS, "Tools"),
        new CategorySpec(CATEGORY_FOOD, "Food"),
        new CategorySpec(CATEGORY_COMBAT, "Combat"),
        new CategorySpec(CATEGORY_MODDED, "Modded")
    };

    @NodeProperty(
        displayName = "Selected Item",
        category = "Selection",
        order = 1,
        description = "The currently selected item id."
    )
    private String selectedItem = "minecraft:stone";

    @NodeProperty(
        displayName = "Allow Modded Items",
        category = "Filter",
        order = 2,
        description = "Whether item ids outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_ITEM_ID = "output_item_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_ITEM_PATH = "output_item_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    public ItemTypeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.item_type_selector");

        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item Type", "The selected item's full identifier", NodeDataType.ITEM_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected item id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_PATH, "Item Path", "The path part of the selected item id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected item is outside the minecraft namespace", NodeDataType.BOOLEAN, this));

        onSelectionApplied();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft item type.";
    }

    @Override
    protected String getPickerPopupKey() {
        return "item_picker";
    }

    @Override
    protected String getPickerTitle() {
        return "Select Item";
    }

    @Override
    protected String getSearchHint() {
        return "Search item id...";
    }

    @Override
    protected String getOpenButtonIdSuffix() {
        return "##open_item_picker";
    }

    @Override
    protected String readSelectedId() {
        return selectedItem;
    }

    @Override
    protected boolean isAllowModded() {
        return allowModded;
    }

    @Override
    protected void setAllowModdedFlag(boolean allowModded) {
        this.allowModded = allowModded;
        normalizeFilterState();
        if (!allowModded && !selectedItem.startsWith("minecraft:")) {
            setSelectedItem(getDefaultId());
        }
        updateFilteredListFromSearch();
    }

    @Override
    protected String getDefaultId() {
        return "minecraft:stone";
    }

    @Override
    protected String[] getQuickPickIds() {
        return QUICK_ITEMS;
    }

    @Override
    protected CategorySpec[] getCategorySpecs() {
        return CATEGORIES;
    }

    @Override
    protected void collectRegistryIds(List<String> target) {
        for (Identifier id : Registries.ITEM.getIds()) {
            target.add(id.toString());
        }
    }

    @Override
    protected boolean isKnownId(String id) {
        if (getDefaultId().equals(id)) {
            return true;
        }
        if (catalogContains(id)) {
            return true;
        }
        try {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                return false;
            }
            if (Registries.ITEM.getIds().isEmpty()) {
                return false;
            }
            return Registries.ITEM.containsId(parsed);
        } catch (Exception ignored) {
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
            case CATEGORY_TOOLS -> containsAny(path,
                "pickaxe", "axe", "shovel", "hoe", "shears", "flint_and_steel", "bucket", "fishing_rod",
                "brush", "compass", "clock", "spyglass", "lead", "name_tag", "saddle");
            case CATEGORY_FOOD -> containsAny(path,
                "apple", "bread", "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon",
                "potato", "carrot", "beetroot", "cookie", "melon", "berry", "honey", "soup", "stew",
                "cake", "pie", "milk", "egg", "kelp", "dried", "sweet", "chorus", "spider_eye", "pufferfish");
            case CATEGORY_COMBAT -> containsAny(path,
                "sword", "bow", "crossbow", "trident", "shield", "arrow", "helmet", "chestplate",
                "leggings", "boots", "tnt", "firework", "spectral", "totem", "elytra");
            case CATEGORY_BLOCKS -> {
                if (matchesCategory(fullId, CATEGORY_TOOLS)
                    || matchesCategory(fullId, CATEGORY_FOOD)
                    || matchesCategory(fullId, CATEGORY_COMBAT)) {
                    yield false;
                }
                yield containsAny(path,
                    "stone", "planks", "log", "glass", "brick", "concrete", "wool", "terracotta",
                    "sand", "dirt", "grass", "leaves", "slab", "stairs", "fence", "door", "trapdoor",
                    "ore", "deepslate", "copper", "torch", "lantern", "chest", "barrel");
            }
            default -> true;
        };
    }

    @Override
    protected String getRegistryNotReadyMessage() {
        return "Item registry not ready";
    }

    @Override
    protected String getRegistryLoadWarningLog() {
        return "Item registry is not ready for ItemTypeSelectorNode yet.";
    }

    @Override
    protected void onSelectionApplied() {
        updateOutputs();
    }

    public void setSelectedItem(String itemId) {
        applyValidatedId(itemId, getDefaultId());
    }

    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "stone";
        if (selectedItem.contains(":")) {
            String[] parts = selectedItem.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_ITEM_ID, selectedItem);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_ITEM_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        syncOutputPorts();
    }

    @Override
    protected void applySelectedId(String id) {
        String nextId = sanitizeNamespacedId(id, getDefaultId());
        if (!allowModded && !nextId.startsWith("minecraft:")) {
            nextId = getDefaultId();
        }
        if (!isKnownId(nextId)) {
            nextId = getDefaultId();
        }
        if (!selectedItem.equals(nextId)) {
            selectedItem = nextId;
            updateOutputs();
            markDirty();
        }
    }

    public String getSelectedItem() {
        return selectedItem;
    }

    public void setAllowModded(boolean allowModded) {
        setAllowModdedFlag(allowModded);
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedItem", getSelectedItem(),
            "allowModded", isAllowModded(),
            "selectedCategory", getFilterCategory(),
            "minecraftOnly", isMinecraftOnlyFilter()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("selectedItem") instanceof String value) {
                setSelectedItem(value);
            }
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            restoreFilterState(
                map.get("allowModded") instanceof Boolean b ? b : allowModded,
                map.get("selectedCategory") instanceof String c ? c : CATEGORY_ALL,
                map.get("minecraftOnly") instanceof Boolean m && m
            );
        }
    }
}
