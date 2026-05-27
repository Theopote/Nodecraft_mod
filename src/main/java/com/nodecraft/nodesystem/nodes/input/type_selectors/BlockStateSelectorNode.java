package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "input.type_selectors.block_state_selector",
    displayName = "Block State Selector",
    description = "Builds block-state key/value data from a compact properties string.",
    category = "input.type_selectors",
    order = 4
)
public class BlockStateSelectorNode extends BaseCustomUINode {

    private static final String BLOCK_PICKER_POPUP_KEY = "block_state_block_picker";
    private static final int POPUP_PAGE_SIZE = 18;
    private static final float POPUP_MIN_WIDTH = 360.0f;
    private static final float POPUP_MIN_HEIGHT = 400.0f;
    private static final float POPUP_FIXED_HEIGHT = 480.0f;
    private static final String OPEN_BUTTON_PREFIX = "[";
    private static final String OPEN_BUTTON_SUFFIX = " v]";
    private static final String ELLIPSIS = "...";

    private static final String OUTPUT_BLOCK_ID = "output_block_id";
    private static final String OUTPUT_BLOCK_STATE = "output_block_state";
    private static final String OUTPUT_HAS_PROPERTIES = "output_has_properties";

    @NodeProperty(displayName = "Block ID", category = "Selection", order = 1)
    private String blockId = "minecraft:stone";

    @NodeProperty(displayName = "State Properties", category = "Selection", order = 2,
        description = "Comma separated key=value pairs, e.g. facing=north,waterlogged=false")
    private String stateProperties = "";

    private transient ImString searchBuffer = new ImString(256);
    private transient ImString propertiesBuffer = new ImString(512);
    private transient volatile List<String> allBlockIds = new ArrayList<>();
    private transient volatile List<String> filteredBlockIds = new ArrayList<>();
    private transient volatile int currentPage = 0;
    private transient volatile boolean blockRegistryReady = true;

    public BlockStateSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.block_state_selector");
        addOutputPort(new BasePort(OUTPUT_BLOCK_ID, "Block Type", "Selected block type id", NodeDataType.BLOCK_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE, "Block State", "Parsed block state data", NodeDataType.BLOCK_STATE_DATA, this));
        addOutputPort(new BasePort(OUTPUT_HAS_PROPERTIES, "Has Properties", "True when state map has at least one key", NodeDataType.BOOLEAN, this));
        syncPropertiesBuffer();
        updateOutputs();
    }

    @Override
    public String getDescription() {
        return "Builds block-state key/value data from a compact properties string.";
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
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layoutHelper -> {
            boolean changed = false;
            try {
                float edgeMargin = layoutHelper.toPixels(getSmallPadding());
                float availableWidth = Math.max(0.0f, layoutHelper.toPixelsExact(width) - edgeMargin * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();

                layoutHelper.addVerticalSpacing(getMediumPadding());
                ImGui.setCursorPosX(baseCursorX + edgeMargin);

                layoutHelper.pushFramePadding(4.0f, 3.0f);
                try {
                    String compactLabel = buildCompactLabel(availableWidth, normalizeBlockId(blockId));
                    if (ImGui.button(compactLabel + "##open_block_state_picker", availableWidth, 0)) {
                        ensureBlockCatalogReady();
                        updateFilteredBlocks(getSearchBuffer().get());
                        openScopedPopup(BLOCK_PICKER_POPUP_KEY, "Select Block");
                    }
                } finally {
                    layoutHelper.popStyleVar();
                }

                if (renderBlockPickerPopup()) {
                    changed = true;
                }

                layoutHelper.addVerticalSpacing(getSmallPadding());
                ImGui.setCursorPosX(baseCursorX + edgeMargin);
                ImGui.text("State:");
                ImGui.sameLine();
                layoutHelper.setItemWidth((availableWidth - ImGui.calcTextSize("State:").x
                    - ImGui.getStyle().getItemSpacingX()) / Math.max(zoom, 0.001f));
                ImString props = getPropertiesBuffer();
                if (ImGui.inputTextWithHint("##block_state_props", "facing=north,waterlogged=false", props,
                    ImGuiInputTextFlags.None)) {
                    setStateProperties(props.get());
                    changed = true;
                }
                layoutHelper.popItemWidth();

                layoutHelper.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("BlockStateSelectorNode UI render failed: {}", e.getMessage());
            }
            return changed;
        });
    }

    private boolean renderBlockPickerPopup() {
        boolean changed = false;
        imgui.ImGui imguiInstance = new imgui.ImGui();

        ImGui.setNextWindowSizeConstraints(POPUP_MIN_WIDTH, POPUP_MIN_HEIGHT, 4096.0f, 4096.0f);
        ImGui.setNextWindowSize(POPUP_MIN_WIDTH, POPUP_FIXED_HEIGHT, imgui.flag.ImGuiCond.Appearing);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 5.0f, 3.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 3.0f);
        try {
            imguiInstance.setWindowFontScale(1.0f);
            int popupFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
            if (!beginScopedPopupModal(BLOCK_PICKER_POPUP_KEY, "Select Block", popupFlags)) {
                return false;
            }
            try {
                imguiInstance.setWindowFontScale(1.0f);
                ImGui.pushItemWidth(-1.0f);
                ImString buffer = getSearchBuffer();
                if (ImGui.inputTextWithHint("##block_state_search", "Search block id...", buffer, ImGuiInputTextFlags.None)) {
                    updateFilteredBlocks(buffer.get());
                }
                ImGui.popItemWidth();

                List<String> snapshot = filteredBlockIds;
                int total = snapshot.size();
                int totalPages = Math.max(1, (int) Math.ceil(total / (float) POPUP_PAGE_SIZE));
                if (currentPage >= totalPages) {
                    currentPage = totalPages - 1;
                }
                int start = currentPage * POPUP_PAGE_SIZE;
                int end = Math.min(start + POPUP_PAGE_SIZE, total);

                ImGui.text(String.format("Results: %d", total));
                float footerReserve = ImGui.getFrameHeightWithSpacing();
                float listHeight = Math.max(160.0f, ImGui.getContentRegionAvail().y - footerReserve);
                ImGui.beginChild("##block_state_picker_list", ImGui.getContentRegionAvail().x, listHeight, false,
                    ImGuiWindowFlags.AlwaysVerticalScrollbar);
                try {
                    String selected = normalizeBlockId(blockId);
                    if (snapshot.isEmpty()) {
                        ImGui.textDisabled(blockRegistryReady ? "No blocks found" : "Block registry not ready");
                    } else {
                        for (int i = start; i < end; i++) {
                            String entryId = snapshot.get(i);
                            if (ImGui.selectable(entryId + "##block_state_" + i, entryId.equals(selected))) {
                                setBlockId(entryId);
                                changed = true;
                            }
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip(entryId);
                            }
                        }
                    }
                } finally {
                    ImGui.endChild();
                }

                boolean canPrev = currentPage > 0;
                boolean canNext = currentPage + 1 < totalPages;
                if (!canPrev) {
                    ImGui.beginDisabled();
                }
                if (ImGui.button("< Prev##block_state_page_prev")) {
                    currentPage--;
                }
                if (!canPrev) {
                    ImGui.endDisabled();
                }
                ImGui.sameLine();
                ImGui.text(String.format("Page %d / %d", currentPage + 1, totalPages));
                ImGui.sameLine();
                if (!canNext) {
                    ImGui.beginDisabled();
                }
                if (ImGui.button("Next >##block_state_page_next")) {
                    currentPage++;
                }
                if (!canNext) {
                    ImGui.endDisabled();
                }
                ImGui.sameLine();
                if (ImGui.button("Close##close_block_state_picker")) {
                    ImGui.closeCurrentPopup();
                }
            } finally {
                imguiInstance.setWindowFontScale(1.0f);
                endScopedPopup();
            }
        } finally {
            ImGui.popStyleVar(2);
        }
        return changed;
    }

    private ImString getSearchBuffer() {
        if (searchBuffer == null) {
            searchBuffer = new ImString(256);
        }
        return searchBuffer;
    }

    private ImString getPropertiesBuffer() {
        if (propertiesBuffer == null) {
            propertiesBuffer = new ImString(512);
        }
        return propertiesBuffer;
    }

    private void syncPropertiesBuffer() {
        getPropertiesBuffer().set(stateProperties == null ? "" : stateProperties);
    }

    private void ensureBlockCatalogReady() {
        if (!allBlockIds.isEmpty()) {
            blockRegistryReady = true;
            return;
        }
        List<String> collected = new ArrayList<>();
        try {
            for (Identifier id : Registries.BLOCK.getIds()) {
                collected.add(id.toString());
            }
            collected.sort(Comparator.naturalOrder());
            blockRegistryReady = !collected.isEmpty();
        } catch (Exception ignored) {
            blockRegistryReady = false;
            NodeCraft.LOGGER.warn("Block registry is not ready for BlockStateSelectorNode yet.");
        }
        allBlockIds = collected;
    }

    private void updateFilteredBlocks(String searchTextRaw) {
        ensureBlockCatalogReady();
        String searchText = searchTextRaw == null ? "" : searchTextRaw.trim().toLowerCase(Locale.ROOT);
        Set<String> next = new LinkedHashSet<>();
        for (String fullId : allBlockIds) {
            if (searchText.isEmpty() || fullId.toLowerCase(Locale.ROOT).contains(searchText)) {
                next.add(fullId);
            }
        }
        currentPage = 0;
        filteredBlockIds = new ArrayList<>(next);
    }

    private static String buildCompactLabel(float buttonWidthPx, String selectedId) {
        String content = selectedId;
        String fullLabel = OPEN_BUTTON_PREFIX + content + OPEN_BUTTON_SUFFIX;
        float stylePadX = ImGui.getStyle().getFramePaddingX() * 2.0f;
        float safeContentWidth = Math.max(48.0f, buttonWidthPx - stylePadX);
        if (ImGui.calcTextSize(fullLabel).x <= safeContentWidth) {
            return fullLabel;
        }
        String fixedParts = OPEN_BUTTON_PREFIX + ELLIPSIS + OPEN_BUTTON_SUFFIX;
        if (ImGui.calcTextSize(fixedParts).x >= safeContentWidth) {
            return fixedParts;
        }
        int best = 0;
        for (int mid = 1; mid <= content.length(); mid++) {
            String candidate = OPEN_BUTTON_PREFIX + content.substring(0, mid) + ELLIPSIS + OPEN_BUTTON_SUFFIX;
            if (ImGui.calcTextSize(candidate).x <= safeContentWidth) {
                best = mid;
            }
        }
        return best <= 0 ? fixedParts : OPEN_BUTTON_PREFIX + content.substring(0, best) + ELLIPSIS + OPEN_BUTTON_SUFFIX;
    }

    public void setBlockId(String value) {
        String next = normalizeBlockId(value);
        if (!blockId.equals(next)) {
            blockId = next;
            updateOutputs();
            markDirty();
        }
    }

    public void setStateProperties(String value) {
        String next = value == null ? "" : value;
        if (!stateProperties.equals(next)) {
            stateProperties = next;
            syncPropertiesBuffer();
            updateOutputs();
            markDirty();
        }
    }

    private void updateOutputs() {
        BlockStateData stateData = parseStateProperties(stateProperties);
        outputValues.put(OUTPUT_BLOCK_ID, normalizeBlockId(blockId));
        outputValues.put(OUTPUT_BLOCK_STATE, stateData);
        outputValues.put(OUTPUT_HAS_PROPERTIES, !stateData.isEmpty());
        syncOutputPorts();
    }

    private static String normalizeBlockId(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:stone";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private static BlockStateData parseStateProperties(String text) {
        BlockStateData state = new BlockStateData();
        if (text == null || text.isBlank()) {
            return state;
        }
        String[] pairs = text.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String propValue = kv[1].trim();
            if (!key.isEmpty() && !propValue.isEmpty()) {
                state.put(key, propValue);
            }
        }
        return state;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("blockId", blockId);
        state.put("stateProperties", stateProperties);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("blockId") instanceof String value) {
            setBlockId(value);
        }
        if (map.get("stateProperties") instanceof String value) {
            setStateProperties(value);
        }
        invalidateCache();
    }
}
