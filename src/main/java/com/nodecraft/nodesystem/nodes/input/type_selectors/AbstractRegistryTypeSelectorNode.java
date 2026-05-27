package com.nodecraft.nodesystem.nodes.input.type_selectors;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 与 {@link BlockTypeSelectorNode} 同款的注册表 ID 选择弹窗：节点上为紧凑按钮，弹层内搜索 / 分类 / 分页列表。
 */
abstract class AbstractRegistryTypeSelectorNode extends BaseCustomUINode {

    static final String CATEGORY_ALL = "all";
    static final String CATEGORY_MODDED = "modded";

    private static final int POPUP_PAGE_SIZE = 18;
    private static final float POPUP_MIN_WIDTH = 380.0f;
    private static final float POPUP_MIN_HEIGHT = 420.0f;
    private static final float POPUP_FIXED_HEIGHT = 520.0f;
    private static final float POPUP_WINDOW_PADDING_X = 5.0f;
    private static final float POPUP_WINDOW_PADDING_Y = 3.0f;
    private static final float POPUP_ITEM_SPACING_X = 4.0f;
    private static final float POPUP_ITEM_SPACING_Y = 3.0f;
    private static final float POPUP_SCROLLBAR_SIZE = 14.0f;
    private static final float POPUP_FRAME_PADDING_X = 4.0f;
    private static final float POPUP_FRAME_PADDING_Y = 3.0f;
    private static final float POPUP_FRAME_BORDER_SIZE = 1.0f;
    private static final float POPUP_FRAME_ROUNDING = 0.0f;
    private static final float POPUP_SECTION_GAP = 2.0f;
    private static final float POPUP_LIST_FOOTER_EXTRA = 0.0f;
    private static final String OPEN_BUTTON_SUFFIX = " v]";
    private static final String OPEN_BUTTON_PREFIX = "[";
    private static final String ELLIPSIS = "...";

    record CategorySpec(String key, String label) {}

    private transient ImString searchBuffer = new ImString(256);
    private transient volatile List<String> allIds = new ArrayList<>();
    private transient volatile List<String> filteredIds = new ArrayList<>();
    private transient volatile boolean minecraftOnly = false;
    private transient volatile int currentPage = 0;
    private transient volatile boolean registryReady = true;
    private transient volatile boolean registryErrorLogged = false;
    private transient volatile String selectedCategory = CATEGORY_ALL;

    protected AbstractRegistryTypeSelectorNode(java.util.UUID id, String nodeType) {
        super(id, nodeType);
    }

    protected abstract String getPickerPopupKey();

    protected abstract String getPickerTitle();

    protected abstract String getSearchHint();

    protected abstract String getOpenButtonIdSuffix();

    protected abstract String readSelectedId();

    protected abstract void applySelectedId(String id);

    protected abstract boolean isAllowModded();

    protected abstract void setAllowModdedFlag(boolean allowModded);

    protected abstract String getDefaultId();

    protected abstract String[] getQuickPickIds();

    protected abstract CategorySpec[] getCategorySpecs();

    protected abstract void collectRegistryIds(List<String> target);

    protected abstract boolean isKnownId(String id);

    protected abstract boolean matchesCategory(String fullId, String categoryKey);

    protected abstract String getRegistryNotReadyMessage();

    protected abstract String getRegistryLoadWarningLog();

    protected abstract void onSelectionApplied();

    protected void normalizeFilterState() {
        if (!isAllowModded()) {
            minecraftOnly = true;
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        onSelectionApplied();
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
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
                    String compactLabel = buildCompactLabel(availableWidth, readSelectedId());
                    if (ImGui.button(compactLabel + getOpenButtonIdSuffix(), availableWidth, 0)) {
                        ensureCatalogReady();
                        updateFilteredList(getSearchBuffer().get());
                        openScopedPopup(getPickerPopupKey(), getPickerTitle());
                    }
                } finally {
                    layoutHelper.popStyleVar();
                }

                if (renderPickerPopup()) {
                    changed = true;
                }

                layoutHelper.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("{} UI render failed: {}", getClass().getSimpleName(), e.getMessage());
            }
            return changed;
        });
    }

    protected final void updateFilteredListFromSearch() {
        updateFilteredList(getSearchBuffer().get());
    }

    protected final String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return CATEGORY_ALL;
        }
        for (CategorySpec spec : getCategorySpecs()) {
            if (spec.key().equals(category)) {
                return category;
            }
        }
        return CATEGORY_ALL;
    }

    protected final boolean matchesModdedCategory(String fullId) {
        String namespace = "minecraft";
        String[] parts = fullId.split(":", 2);
        if (parts.length == 2) {
            namespace = parts[0];
        }
        return !"minecraft".equals(namespace);
    }

    protected final boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    protected final void restoreFilterState(boolean allowModded, String category, boolean minecraftOnlyFilter) {
        setAllowModdedFlag(allowModded);
        selectedCategory = sanitizeCategory(category);
        minecraftOnly = minecraftOnlyFilter;
        normalizeFilterState();
        updateFilteredListFromSearch();
    }

    protected final String getFilterCategory() {
        return selectedCategory;
    }

    protected final boolean isMinecraftOnlyFilter() {
        return minecraftOnly;
    }

    private boolean renderPickerPopup() {
        boolean changed = false;
        imgui.ImGui imguiInstance = new imgui.ImGui();

        float popupAppearingHeight = Math.max(POPUP_MIN_HEIGHT, POPUP_FIXED_HEIGHT);
        ImGui.setNextWindowSizeConstraints(POPUP_MIN_WIDTH, POPUP_MIN_HEIGHT, 4096.0f, 4096.0f);
        ImGui.setNextWindowSize(POPUP_MIN_WIDTH, popupAppearingHeight, imgui.flag.ImGuiCond.Appearing);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, POPUP_WINDOW_PADDING_X, POPUP_WINDOW_PADDING_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, POPUP_ITEM_SPACING_X, POPUP_ITEM_SPACING_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, POPUP_SCROLLBAR_SIZE);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, POPUP_FRAME_PADDING_X, POPUP_FRAME_PADDING_Y);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, POPUP_FRAME_BORDER_SIZE);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, POPUP_FRAME_ROUNDING);
        try {
            imguiInstance.setWindowFontScale(1.0f);
            int popupFlags = ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
            if (!beginScopedPopupModal(getPickerPopupKey(), getPickerTitle(), popupFlags)) {
                return false;
            }
            try {
                imguiInstance.setWindowFontScale(1.0f);
                renderSearchScopeRow();

                popupSectionGap();
                renderCategorySelector();
                popupSectionGap();

                if (renderQuickPickStrip()) {
                    changed = true;
                }

                popupSectionGap();
                List<String> snapshot = filteredIds;
                int total = snapshot.size();
                int totalPages = Math.max(1, (int) Math.ceil(total / (float) POPUP_PAGE_SIZE));
                if (currentPage >= totalPages) {
                    currentPage = totalPages - 1;
                }
                int start = currentPage * POPUP_PAGE_SIZE;
                int end = Math.min(start + POPUP_PAGE_SIZE, total);

                ImGui.text(String.format("Results: %d", total));
                float footerReserve = ImGui.getFrameHeightWithSpacing() + POPUP_LIST_FOOTER_EXTRA;
                float listHeight = ImGui.getContentRegionAvail().y - footerReserve;
                listHeight = Math.max(160.0f, listHeight);
                float listWidth = ImGui.getContentRegionAvail().x;
                ImGui.beginChild("##registry_picker_list", listWidth, listHeight, false, ImGuiWindowFlags.AlwaysVerticalScrollbar);
                try {
                    if (snapshot.isEmpty()) {
                        if (!registryReady) {
                            ImGui.textDisabled(getRegistryNotReadyMessage());
                        } else {
                            ImGui.textDisabled("No results found");
                        }
                    } else {
                        String selected = readSelectedId();
                        for (int i = start; i < end; i++) {
                            String entryId = snapshot.get(i);
                            boolean isSelected = entryId.equals(selected);
                            String selectableLabel = buildSelectableLabel(entryId);
                            if (ImGui.selectable(selectableLabel + "##entry_" + i, isSelected)) {
                                applySelectedId(entryId);
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
                if (ImGui.button("< Prev##registry_page_prev")) {
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
                if (ImGui.button("Next >##registry_page_next")) {
                    currentPage++;
                }
                if (!canNext) {
                    ImGui.endDisabled();
                }

                ImGui.sameLine();
                if (ImGui.button("Close##close_registry_picker")) {
                    ImGui.closeCurrentPopup();
                }
            } finally {
                imguiInstance.setWindowFontScale(1.0f);
                endScopedPopup();
            }
        } finally {
            ImGui.popStyleVar(6);
        }
        return changed;
    }

    private static void popupSectionGap() {
        ImGui.dummy(0.0f, POPUP_SECTION_GAP);
    }

    private ImString getSearchBuffer() {
        if (searchBuffer == null) {
            searchBuffer = new ImString(256);
        }
        return searchBuffer;
    }

    private void renderSearchScopeRow() {
        float fp = ImGui.getStyle().getFramePaddingX() * 2f;
        float inner = ImGui.getStyle().getItemInnerSpacingX();
        float reserve = ImGui.calcTextSize("All").x + ImGui.calcTextSize("Minecraft").x + fp * 2f + inner + 20f;
        float avail = ImGui.getContentRegionAvail().x;
        float scopeColW = Math.min(Math.max(reserve, 118f), avail * 0.45f);

        if (!ImGui.beginTable("##registry_search_scope", 2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.NoBordersInBody)) {
            return;
        }
        ImGui.tableSetupColumn("search", ImGuiTableColumnFlags.WidthStretch);
        ImGui.tableSetupColumn("scope", ImGuiTableColumnFlags.WidthFixed, scopeColW);
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        ImGui.pushItemWidth(-1.0f);
        ImString buffer = getSearchBuffer();
        if (ImGui.inputTextWithHint("##registry_picker_search", getSearchHint(), buffer, ImGuiInputTextFlags.None)) {
            updateFilteredList(buffer.get());
        }
        ImGui.popItemWidth();
        ImGui.tableSetColumnIndex(1);
        if (renderPopupButton("All##scope_all")) {
            minecraftOnly = false;
            updateFilteredList(getSearchBuffer().get());
        }
        ImGui.sameLine(0f, inner);
        if (renderPopupButton("Minecraft##scope_vanilla")) {
            minecraftOnly = true;
            updateFilteredList(getSearchBuffer().get());
        }
        ImGui.endTable();
    }

    private void renderCategorySelector() {
        CategorySpec[] categories = getCategorySpecs();
        if (categories == null || categories.length == 0) {
            return;
        }

        ImGui.text("Category:");
        int n = categories.length;
        int firstRowCount = (n + 1) / 2;
        renderCategoryButtonRow(categories, 0, firstRowCount);
        renderCategoryButtonRow(categories, firstRowCount, n);
    }

    private void renderCategoryButtonRow(CategorySpec[] categories, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (i > fromInclusive) {
                ImGui.sameLine();
            }
            CategorySpec spec = categories[i];
            boolean isSelected = spec.key().equals(selectedCategory);
            if (isSelected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.26f, 0.44f, 0.62f, 1.0f);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.30f, 0.50f, 0.70f, 1.0f);
            }
            if (renderPopupButton(spec.label() + "##category_" + spec.key())) {
                selectedCategory = spec.key();
                updateFilteredList(getSearchBuffer().get());
            }
            if (isSelected) {
                ImGui.popStyleColor(2);
            }
        }
    }

    private boolean renderQuickPickStrip() {
        boolean quickChanged = false;
        String[] quickIds = getQuickPickIds();
        if (quickIds == null || quickIds.length == 0) {
            return false;
        }

        ImGui.text("Quick:");
        int perRow = 4;
        for (int row = 0; row < 2; row++) {
            int start = row * perRow;
            if (start >= quickIds.length) {
                break;
            }
            int end = Math.min(start + perRow, quickIds.length);
            for (int i = start; i < end; i++) {
                if (i > start) {
                    ImGui.sameLine();
                }
                String quickId = quickIds[i];
                String quickLabel = quickId.contains(":") ? quickId.split(":", 2)[1] : quickId;
                if (renderPopupButton(quickLabel + "##quick_" + i)) {
                    applySelectedId(quickId);
                    quickChanged = true;
                }
            }
        }
        return quickChanged;
    }

    private static boolean renderPopupButton(String label) {
        String visibleLabel = label;
        int idSeparator = label.indexOf("##");
        if (idSeparator >= 0) {
            visibleLabel = label.substring(0, idSeparator);
        }
        float width = ImGui.calcTextSize(visibleLabel).x + ImGui.getStyle().getFramePaddingX() * 2.0f;
        float height = ImGui.getFrameHeight();
        return ImGui.button(label, width, height);
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
        float fixedWidth = ImGui.calcTextSize(fixedParts).x;
        if (fixedWidth >= safeContentWidth) {
            return fixedParts;
        }

        int low = 0;
        int high = content.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = OPEN_BUTTON_PREFIX + content.substring(0, mid) + ELLIPSIS + OPEN_BUTTON_SUFFIX;
            float width = ImGui.calcTextSize(candidate).x;
            if (width <= safeContentWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (best <= 0) {
            return fixedParts;
        }
        return OPEN_BUTTON_PREFIX + content.substring(0, best) + ELLIPSIS + OPEN_BUTTON_SUFFIX;
    }

    private static String buildSelectableLabel(String fullId) {
        String[] parts = fullId.split(":", 2);
        if (parts.length < 2) {
            return fullId;
        }
        String prefix = "minecraft".equals(parts[0]) ? "[MC]" : "[MOD]";
        return prefix + " " + parts[1];
    }

    private void ensureCatalogReady() {
        if (!allIds.isEmpty()) {
            registryReady = true;
            return;
        }
        List<String> collected = new ArrayList<>();
        try {
            collectRegistryIds(collected);
            collected.sort(Comparator.naturalOrder());
            registryReady = !collected.isEmpty();
            if (registryReady) {
                registryErrorLogged = false;
            }
        } catch (Exception ignored) {
            registryReady = false;
            if (!registryErrorLogged) {
                NodeCraft.LOGGER.warn(getRegistryLoadWarningLog());
                registryErrorLogged = true;
            }
        }
        allIds = collected;
    }

    private void updateFilteredList(String searchTextRaw) {
        ensureCatalogReady();

        String searchText = searchTextRaw == null ? "" : searchTextRaw.trim().toLowerCase(Locale.ROOT);
        Set<String> nextFiltered = new LinkedHashSet<>();

        for (String fullId : allIds) {
            if (matchesFilters(fullId, searchText)) {
                nextFiltered.add(fullId);
            }
        }

        for (String quickId : getQuickPickIds()) {
            if (matchesFilters(quickId, searchText)) {
                nextFiltered.add(quickId);
            }
        }

        currentPage = 0;
        filteredIds = new ArrayList<>(nextFiltered);
    }

    private boolean matchesFilters(String fullId, String searchText) {
        boolean isMinecraft = fullId.startsWith("minecraft:");
        if (!isAllowModded() && !isMinecraft) {
            return false;
        }
        if (minecraftOnly && !isMinecraft) {
            return false;
        }
        if (!matchesCategory(fullId, selectedCategory)) {
            return false;
        }
        return searchText.isEmpty() || fullId.toLowerCase(Locale.ROOT).contains(searchText);
    }

    protected final String sanitizeNamespacedId(String raw, String defaultId) {
        if (raw == null) {
            return defaultId;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return defaultId;
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return normalized;
    }

    protected final void applyValidatedId(String rawId, String defaultId) {
        String nextId = sanitizeNamespacedId(rawId, defaultId);
        if (!isAllowModded() && !nextId.startsWith("minecraft:")) {
            nextId = defaultId;
        }
        if (!isKnownId(nextId)) {
            nextId = defaultId;
        }
        if (!readSelectedId().equals(nextId)) {
            applySelectedId(nextId);
        }
    }

    protected final boolean catalogContains(String id) {
        ensureCatalogReady();
        return !allIds.isEmpty() && allIds.contains(id);
    }
}
