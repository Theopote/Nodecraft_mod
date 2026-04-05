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
    id = "inputs.selectors.entity_type_selector",
    displayName = "Entity Type Selector",
    description = "Searches and selects a Minecraft entity type.",
    category = "inputs.selectors"
)
public class EntityTypeSelectorNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Selected Entity",
        category = "Selection",
        order = 1,
        description = "The currently selected entity ID."
    )
    private String selectedEntity = "minecraft:pig";

    @NodeProperty(
        displayName = "Allow Modded Entities",
        category = "Filter",
        order = 2,
        description = "Whether entity IDs outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_ENTITY_ID = "output_entity_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_ENTITY_PATH = "output_entity_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";

    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredEntities = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";

    private static final int MAX_RESULTS = 20;

    public EntityTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.entity_type_selector");

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity ID", "The selected entity's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected entity ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_PATH, "Entity Path", "The path part of the selected entity ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected entity is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "A simple derived category for the selected entity", NodeDataType.STRING, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft entity type.";
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
            height += Math.min(filteredEntities.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
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

                if (ImGui.inputTextWithHint("##entity_search", "Search entities...", searchBuffer)) {
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

                if (showDropdown && !filteredEntities.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);
                    int displayCount = Math.min(filteredEntities.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String entityId = filteredEntities.get(i);
                        String entityPath = entityId.contains(":") ? entityId.split(":", 2)[1] : entityId;
                        boolean isSelected = entityId.equals(selectedEntity);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.7f, 0.3f, 1.0f);
                        }

                        if (ImGui.selectable("  " + entityPath + "##" + i, isSelected)) {
                            setSelectedEntity(entityId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }

                        if (isSelected) {
                            ImGui.popStyleColor();
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(entityId);
                        }
                    }

                    if (filteredEntities.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... " + (filteredEntities.size() - MAX_RESULTS) + " more");
                        ImGui.popStyleColor();
                    }
                    ImGui.popStyleColor();
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("EntityTypeSelectorNode UI render failed: " + e.getMessage());
            }
            return changed;
        });
    }

    private void updateFilteredList(String searchText) {
        filteredEntities.clear();
        if (searchText.isEmpty()) {
            invalidateCache();
            return;
        }
        try {
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) {
                    continue;
                }
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredEntities.add(fullId);
                    if (filteredEntities.size() >= MAX_RESULTS * 2) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Registry may not be ready yet.
        }
        invalidateCache();
    }

    private String calculateEntityCategory() {
        String path = selectedEntity.contains(":") ? selectedEntity.split(":", 2)[1] : selectedEntity;
        return switch (path) {
            case "zombie", "skeleton", "creeper", "spider", "enderman" -> "hostile";
            case "pig", "cow", "sheep", "chicken", "rabbit" -> "passive";
            case "wolf", "bee", "dolphin", "panda", "llama" -> "neutral";
            case "ender_dragon", "wither" -> "boss";
            case "villager", "wandering_trader" -> "npc";
            default -> "unknown";
        };
    }

    public void setSelectedEntity(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            entityId = "minecraft:pig";
        }
        if (!this.selectedEntity.equals(entityId)) {
            this.selectedEntity = entityId;
            updateOutputs();
            markDirty();
        }
    }

    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "pig";
        if (selectedEntity.contains(":")) {
            String[] parts = selectedEntity.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_ENTITY_ID, selectedEntity);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_ENTITY_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, calculateEntityCategory());
    }

    public String getSelectedEntity() {
        return selectedEntity;
    }

    public boolean isAllowModded() {
        return allowModded;
    }

    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedEntity.startsWith("minecraft:")) {
            setSelectedEntity("minecraft:pig");
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedEntity", getSelectedEntity(),
            "allowModded", isAllowModded()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("selectedEntity") instanceof String value) {
                setSelectedEntity(value);
            }
        }
    }
}
