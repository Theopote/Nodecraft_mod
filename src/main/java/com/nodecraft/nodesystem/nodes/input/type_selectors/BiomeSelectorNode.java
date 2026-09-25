package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.type_selectors.biome_selector",
    displayName = "Biome Selector",
    description = "Selects a biome id for biome-aware generation workflows.",
    category = "input.type_selectors",
    order = 3
)
public class BiomeSelectorNode extends AbstractRegistryTypeSelectorNode {

    private static final String CATEGORY_OVERWORLD = "overworld";
    private static final String CATEGORY_NETHER = "nether";
    private static final String CATEGORY_END = "end";
    private static final String CATEGORY_OCEAN = "ocean";

    private static final String[] QUICK_BIOMES = {
        "minecraft:plains",
        "minecraft:forest",
        "minecraft:desert",
        "minecraft:taiga",
        "minecraft:jungle",
        "minecraft:swamp",
        "minecraft:badlands",
        "minecraft:cherry_grove"
    };

    private static final CategorySpec[] CATEGORIES = {
        new CategorySpec(CATEGORY_ALL, "All"),
        new CategorySpec(CATEGORY_OVERWORLD, "Overworld"),
        new CategorySpec(CATEGORY_OCEAN, "Ocean"),
        new CategorySpec(CATEGORY_NETHER, "Nether"),
        new CategorySpec(CATEGORY_END, "End"),
        new CategorySpec(CATEGORY_MODDED, "Modded")
    };

    @NodeProperty(displayName = "Selected Biome", category = "Selection", order = 1,
        description = "The currently selected biome id.")
    private String selectedBiome = "minecraft:plains";

    @NodeProperty(displayName = "Allow Modded Biomes", category = "Filter", order = 2,
        description = "Whether biome ids outside the minecraft namespace should appear in search results.")
    private boolean allowModded = true;

    private static final String OUTPUT_BIOME_ID = "output_biome_id";
    private static final String OUTPUT_BIOME_PATH = "output_biome_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    public BiomeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.biome_selector");
        addOutputPort(new BasePort(OUTPUT_BIOME_ID, "Biome", "Selected biome id", NodeDataType.BIOME, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "Biome namespace", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BIOME_PATH, "Biome Path", "Biome path", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether biome is non-minecraft namespace", NodeDataType.BOOLEAN, this));
        addValidOutputPort();
        onSelectionApplied();
    }

    @Override
    public String getDescription() {
        return "Selects a biome id for biome-aware generation workflows.";
    }

    @Override
    protected String getPickerPopupKey() {
        return "biome_picker";
    }

    @Override
    protected String getPickerTitle() {
        return "Select Biome";
    }

    @Override
    protected String getSearchHint() {
        return "Search biome id...";
    }

    @Override
    protected String getOpenButtonIdSuffix() {
        return "##open_biome_picker";
    }

    @Override
    protected String readSelectedId() {
        return selectedBiome;
    }

    @Override
    protected void writeSelectedId(String canonicalId) {
        this.selectedBiome = canonicalId;
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
        return "minecraft:plains";
    }

    @Override
    protected String[] getQuickPickIds() {
        return QUICK_BIOMES;
    }

    @Override
    protected CategorySpec[] getCategorySpecs() {
        return CATEGORIES;
    }

    @Override
    protected void collectRegistryIds(List<String> target) {
        RegistryCatalogHelper.collectBiomeIds(target);
    }

    @Override
    protected boolean isKnownId(String id) {
        if (catalogContains(id)) {
            return true;
        }
        return RegistryCatalogHelper.isKnownBiomeId(id);
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
            case CATEGORY_NETHER -> containsAny(path, "nether", "soul", "crimson", "warped", "basalt");
            case CATEGORY_END -> path.startsWith("end") || path.contains("end_");
            case CATEGORY_OCEAN -> containsAny(path, "ocean", "beach", "river", "deep");
            case CATEGORY_OVERWORLD -> !matchesCategory(fullId, CATEGORY_NETHER)
                && !matchesCategory(fullId, CATEGORY_END)
                && !matchesCategory(fullId, CATEGORY_OCEAN);
            default -> true;
        };
    }

    @Override
    protected String getRegistryNotReadyMessage() {
        return "Biome registry not ready";
    }

    @Override
    protected String getRegistryLoadWarningLog() {
        return "Biome registry is not ready for BiomeSelectorNode yet.";
    }

    @Override
    protected void onSelectionApplied() {
        updateOutputs();
    }

    public void setSelectedBiome(String biomeId) {
        commitSelectedId(biomeId);
        onSelectionApplied();
    }

    private void updateOutputs() {
        RegistrySelectionOutputs resolved = resolveSelectionOutputs(selectedBiome);
        outputValues.put(OUTPUT_BIOME_ID, selectedBiome);
        outputValues.put(OUTPUT_NAMESPACE, resolved.namespace());
        outputValues.put(OUTPUT_BIOME_PATH, resolved.path());
        outputValues.put(OUTPUT_IS_MODDED, resolved.modded());
        outputValues.put(OUTPUT_VALID_ID, resolved.valid());
        syncOutputPorts();
    }

    public String getSelectedBiome() {
        return selectedBiome;
    }

    public void setAllowModded(boolean allowModded) {
        setAllowModdedFlag(allowModded);
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedBiome", getSelectedBiome(),
            "allowModded", isAllowModded(),
            "selectedCategory", getFilterCategory(),
            "minecraftOnly", isMinecraftOnlyFilter()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("selectedBiome") instanceof String value) {
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
