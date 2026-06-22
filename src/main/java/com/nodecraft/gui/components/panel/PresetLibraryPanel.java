package com.nodecraft.gui.components.panel;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.preset.GraphPresetCatalog;
import com.nodecraft.gui.preset.GraphPresetRules;
import com.nodecraft.gui.utils.UserPreferences;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PresetLibraryPanel {

    private static final String PREF_ACTIVE_TAB_KEY = "left_panel.active_tab";
    private static final String SEARCH_HINT = "搜索预设...";

    private final GraphPresetCatalog catalog = GraphPresetCatalog.getInstance();
    private final ImString searchQuery = new ImString("", 128);
    private final ImString renameBuffer = new ImString("", 128);
    private final ImString createCategoryBuffer = new ImString("", 64);

    private String statusMessage = "";
    private long statusMessageUntilMs = 0L;
    private boolean loaded;

    private String contextPresetId;
    private String contextCategoryId;
    private boolean openRenamePresetPopup;
    private boolean openRenameCategoryPopup;
    private boolean openCreateCategoryPopup;

    public void initialize() {
        if (loaded) {
            return;
        }
        catalog.reload();
        loaded = true;
    }

    public void reload() {
        catalog.reload();
        loaded = true;
    }

    public void render() {
        initialize();
        renderModals();

        ImGui.text("拖动预设到画布创建节点链；右键可重命名、删除或移动。");
        if (ImGui.button("+ 新建分类")) {
            createCategoryBuffer.set("");
            openCreateCategoryPopup = true;
        }

        ImGui.separator();

        ImGui.pushItemWidth(-1);
        ImGui.inputTextWithHint("##presetSearch", SEARCH_HINT, searchQuery);
        ImGui.popItemWidth();

        if (System.currentTimeMillis() < statusMessageUntilMs && !statusMessage.isBlank()) {
            ImGui.textColored(0.4f, 0.9f, 0.5f, 1.0f, statusMessage);
        }

        ImGui.separator();

        String filter = searchQuery.get().trim().toLowerCase(Locale.ROOT);
        List<GraphPresetCatalog.CategoryView> categories = catalog.getCategories();
        if (categories.isEmpty()) {
            ImGui.textDisabled("暂无预设数据");
            return;
        }

        for (GraphPresetCatalog.CategoryView categoryView : categories) {
            renderCategory(categoryView, filter);
        }

        renderPresetContextMenu();
        renderCategoryContextMenu();
    }

    private void renderCategory(GraphPresetCatalog.CategoryView categoryView, String filter) {
        GraphPresetRules.PresetCategory category = categoryView.category();
        if (category == null || category.presets == null) {
            return;
        }

        List<GraphPresetCatalog.PresetView> visiblePresets = new ArrayList<>();
        for (int i = 0; i < category.presets.size(); i++) {
            GraphPresetRules.GraphPresetDefinition preset = category.presets.get(i);
            if (preset == null || !matchesFilter(preset, filter)) {
                continue;
            }
            visiblePresets.add(new GraphPresetCatalog.PresetView(
                    preset,
                    category.id,
                    categoryView.source(),
                    i));
        }
        if (visiblePresets.isEmpty()) {
            return;
        }

        ImGui.pushID("preset_cat_" + category.id);
        String categoryLabel = category.displayName != null ? category.displayName : category.id;
        boolean categoryOpen = ImGui.collapsingHeader(categoryLabel, ImGuiTreeNodeFlags.DefaultOpen);
        if (categoryView.isEditable() && ImGui.isItemHovered() && ImGui.isMouseClicked(1)) {
            contextCategoryId = category.id;
            renameBuffer.set(categoryLabel);
            ImGui.openPopup("PresetCategoryContextMenu");
        }

        if (categoryOpen) {
            for (GraphPresetCatalog.PresetView presetView : visiblePresets) {
                renderPresetItem(presetView, categoryView);
            }

            if (categoryView.isEditable()) {
                renderCategoryDropTarget(category.id);
            }
        }
        ImGui.popID();
    }

    private void renderPresetItem(
            GraphPresetCatalog.PresetView presetView,
            GraphPresetCatalog.CategoryView categoryView) {
        GraphPresetRules.GraphPresetDefinition preset = presetView.preset();
        String label = preset.displayName != null ? preset.displayName : preset.id;
        boolean applicable = presetView.isApplicable();

        ImGui.pushID("preset_" + preset.id);
        if (!applicable) {
            ImGui.beginDisabled();
        }

        if (presetView.isEditable()) {
            ImGui.button("::");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("拖动以调整顺序或移动到其他分类");
            }
            if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
                byte[] payloadBytes = preset.id.getBytes(StandardCharsets.UTF_8);
                ImGui.setDragDropPayload(GraphPresetCatalog.PRESET_REORDER_PAYLOAD, payloadBytes);
                ImGui.text("移动: " + label);
                ImGui.endDragDropSource();
            }
            ImGui.sameLine();
        }

        ImGui.selectable(label, false);
        if (applicable && ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            byte[] payloadBytes = preset.id.getBytes(StandardCharsets.UTF_8);
            ImGui.setDragDropPayload(GraphPresetCatalog.PRESET_DRAG_PAYLOAD, payloadBytes);
            ImGui.text("放置到画布: " + label);
            ImGui.endDragDropSource();
        }

        if (presetView.isEditable()) {
            if (ImGui.beginDragDropTarget()) {
                Object payload = ImGui.acceptDragDropPayload(GraphPresetCatalog.PRESET_REORDER_PAYLOAD);
                String draggedPresetId = parsePayload(payload);
                if (draggedPresetId != null && !draggedPresetId.equals(preset.id)) {
                    catalog.moveUserPresetBefore(draggedPresetId, presetView.categoryId(), preset.id);
                    showStatus("已移动预设", true);
                }
                ImGui.endDragDropTarget();
            }
        }

        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            if (preset.description != null && !preset.description.isBlank()) {
                ImGui.textWrapped(preset.description);
            }
            if (applicable) {
                ImGui.text("拖动到画布以创建节点链");
            } else {
                ImGui.textDisabled("筹备中");
            }
            if (presetView.isEditable()) {
                ImGui.textDisabled("右键可编辑");
            }
            ImGui.endTooltip();
        }

        if (ImGui.isItemHovered() && ImGui.isMouseClicked(1) && presetView.isEditable()) {
            contextPresetId = preset.id;
            renameBuffer.set(label);
            ImGui.openPopup("PresetItemContextMenu");
        }

        if (!applicable) {
            ImGui.endDisabled();
        }
        ImGui.popID();
    }

    private void renderCategoryDropTarget(String categoryId) {
        ImGui.spacing();
        ImGui.pushID("drop_" + categoryId + "_end");
        ImGui.selectable("  拖放到此分类末尾", false);
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(GraphPresetCatalog.PRESET_REORDER_PAYLOAD);
            String draggedPresetId = parsePayload(payload);
            if (draggedPresetId != null) {
                int endIndex = categoryPresetCount(categoryId);
                catalog.moveUserPreset(draggedPresetId, categoryId, endIndex);
                showStatus("已移动预设", true);
            }
            ImGui.endDragDropTarget();
        }
        ImGui.popID();
    }

    private int categoryPresetCount(String categoryId) {
        for (GraphPresetCatalog.CategoryView categoryView : catalog.getCategories()) {
            if (categoryView.category().id.equals(categoryId)) {
                return categoryView.category().presets != null ? categoryView.category().presets.size() : 0;
            }
        }
        return 0;
    }

    private void renderPresetContextMenu() {
        if (ImGui.beginPopup("PresetItemContextMenu")) {
            GraphPresetCatalog.PresetView presetView = contextPresetId != null
                    ? catalog.findPreset(contextPresetId)
                    : null;
            if (presetView != null && presetView.isEditable()) {
                if (ImGui.menuItem("重命名")) {
                    renameBuffer.set(presetView.preset().displayName);
                    openRenamePresetPopup = true;
                }
                if (ImGui.menuItem("删除")) {
                    if (catalog.deleteUserPreset(presetView.preset().id)) {
                        showStatus("已删除预设", true);
                    }
                }
                if (ImGui.beginMenu("移动到分类")) {
                    for (GraphPresetCatalog.CategoryView categoryView : catalog.getUserCategories()) {
                        String label = categoryView.category().displayName != null
                                ? categoryView.category().displayName
                                : categoryView.category().id;
                        if (ImGui.menuItem(label)) {
                            int endIndex = categoryView.category().presets != null
                                    ? categoryView.category().presets.size()
                                    : 0;
                            catalog.moveUserPreset(presetView.preset().id, categoryView.category().id, endIndex);
                            showStatus("已移动预设", true);
                        }
                    }
                    ImGui.endMenu();
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderCategoryContextMenu() {
        if (ImGui.beginPopup("PresetCategoryContextMenu")) {
            GraphPresetCatalog.CategoryView categoryView = catalog.getCategories().stream()
                    .filter(c -> contextCategoryId != null && contextCategoryId.equals(c.category().id))
                    .findFirst()
                    .orElse(null);
            if (categoryView != null && categoryView.isEditable()) {
                if (ImGui.menuItem("重命名分类")) {
                    renameBuffer.set(categoryView.category().displayName);
                    openRenameCategoryPopup = true;
                }
                boolean isDefault = GraphPresetCatalog.DEFAULT_USER_CATEGORY_ID.equals(categoryView.category().id);
                if (!isDefault) {
                    if (ImGui.menuItem("删除分类")) {
                        if (catalog.deleteUserCategory(categoryView.category().id)) {
                            showStatus("已删除分类", true);
                        }
                    }
                }
            }
            ImGui.endPopup();
        }
    }

    private void renderModals() {
        if (openCreateCategoryPopup) {
            ImGui.openPopup("CreatePresetCategory");
            openCreateCategoryPopup = false;
        }
        if (ImGui.beginPopupModal("CreatePresetCategory", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("新建分类");
            ImGui.setNextItemWidth(280.0f);
            boolean submit = ImGui.inputText("##new_preset_category", createCategoryBuffer);
            submit |= ImGui.button("创建", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (submit) {
                String name = createCategoryBuffer.get().trim();
                if (!name.isEmpty() && catalog.createUserCategory(name) != null) {
                    showStatus("已创建分类", true);
                    ImGui.closeCurrentPopup();
                }
            } else if (cancel) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        if (openRenamePresetPopup) {
            ImGui.openPopup("RenamePreset");
            openRenamePresetPopup = false;
        }
        if (ImGui.beginPopupModal("RenamePreset", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("重命名预设");
            ImGui.setNextItemWidth(280.0f);
            boolean submit = ImGui.inputText("##rename_preset", renameBuffer);
            submit |= ImGui.button("确定", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (submit && contextPresetId != null) {
                if (catalog.renameUserPreset(contextPresetId, renameBuffer.get().trim(), null)) {
                    showStatus("已重命名预设", true);
                    ImGui.closeCurrentPopup();
                }
            } else if (cancel) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        if (openRenameCategoryPopup) {
            ImGui.openPopup("RenamePresetCategory");
            openRenameCategoryPopup = false;
        }
        if (ImGui.beginPopupModal("RenamePresetCategory", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("重命名分类");
            ImGui.setNextItemWidth(280.0f);
            boolean submit = ImGui.inputText("##rename_preset_category", renameBuffer);
            submit |= ImGui.button("确定", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (submit && contextCategoryId != null) {
                if (catalog.renameUserCategory(contextCategoryId, renameBuffer.get().trim())) {
                    showStatus("已重命名分类", true);
                    ImGui.closeCurrentPopup();
                }
            } else if (cancel) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private boolean matchesFilter(GraphPresetRules.GraphPresetDefinition preset, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        String name = preset.displayName != null ? preset.displayName.toLowerCase(Locale.ROOT) : "";
        String description = preset.description != null ? preset.description.toLowerCase(Locale.ROOT) : "";
        String id = preset.id != null ? preset.id.toLowerCase(Locale.ROOT) : "";
        return name.contains(filter) || description.contains(filter) || id.contains(filter);
    }

    private static String parsePayload(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (payload instanceof String text) {
            return text;
        }
        return null;
    }

    private void showStatus(String message, boolean success) {
        statusMessage = message;
        statusMessageUntilMs = System.currentTimeMillis() + 3000L;
        if (!success) {
            NodeCraft.LOGGER.debug("Preset status: {}", message);
        }
    }

    public static int loadPreferredTabIndex() {
        String stored = UserPreferences.getString(PREF_ACTIVE_TAB_KEY, "0");
        try {
            return Math.max(0, Math.min(1, Integer.parseInt(stored)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static void savePreferredTabIndex(int index) {
        UserPreferences.setString(PREF_ACTIVE_TAB_KEY, Integer.toString(Math.max(0, Math.min(1, index))));
    }
}
