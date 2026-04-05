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
    id = "inputs.selectors.block_type_selector",
    displayName = "Block Type Selector",
    description = "Searches and selects a Minecraft block type.",
    category = "inputs.selectors"
)
public class BlockTypeSelectorNode extends BaseCustomUINode {

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

    @NodeProperty(
        displayName = "Selected Block",
        category = "Selection",
        order = 1,
        description = "The currently selected block ID."
    )
    private String selectedBlock = "minecraft:stone";

    @NodeProperty(
        displayName = "Allow Modded Blocks",
        category = "Filter",
        order = 2,
        description = "Whether block IDs outside the minecraft namespace should appear in search results."
    )
    private boolean allowModded = true;

    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_NAMESPACE = "output_namespace";
    private static final String OUTPUT_BLOCK_PATH = "output_block_path";
    private static final String OUTPUT_IS_MODDED = "output_is_modded";

    private transient ImString searchBuffer = new ImString(256);
    private transient List<String> filteredBlocks = new ArrayList<>();
    private transient boolean showDropdown = false;
    private transient String lastSearchText = "";

    private static final int MAX_RESULTS = 20;

    public BlockTypeSelectorNode() {
        super(UUID.randomUUID(), "inputs.selectors.block_type_selector");

        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block ID", "The selected block's full identifier", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_NAMESPACE, "Namespace", "The namespace part of the selected block ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_PATH, "Block Path", "The path part of the selected block ID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_MODDED, "Is Modded", "Whether the selected block is outside the minecraft namespace", NodeDataType.BOOLEAN, this));

        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Searches and selects a Minecraft block type.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutputs();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getTextLineHeight();
        height += getSmallPadding();
        height += ImGui.getFrameHeight();
        height += getSmallPadding();
        if (showDropdown) {
            height += Math.min(filteredBlocks.size(), MAX_RESULTS) * ImGui.getTextLineHeightWithSpacing();
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
                float availableWidth = getAvailableContentWidth(width, zoom);
                layout.addVerticalSpacing(getMediumPadding());

                ImGui.text("Common:");
                for (int i = 0; i < QUICK_BLOCKS.length; i++) {
                    String quickBlock = QUICK_BLOCKS[i];
                    String quickLabel = quickBlock.split(":", 2)[1];
                    if (i > 0 && i % 2 != 0) {
                        ImGui.sameLine();
                    }
                    if (ImGui.smallButton(quickLabel + "##quick_" + i)) {
                        setSelectedBlock(quickBlock);
                        searchBuffer.set("");
                        filteredBlocks.clear();
                        showDropdown = false;
                        lastSearchText = "";
                        changed = true;
                    }
                }

                layout.addVerticalSpacing(getSmallPadding());

                layout.pushFramePadding(4.0f, 3.0f);
                layout.setItemWidth(availableWidth / zoom);

                if (ImGui.inputTextWithHint("##block_search", "Search blocks...", searchBuffer)) {
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

                if (showDropdown && !filteredBlocks.isEmpty()) {
                    ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.18f, 0.95f);

                    int displayCount = Math.min(filteredBlocks.size(), MAX_RESULTS);
                    for (int i = 0; i < displayCount; i++) {
                        String blockId = filteredBlocks.get(i);
                        String blockPath = blockId.contains(":") ? blockId.split(":", 2)[1] : blockId;

                        boolean isSelected = blockId.equals(selectedBlock);
                        if (isSelected) {
                            ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.9f, 0.5f, 1.0f);
                        }

                        if (ImGui.selectable("  " + blockPath + "##" + i, isSelected)) {
                            setSelectedBlock(blockId);
                            searchBuffer.set("");
                            showDropdown = false;
                            lastSearchText = "";
                            changed = true;
                        }

                        if (isSelected) {
                            ImGui.popStyleColor();
                        }

                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(blockId);
                        }
                    }

                    if (filteredBlocks.size() > MAX_RESULTS) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                        ImGui.text("  ... " + (filteredBlocks.size() - MAX_RESULTS) + " more");
                        ImGui.popStyleColor();
                    }

                    ImGui.popStyleColor();
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("BlockTypeSelectorNode UI render failed: " + e.getMessage());
            }

            return changed;
        });
    }

    private void updateFilteredList(String searchText) {
        filteredBlocks.clear();
        if (searchText.isEmpty()) {
            for (String quickBlock : QUICK_BLOCKS) {
                if (allowModded || quickBlock.startsWith("minecraft:")) {
                    filteredBlocks.add(quickBlock);
                }
            }
            invalidateCache();
            return;
        }

        try {
            for (Identifier id : Registries.BLOCK.getIds()) {
                String fullId = id.toString();
                if (!allowModded && !id.getNamespace().equals("minecraft")) {
                    continue;
                }

                if (fullId.contains(searchText) || id.getPath().contains(searchText)) {
                    filteredBlocks.add(fullId);
                    if (filteredBlocks.size() >= MAX_RESULTS * 2) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Registry may not be ready yet.
        }

        invalidateCache();
    }

    public void setSelectedBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            blockId = "minecraft:stone";
        }
        if (!this.selectedBlock.equals(blockId)) {
            this.selectedBlock = blockId;
            updateOutputs();
            markDirty();
        }
    }

    private void updateOutputs() {
        String namespace = "minecraft";
        String path = "stone";
        if (selectedBlock.contains(":")) {
            String[] parts = selectedBlock.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        outputValues.put(OUTPUT_BLOCK_ID, selectedBlock);
        outputValues.put(OUTPUT_NAMESPACE, namespace);
        outputValues.put(OUTPUT_BLOCK_PATH, path);
        outputValues.put(OUTPUT_IS_MODDED, !namespace.equals("minecraft"));
        syncOutputPorts();
    }

    public String getSelectedBlock() {
        return selectedBlock;
    }

    public boolean isAllowModded() {
        return allowModded;
    }

    public void setAllowModded(boolean allowModded) {
        this.allowModded = allowModded;
        if (!allowModded && !selectedBlock.startsWith("minecraft:")) {
            setSelectedBlock("minecraft:stone");
        }
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedBlock", getSelectedBlock(),
            "allowModded", isAllowModded()
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("selectedBlock") instanceof String value) {
                setSelectedBlock(value);
            }
        }
    }
}
