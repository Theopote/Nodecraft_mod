package com.nodecraft.nodesystem.nodes.inputs.selectors;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.selectors.item_type_selector",
    displayName = "Item Type Selector",
    description = "Searches and selects a Minecraft item type.",
    category = "inputs.selectors"
)
public class ItemTypeSelectorNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Selected Item",
        category = "Selection",
        order = 1,
        description = "The currently selected item ID."
    )
    private String selectedItem = "minecraft:diamond";

    @NodeProperty(
        displayName = "Allow Modded Items",
        category = "Filter",
        order = 2,
        description = "Whether item IDs outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_ITEM_ID = "output_item_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_ITEM_PATH = "output_item_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";

    private transient ImString searchBuffer = new ImString(256);
    private transient volatile List<String> filteredItems = new ArrayList<>();
    private transient volatile boolean showDropdown = false;
    private transient volatile String lastSearchText = "";

    private static final int MAX_RESULTS = 20;

    public ItemTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.item_type_selector");

        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item ID", "The selected item's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected item ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_PATH, "Item Path", "The path part of the selected item ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected item is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "A simple derived category for the selected item", NodeDataType.STRING, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft item type.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutputs();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredItems.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 184f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;

            try {
                float edgeMargin = toPixels(getSmallPadding(), zoom);
                float availableWidth = Math.max(0.0f, toPixelsExact(width, zoom) - edgeMargin * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();
                layout.addVerticalSpacing(getMediumPadding());

                layout.pushFramePadding(4.0f, 3.0f);
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                layout.setItemWidth(availableWidth / zoom);

                if (ImGui.inputTextWithHint("##item_search", "Search items...", searchBuffer)) {
                    String searchText = searchBuffer.get().trim().toLowerCase();
                    if (!searchText.equals(lastSearchText)) {
                        lastSearchText = searchText;
                        updateFilteredList(searchText);
                        showDropdown = !searchText.isEmpty();
                    }
                }
                if (ImGui.isItemActivated() && !searchBuffer.get().isEmpty()) {
                    showDropdown = true;
                }

                layout.popItemWidth();
                layout.popStyleVar();

                layout.addVerticalSpacing(getSmallPadding());

                List<String> filteredSnapshot = filteredItems;
                if (showDropdown && !filteredSnapshot.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);

                    int displayCount = Math.min(filteredSnapshot.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String itemId = filteredSnapshot.get(i);
                        String itemPath = itemId.contains(":") ? itemId.split(":", 2)[1] : itemId;
                        boolean isSelected = itemId.equals(selectedItem);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.8f, 1.0f, 1.0f);
                        }

                        if (ImGui.selectable("  " + itemPath + "##" + i, isSelected)) {
                            setSelectedItem(itemId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }

                        if (isSelected) {
                            ImGui.popStyleColor();
                        }

                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(itemId);
                        }
                    }

                    if (filteredSnapshot.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... " + (filteredSnapshot.size() - MAX_RESULTS) + " more");
                        ImGui.popStyleColor();
                    }

                    ImGui.popStyleColor();
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("ItemTypeSelectorNode UI render failed: " + e.getMessage());
            }
            return changed;
        });
    }

    private void updateFilteredList(String searchText) {
        List<String> nextFilteredItems = new ArrayList<>();
        if (searchText.isEmpty()) {
            filteredItems = nextFilteredItems;
            invalidateCache();
            return;
        }
        try {
            for (Identifier id : Registries.ITEM.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) {
                    continue;
                }
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    nextFilteredItems.add(fullId);
                    if (nextFilteredItems.size() >= MAX_RESULTS * 2) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Registry may not be ready yet.
        }
        filteredItems = nextFilteredItems;
        invalidateCache();
    }

    private String calculateItemCategory() {
        String path = selectedItem.contains(":") ? selectedItem.split(":", 2)[1] : selectedItem;
        if (path.contains("sword") || path.contains("bow") || path.contains("arrow") || path.equals("trident")) {
            return "weapon";
        }
        if (path.contains("pickaxe") || path.contains("shovel") || path.contains("hoe") || path.equals("shears")) {
            return "tool";
        }
        if (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots")) {
            return "armor";
        }
        if (path.contains("apple") || path.contains("bread") || path.contains("beef") || path.contains("porkchop") || path.contains("stew")) {
            return "food";
        }
        if (path.contains("ore") || path.equals("diamond") || path.equals("gold_ingot") || path.equals("iron_ingot")) {
            return "resource";
        }
        return "misc";
    }

    public void setSelectedItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            itemId = "minecraft:diamond";
        }
        if (!this.selectedItem.equals(itemId)) {
            this.selectedItem = itemId;
            updateOutputs();
            markDirty();
        }
    }

    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "diamond";
        if (selectedItem.contains(":")) {
            String[] parts = selectedItem.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_ITEM_ID, selectedItem);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_ITEM_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, calculateItemCategory());
    }

    public String getSelectedItem() {
        return selectedItem;
    }

    public boolean isAllowModded() {
        return allowModded;
    }

    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedItem.startsWith("minecraft:")) {
            setSelectedItem("minecraft:diamond");
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedItem", getSelectedItem(),
            "allowModded", isAllowModded()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("selectedItem") instanceof String value) {
                setSelectedItem(value);
            }
        }
    }
}
