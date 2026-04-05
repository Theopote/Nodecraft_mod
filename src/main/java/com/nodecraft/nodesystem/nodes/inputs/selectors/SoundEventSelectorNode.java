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
    id = "inputs.selectors.sound_event_selector",
    displayName = "Sound Event Selector",
    description = "Searches and selects a Minecraft sound event.",
    category = "inputs.selectors"
)
public class SoundEventSelectorNode extends BaseCustomUINode {

    @NodeProperty(
        displayName = "Selected Sound",
        category = "Selection",
        order = 1,
        description = "The currently selected sound event ID."
    )
    private String selectedSound = "minecraft:entity.player.levelup";

    @NodeProperty(
        displayName = "Allow Modded Sounds",
        category = "Filter",
        order = 2,
        description = "Whether sound IDs outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_SOUND_ID = "output_sound_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_SOUND_PATH = "output_sound_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";
    private static final String OUTPUT_CATEGORY = "output_category";

    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredSounds = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";

    private static final int MAX_RESULTS = 20;

    public SoundEventSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.sound_event_selector");

        addOutputPort(new BasePort(OUTPUT_SOUND_ID, "Sound ID", "The selected sound's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected sound ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SOUND_PATH, "Sound Path", "The path part of the selected sound ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected sound is outside the minecraft namespace", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CATEGORY, "Category", "A simple derived category for the selected sound", NodeDataType.STRING, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft sound event.";
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
            height += Math.min(filteredSounds.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
            height += getSmallPadding();
        }
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 176f;
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

                if (ImGui.inputTextWithHint("##sound_search", "Search sounds...", searchBuffer)) {
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

                layout.addVerticalSpacing(0.5f);

                if (showDropdown && !filteredSounds.isEmpty()) {
                    int displayCount = Math.min(filteredSounds.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String soundId = filteredSounds.get(i);
                        String soundPath = soundId.contains(":") ? soundId.split(":", 2)[1] : soundId;
                        boolean isSelected = soundId.equals(selectedSound);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, 0.8f, 0.6f, 1.0f, 1.0f);
                        }

                        if (ImGui.selectable("  * " + soundPath + "##" + i, isSelected)) {
                            setSelectedSound(soundId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }

                        if (isSelected) {
                            ImGui.popStyleColor();
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(soundId);
                        }
                    }
                    if (filteredSounds.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... " + (filteredSounds.size() - MAX_RESULTS) + " more");
                        ImGui.popStyleColor();
                    }
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("SoundEventSelectorNode UI render failed: " + e.getMessage());
            }
            return changed;
        });
    }

    private void updateFilteredList(String searchText) {
        filteredSounds.clear();
        if (searchText.isEmpty()) {
            invalidateCache();
            return;
        }
        try {
            for (Identifier id : Registries.SOUND_EVENT.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) {
                    continue;
                }
                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredSounds.add(fullId);
                    if (filteredSounds.size() >= MAX_RESULTS * 2) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Registry may not be ready yet.
        }
        invalidateCache();
    }

    private String getSoundCategory() {
        String path = selectedSound.contains(":") ? selectedSound.split(":", 2)[1] : selectedSound;
        if (path.isEmpty()) {
            return "unknown";
        }
        String[] pathParts = path.split("\\.", 2);
        if (pathParts.length > 0) {
            String first = pathParts[0];
            return switch (first) {
                case "block", "blocks" -> "block";
                case "entity", "entities", "mob", "mobs" -> "entity";
                case "item", "items" -> "item";
                case "music" -> "music";
                case "ambient" -> "ambient";
                case "weather" -> "weather";
                case "player" -> "player";
                case "ui" -> "ui";
                default -> first;
            };
        }
        return "unknown";
    }

    public void setSelectedSound(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            soundId = "minecraft:entity.player.levelup";
        }
        if (!this.selectedSound.equals(soundId)) {
            this.selectedSound = soundId;
            updateOutputs();
            markDirty();
        }
    }

    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "entity.player.levelup";
        if (selectedSound.contains(":")) {
            String[] parts = selectedSound.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_SOUND_ID, selectedSound);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_SOUND_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        outputValues.put(OUTPUT_CATEGORY, getSoundCategory());
    }

    public String getSelectedSound() {
        return selectedSound;
    }

    public boolean isAllowModded() {
        return allowModded;
    }

    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedSound.startsWith("minecraft:")) {
            setSelectedSound("minecraft:entity.player.levelup");
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedSound", getSelectedSound(),
            "allowModded", isAllowModded()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("selectedSound") instanceof String value) {
                setSelectedSound(value);
            }
        }
    }
}
