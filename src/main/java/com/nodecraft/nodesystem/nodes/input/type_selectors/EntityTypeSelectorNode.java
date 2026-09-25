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
    id = "input.type_selectors.entity_type_selector",
    displayName = "Entity Type Selector",
    description = "Searches and selects a Minecraft entity type.",
    category = "input.type_selectors",
    order = 1
)
public class EntityTypeSelectorNode extends AbstractRegistryTypeSelectorNode {

    private static final String CATEGORY_HOSTILE = "hostile";
    private static final String CATEGORY_PASSIVE = "passive";
    private static final String CATEGORY_BOSS = "boss";
    private static final String CATEGORY_UTILITY = "utility";

    private static final String[] QUICK_ENTITIES = {
        "minecraft:pig",
        "minecraft:villager",
        "minecraft:armor_stand",
        "minecraft:item_frame",
        "minecraft:boat",
        "minecraft:minecart",
        "minecraft:zombie",
        "minecraft:cow"
    };

    private static final CategorySpec[] CATEGORIES = {
        new CategorySpec(CATEGORY_ALL, "All"),
        new CategorySpec(CATEGORY_HOSTILE, "Hostile"),
        new CategorySpec(CATEGORY_PASSIVE, "Passive"),
        new CategorySpec(CATEGORY_BOSS, "Boss"),
        new CategorySpec(CATEGORY_UTILITY, "Utility"),
        new CategorySpec(CATEGORY_MODDED, "Modded")
    };

    @NodeProperty(
        displayName = "Selected Entity",
        category = "Selection",
        order = 1,
        description = "The currently selected entity type id."
    )
    private String selectedEntity = "minecraft:pig";

    @NodeProperty(
        displayName = "Allow Modded Entities",
        category = "Filter",
        order = 2,
        description = "Whether entity ids outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_ENTITY_ID = "output_entity_id";
    private static final String OUTPUT_ENTITY_PATH = "output_entity_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    public EntityTypeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.entity_type_selector");

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity Type", "The selected entity's full identifier", NodeDataType.ENTITY_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected entity id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_PATH, "Entity Path", "The path part of the selected entity id", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected entity is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addValidOutputPort();

        onSelectionApplied();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft entity type.";
    }

    @Override
    protected String getPickerPopupKey() {
        return "entity_picker";
    }

    @Override
    protected String getPickerTitle() {
        return "Select Entity";
    }

    @Override
    protected String getSearchHint() {
        return "Search entity id...";
    }

    @Override
    protected String getOpenButtonIdSuffix() {
        return "##open_entity_picker";
    }

    @Override
    protected String readSelectedId() {
        return selectedEntity;
    }

    @Override
    protected void writeSelectedId(String canonicalId) {
        this.selectedEntity = canonicalId;
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
        return "minecraft:pig";
    }

    @Override
    protected String[] getQuickPickIds() {
        return QUICK_ENTITIES;
    }

    @Override
    protected CategorySpec[] getCategorySpecs() {
        return CATEGORIES;
    }

    @Override
    protected void collectRegistryIds(List<String> target) {
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
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
            if (Registries.ENTITY_TYPE.getIds().isEmpty()) {
                return false;
            }
            return Registries.ENTITY_TYPE.containsId(parsed);
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
            case CATEGORY_BOSS -> containsAny(path, "wither", "ender_dragon", "warden");
            case CATEGORY_HOSTILE -> containsAny(path,
                "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "phantom", "slime",
                "blaze", "ghast", "piglin", "hoglin", "guardian", "shulker", "silverfish", "pillager",
                "vindicator", "evoker", "ravager", "vex", "bogged", "breeze", "creaking");
            case CATEGORY_PASSIVE -> containsAny(path,
                "pig", "cow", "sheep", "chicken", "rabbit", "horse", "donkey", "mule", "llama",
                "cat", "wolf", "parrot", "bee", "squid", "dolphin", "turtle", "panda", "fox", "goat",
                "axolotl", "frog", "camel", "sniffer", "villager", "iron_golem", "snow_golem", "allay",
                "mooshroom", "ocelot", "polar_bear", "strider", "glow_squid", "tadpole", "cod", "salmon");
            case CATEGORY_UTILITY -> containsAny(path,
                "armor_stand", "item_frame", "painting", "boat", "minecart", "marker", "display",
                "falling_block", "tnt", "lightning_bolt", "experience_orb", "egg", "arrow", "firework",
                "leash_knot", "end_crystal", "eye_of_ender", "fishing_bobber");
            default -> true;
        };
    }

    @Override
    protected String getRegistryNotReadyMessage() {
        return "Entity registry not ready";
    }

    @Override
    protected String getRegistryLoadWarningLog() {
        return "Entity registry is not ready for EntityTypeSelectorNode yet.";
    }

    @Override
    protected void onSelectionApplied() {
        updateOutputs();
    }

    public void setSelectedEntity(String entityId) {
        commitSelectedId(entityId);
        onSelectionApplied();
    }

    private void updateOutputs() {
        RegistrySelectionOutputs resolved = resolveSelectionOutputs(selectedEntity);
        outputValues.put(OUTPUT_ENTITY_ID, selectedEntity);
        outputValues.put(OUTPUT_NAMESPACE, resolved.namespace());
        outputValues.put(OUTPUT_ENTITY_PATH, resolved.path());
        outputValues.put(OUTPUT_IS_MODDED, resolved.modded());
        outputValues.put(OUTPUT_VALID_ID, resolved.valid());
        syncOutputPorts();
    }

    public String getSelectedEntity() {
        return selectedEntity;
    }

    public void setAllowModded(boolean allowModded) {
        setAllowModdedFlag(allowModded);
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedEntity", getSelectedEntity(),
            "allowModded", isAllowModded(),
            "selectedCategory", getFilterCategory(),
            "minecraftOnly", isMinecraftOnlyFilter()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("selectedEntity") instanceof String value) {
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
