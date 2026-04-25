package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.type_selectors.block_type_selector",
    displayName = "Block Type Selector",
    description = "Searches and selects a Minecraft block type.",
    category = "input.type_selectors",
    order = 0
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
    private static final int POPUP_PAGE_SIZE = 18;
    /** 弹窗推荐尺寸（每次打开时用 Appearing 应用，避免 ini 里残留过小的窗口） */
    private static final float POPUP_WIDTH = 440.0f;
    /** 弹窗最小尺寸，防止分类 + 列表被压到不可见 */
    private static final float POPUP_MIN_WIDTH = 380.0f;
    private static final float POPUP_MIN_HEIGHT = 480.0f;
    private static final String BLOCK_PICKER_POPUP_KEY = "block_picker";
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_STONE = "stone";
    private static final String CATEGORY_WOOD = "wood";
    private static final String CATEGORY_NATURAL = "natural";
    private static final String CATEGORY_DECOR = "decor";
    private static final String CATEGORY_REDSTONE = "redstone";
    private static final String CATEGORY_FUNCTIONAL = "functional";
    private static final String CATEGORY_NETHER_END = "nether_end";
    private static final String CATEGORY_MODDED = "modded";

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
    private transient volatile List<String> allBlocks = new ArrayList<>();
    private transient volatile List<String> filteredBlocks = new ArrayList<>();
    private transient volatile boolean minecraftOnly = false;
    private transient volatile int currentPage = 0;
    private transient volatile boolean blockRegistryReady = true;
    private transient volatile boolean registryErrorLogged = false;
    private transient volatile String selectedCategory = CATEGORY_ALL;

    public BlockTypeSelectorNode() {
        super(UUID.randomUUID(), "input.type_selectors.block_type_selector");

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
        // 与 renderCustomUIScaled 一致：仅上下边距 + 单行按钮（无独立标题行）
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        // 与 Text Input 等节点同量级，保证缩小时仍有稳定内容区宽度（逻辑单位）
        return 200f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;

            try {
                // 与 TextInputNode 一致：在像素空间内算可用宽度，避免 getAvailableContentWidth(像素) 再 /zoom
                // 又经 LayoutHelper.setItemWidth 二次缩放导致缩放画布时按钮宽度飘忽
                float edgeMargin = layout.toPixels(getSmallPadding());
                float availableWidth = Math.max(0.0f, layout.toPixelsExact(width) - edgeMargin * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();

                layout.addVerticalSpacing(getMediumPadding());
                ImGui.setCursorPosX(baseCursorX + edgeMargin);

                layout.pushFramePadding(4.0f, 3.0f);
                try {
                    String compactLabel = buildCompactLabel();
                    if (ImGui.button(compactLabel + "##open_block_picker", availableWidth, 0)) {
                        ensureBlockCatalogReady();
                        updateFilteredList(searchBuffer.get());
                        openScopedPopup(BLOCK_PICKER_POPUP_KEY, "Select Block");
                    }
                } finally {
                    layout.popStyleVar();
                }

                if (renderBlockPickerPopup()) {
                    changed = true;
                }

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("BlockTypeSelectorNode UI render failed: " + e.getMessage());
            }

            return changed;
        });
    }

    private boolean renderBlockPickerPopup() {
        boolean changed = false;
        // 限制最小窗口，避免分类/快捷栏把列表挤没；Appearing 在每次弹出时给足默认尺寸
        ImGui.setNextWindowSizeConstraints(POPUP_MIN_WIDTH, POPUP_MIN_HEIGHT, 4096.0f, 4096.0f);
        // 固定宽高（不用 AlwaysAutoResize），避免首帧内容区过窄导致按钮极小，以及每帧重算宽度产生「由宽变窄」的观感
        ImGui.setNextWindowSize(POPUP_WIDTH, POPUP_MIN_HEIGHT, imgui.flag.ImGuiCond.Appearing);
        // WindowPadding 必须在 BeginPopupModal 之前 push，否则弹窗已按默认 padding 创建，内容会紧贴左/上边缘
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 8.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 8.0f);
        try {
            if (!beginScopedPopupModal(BLOCK_PICKER_POPUP_KEY, "Select Block", 0)) {
                return false;
            }
            try {
            renderSearchScopeRow();

            ImGui.separator();
            renderCategorySelector();
            ImGui.separator();

            if (renderQuickBlockStrip()) {
                changed = true;
            }

            ImGui.separator();
            List<String> snapshot = filteredBlocks;
            int total = snapshot.size();
            int totalPages = Math.max(1, (int) Math.ceil(total / (float) POPUP_PAGE_SIZE));
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1;
            }
            int start = currentPage * POPUP_PAGE_SIZE;
            int end = Math.min(start + POPUP_PAGE_SIZE, total);

            ImGui.text(String.format("Results: %d", total));
            // 用剩余可用高度显式分配列表区，避免负高度在头部内容变多时算错导致列表被压扁
            float footerReserve = ImGui.getFrameHeightWithSpacing() * 1.35f + ImGui.getStyle().getItemSpacingY() * 2.0f + 6.0f;
            float listHeight = ImGui.getContentRegionAvail().y - footerReserve;
            listHeight = Math.max(160.0f, listHeight);
            float listWidth = ImGui.getContentRegionAvail().x;
            // 列表子窗口不画边框，减少一层内边距/裁剪观感
            ImGui.beginChild("##block_picker_list", listWidth, listHeight, false, ImGuiWindowFlags.AlwaysVerticalScrollbar);
            try {
                if (snapshot.isEmpty()) {
                    if (!blockRegistryReady) {
                        ImGui.textDisabled("Block registry not ready");
                    } else {
                        ImGui.textDisabled("No blocks found");
                    }
                } else {
                    for (int i = start; i < end; i++) {
                        String blockId = snapshot.get(i);
                        boolean isSelected = blockId.equals(selectedBlock);
                        String selectableLabel = buildSelectableLabel(blockId);
                        if (ImGui.selectable(selectableLabel + "##block_" + i, isSelected)) {
                            setSelectedBlock(blockId);
                            changed = true;
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(blockId);
                        }
                    }
                }
            } finally {
                ImGui.endChild();
            }

            boolean canPrev = currentPage > 0;
            boolean canNext = currentPage + 1 < totalPages;
            if (!canPrev) ImGui.beginDisabled();
            if (ImGui.button("< Prev##block_page_prev")) {
                currentPage--;
            }
            if (!canPrev) ImGui.endDisabled();

            ImGui.sameLine();
            ImGui.text(String.format("Page %d / %d", currentPage + 1, totalPages));
            ImGui.sameLine();

            if (!canNext) ImGui.beginDisabled();
            if (ImGui.button("Next >##block_page_next")) {
                currentPage++;
            }
            if (!canNext) ImGui.endDisabled();

            ImGui.sameLine();
            if (ImGui.button("Close##close_block_picker")) {
                ImGui.closeCurrentPopup();
            }
            } finally {
                endScopedPopup();
            }
        } finally {
            ImGui.popStyleVar(2);
        }
        return changed;
    }

    /**
     * 搜索框与 All / Minecraft 同一行：用表格分配列宽，避免输入框占满整行后挤出边界。
     */
    private void renderSearchScopeRow() {
        float fp = ImGui.getStyle().getFramePaddingX() * 2f;
        float inner = ImGui.getStyle().getItemInnerSpacingX();
        float reserve = ImGui.calcTextSize("All").x + ImGui.calcTextSize("Minecraft").x + fp * 2f + inner + 20f;
        float avail = ImGui.getContentRegionAvail().x;
        float scopeColW = Math.min(Math.max(reserve, 118f), avail * 0.45f);

        if (!ImGui.beginTable("##block_search_scope", 2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoBordersInBody)) {
            return;
        }
        ImGui.tableSetupColumn("search", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("scope", ImGuiTableColumnFlags.WidthFixed, scopeColW);
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ImGui.pushItemWidth(-1.0f);
        if (ImGui.inputTextWithHint("##block_picker_search", "Search block id...", searchBuffer, ImGuiInputTextFlags.None)) {
            updateFilteredList(searchBuffer.get());
        }
        ImGui.popItemWidth();
        ImGui.tableSetColumnIndex(1);
        if (ImGui.smallButton("All##scope_all")) {
            minecraftOnly = false;
            updateFilteredList(searchBuffer.get());
        }
        ImGui.sameLine(0f, inner);
        if (ImGui.smallButton("Minecraft##scope_vanilla")) {
            minecraftOnly = true;
            updateFilteredList(searchBuffer.get());
        }
        ImGui.endTable();
    }

    private void renderCategorySelector() {
        String[][] categories = {
                {CATEGORY_ALL, "All"},
                {CATEGORY_STONE, "Stone"},
                {CATEGORY_WOOD, "Wood"},
                {CATEGORY_NATURAL, "Natural"},
                {CATEGORY_DECOR, "Decor"},
                {CATEGORY_REDSTONE, "Redstone"},
                {CATEGORY_FUNCTIONAL, "Functional"},
                {CATEGORY_NETHER_END, "Nether/End"},
                {CATEGORY_MODDED, "Modded"}
        };

        ImGui.text("Category:");
        // 固定两行：上行 ceil(n/2)，下行剩余，避免横向子窗口裁剪
        int n = categories.length;
        int firstRowCount = (n + 1) / 2;
        renderCategoryButtonRow(categories, 0, firstRowCount);
        renderCategoryButtonRow(categories, firstRowCount, n);
    }

    private void renderCategoryButtonRow(String[][] categories, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (i > fromInclusive) {
                ImGui.sameLine();
            }
            String categoryKey = categories[i][0];
            String categoryLabel = categories[i][1];
            boolean isSelected = categoryKey.equals(selectedCategory);
            if (isSelected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.26f, 0.44f, 0.62f, 1.0f);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.30f, 0.50f, 0.70f, 1.0f);
            }
            if (ImGui.smallButton(categoryLabel + "##category_" + categoryKey)) {
                selectedCategory = categoryKey;
                updateFilteredList(searchBuffer.get());
            }
            if (isSelected) {
                ImGui.popStyleColor(2);
            }
        }
    }

    /** 两行快捷块，每行 4 个 */
    private boolean renderQuickBlockStrip() {
        boolean quickChanged = false;
        ImGui.text("Quick:");
        int perRow = 4;
        for (int row = 0; row < 2; row++) {
            int start = row * perRow;
            if (start >= QUICK_BLOCKS.length) {
                break;
            }
            int end = Math.min(start + perRow, QUICK_BLOCKS.length);
            for (int i = start; i < end; i++) {
                if (i > start) {
                    ImGui.sameLine();
                }
                String quickBlock = QUICK_BLOCKS[i];
                String quickLabel = quickBlock.split(":", 2)[1];
                if (ImGui.smallButton(quickLabel + "##quick_" + i)) {
                    setSelectedBlock(quickBlock);
                    quickChanged = true;
                }
            }
        }
        return quickChanged;
    }

    private String buildCompactLabel() {
        String display = selectedBlock;
        if (display.length() > 26) {
            display = display.substring(0, 23) + "...";
        }
        return "[" + display + " v]";
    }

    private String buildSelectableLabel(String blockId) {
        String[] parts = blockId.split(":", 2);
        if (parts.length < 2) {
            return blockId;
        }
        String prefix = "minecraft".equals(parts[0]) ? "[MC]" : "[MOD]";
        return prefix + " " + parts[1];
    }

    private void ensureBlockCatalogReady() {
        if (!allBlocks.isEmpty()) {
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
            if (blockRegistryReady) {
                registryErrorLogged = false;
            }
        } catch (Exception ignored) {
            blockRegistryReady = false;
            if (!registryErrorLogged) {
                NodeCraft.LOGGER.warn("Block registry is not ready for BlockTypeSelectorNode yet.");
                registryErrorLogged = true;
            }
        }
        allBlocks = collected;
    }

    private void updateFilteredList(String searchTextRaw) {
        ensureBlockCatalogReady();

        String searchText = searchTextRaw == null ? "" : searchTextRaw.trim().toLowerCase(Locale.ROOT);
        List<String> nextFilteredBlocks = new ArrayList<>();

        List<String> source = allBlocks;
        for (String fullId : source) {
            boolean isMinecraft = fullId.startsWith("minecraft:");
            if (!allowModded && !isMinecraft) {
                continue;
            }
            if (minecraftOnly && !isMinecraft) {
                continue;
            }
            if (!matchesCategory(fullId)) {
                continue;
            }
            if (searchText.isEmpty() || fullId.toLowerCase(Locale.ROOT).contains(searchText)) {
                nextFilteredBlocks.add(fullId);
            }
        }

        for (String quickBlock : QUICK_BLOCKS) {
            if (!nextFilteredBlocks.contains(quickBlock)) {
                boolean isMinecraft = quickBlock.startsWith("minecraft:");
                if (!allowModded && !isMinecraft) {
                    continue;
                }
                if (minecraftOnly && !isMinecraft) {
                    continue;
                }
                if (!matchesCategory(quickBlock)) {
                    continue;
                }
                if (searchText.isEmpty() || quickBlock.toLowerCase(Locale.ROOT).contains(searchText)) {
                    nextFilteredBlocks.add(quickBlock);
                }
            }
        }

        currentPage = 0;
        filteredBlocks = nextFilteredBlocks;
    }

    private boolean matchesCategory(String fullId) {
        if (CATEGORY_ALL.equals(selectedCategory)) {
            return true;
        }

        String namespace = "minecraft";
        String path = fullId;
        String[] parts = fullId.split(":", 2);
        if (parts.length == 2) {
            namespace = parts[0];
            path = parts[1];
        }

        if (CATEGORY_MODDED.equals(selectedCategory)) {
            return !"minecraft".equals(namespace);
        }
        if (!"minecraft".equals(namespace)) {
            return false;
        }

        return switch (selectedCategory) {
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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void setSelectedBlock(String blockId) {
        String nextBlockId = sanitizeBlockId(blockId);

        if (!allowModded && !nextBlockId.startsWith("minecraft:")) {
            nextBlockId = "minecraft:stone";
        }

        if (!isKnownBlockId(nextBlockId)) {
            nextBlockId = "minecraft:stone";
        }

        if (!this.selectedBlock.equals(nextBlockId)) {
            this.selectedBlock = nextBlockId;
            updateOutputs();
            markDirty();
        }
    }

    private String sanitizeBlockId(String blockId) {
        if (blockId == null) {
            return "minecraft:stone";
        }
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "minecraft:stone";
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    private boolean isKnownBlockId(String blockId) {
        if ("minecraft:stone".equals(blockId)) {
            return true;
        }

        ensureBlockCatalogReady();
        if (!allBlocks.isEmpty()) {
            return allBlocks.contains(blockId);
        }

        try {
            Identifier id = Identifier.tryParse(blockId);
            if (id == null) {
                return false;
            }

            // 注册表可能在极早期尚未完全可用，此时不强制判定为未知，避免误降级。
            if (Registries.BLOCK.getIds().isEmpty()) {
                return true;
            }
            return Registries.BLOCK.containsId(id);
        } catch (Exception ignored) {
            return false;
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
        updateFilteredList(searchBuffer.get());
    }

    @Override
    public Object getNodeState() {
        return Map.of(
            "selectedBlock", getSelectedBlock(),
            "allowModded", isAllowModded(),
            "selectedCategory", selectedCategory,
            "minecraftOnly", minecraftOnly
        );
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map) {
            if (map.get("allowModded") instanceof Boolean bool) {
                setAllowModded(bool);
            }
            if (map.get("minecraftOnly") instanceof Boolean onlyMinecraft) {
                minecraftOnly = onlyMinecraft;
            }
            if (map.get("selectedCategory") instanceof String category) {
                selectedCategory = sanitizeCategory(category);
            }
            if (map.get("selectedBlock") instanceof String value) {
                setSelectedBlock(value);
            }
            updateFilteredList(searchBuffer.get());
        }
    }

    private String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_ALL;
        }
        return switch (category) {
            case CATEGORY_ALL, CATEGORY_STONE, CATEGORY_WOOD, CATEGORY_NATURAL, CATEGORY_DECOR,
                 CATEGORY_REDSTONE, CATEGORY_FUNCTIONAL, CATEGORY_NETHER_END, CATEGORY_MODDED -> category;
            default -> CATEGORY_ALL;
        };
    }
}
