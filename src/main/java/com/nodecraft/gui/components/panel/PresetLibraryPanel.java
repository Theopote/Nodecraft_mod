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
    private final ImString descriptionBuffer = new ImString("", 256);
    private final ImString createCategoryBuffer = new ImString("", 64);

    private String statusMessage = "";
    private long statusMessageUntilMs = 0L;
    private boolean loaded;

    private String contextPresetId;
    private String contextCategoryId;
    private boolean openEditPresetPopup;
    private boolean openRenameCategoryPopup;
    private boolean openCreateCategoryPopup;
    private boolean openDeletePresetConfirm;
    private boolean openDeleteCategoryConfirm;
    private String pendingDeletePresetName;
    private String pendingDeleteCategoryName;

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

        ImGui.text("拖动预设到画布创建节点链；拖动手柄调整顺序；右键可编辑或删除。");
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
        if (visiblePresets.isEmpty() && !filter.isEmpty()) {
            return;
        }

        ImGui.pushID("preset_cat_" + category.id);
        String categoryLabel = category.displayName != null ? category.displayName : category.id;
        boolean categoryOpen = ImGui.collapsingHeader(categoryLabel, ImGuiTreeNodeFlags.DefaultOpen);

        if (categoryView.isEditable() && ImGui.beginPopupContextItem("preset_category_ctx")) {
            contextCategoryId = category.id;
            if (ImGui.menuItem("重命名分类")) {
                renameBuffer.set(categoryLabel);
                openRenameCategoryPopup = true;
            }
            boolean isDefault = GraphPresetCatalog.DEFAULT_USER_CATEGORY_ID.equals(category.id);
            if (!isDefault && ImGui.menuItem("删除分类")) {
                pendingDeleteCategoryName = categoryLabel;
                openDeleteCategoryConfirm = true;
            }
            ImGui.endPopup();
        }

        if (categoryOpen) {
            if (visiblePresets.isEmpty()) {
                ImGui.textDisabled("  （空）");
            }
            for (GraphPresetCatalog.PresetView presetView : visiblePresets) {
                renderPresetItem(presetView);
            }

            if (categoryView.isEditable()) {
                renderCategoryDropTarget(category.id);
            }
        }
        ImGui.popID();
    }

    private void renderPresetItem(GraphPresetCatalog.PresetView presetView) {
        GraphPresetRules.GraphPresetDefinition preset = presetView.preset();
        String label = preset.displayName != null ? preset.displayName : preset.id;
        boolean applicable = presetView.isApplicable();
        boolean editable = presetView.isEditable();

        ImGui.pushID("preset_" + preset.id);
        if (!applicable) {
            ImGui.beginDisabled();
        }

        ImGui.beginGroup();
        if (editable) {
            ImGui.button("::");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("拖动以调整顺序或移动到其他分类");
            }
            if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
                ImGui.setDragDropPayload(
                        GraphPresetCatalog.PRESET_REORDER_PAYLOAD,
                        preset.id.getBytes(StandardCharsets.UTF_8));
                ImGui.text("移动: " + label);
                ImGui.endDragDropSource();
            }
            ImGui.sameLine();
        }

        ImGui.selectable(label, false);
        if (applicable && ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            ImGui.setDragDropPayload(
                    GraphPresetCatalog.PRESET_DRAG_PAYLOAD,
                    preset.id.getBytes(StandardCharsets.UTF_8));
            ImGui.text("放置到画布: " + label);
            ImGui.endDragDropSource();
        }
        ImGui.endGroup();

        if (editable && ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(GraphPresetCatalog.PRESET_REORDER_PAYLOAD);
            String draggedPresetId = parsePayload(payload);
            if (draggedPresetId != null && !draggedPresetId.equals(preset.id)) {
                catalog.moveUserPresetBefore(draggedPresetId, presetView.categoryId(), preset.id);
                showStatus("已移动预设", true);
            }
            ImGui.endDragDropTarget();
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
            if (editable) {
                ImGui.textDisabled("右键：编辑 / 删除；拖 :: 可移动位置");
            }
            ImGui.endTooltip();
        }

        if (editable && ImGui.beginPopupContextItem("preset_item_ctx")) {
            renderPresetContextMenu(presetView);
            ImGui.endPopup();
        }

        if (!applicable) {
            ImGui.endDisabled();
        }
        ImGui.popID();
    }

    private void renderPresetContextMenu(GraphPresetCatalog.PresetView presetView) {
        GraphPresetRules.GraphPresetDefinition preset = presetView.preset();
        contextPresetId = preset.id;

        if (ImGui.menuItem("编辑")) {
            renameBuffer.set(preset.displayName != null ? preset.displayName : "");
            descriptionBuffer.set(preset.description != null ? preset.description : "");
            openEditPresetPopup = true;
        }

        ImGui.separator();
        if (ImGui.menuItem("删除")) {
            pendingDeletePresetName = preset.displayName != null ? preset.displayName : preset.id;
            openDeletePresetConfirm = true;
        }
    }

    private void renderCategoryDropTarget(String categoryId) {
        ImGui.spacing();
        ImGui.pushID("drop_" + categoryId + "_end");
        ImGui.selectable("  拖放到此分类末尾", false);
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(GraphPresetCatalog.PRESET_REORDER_PAYLOAD);
            String draggedPresetId = parsePayload(payload);
            if (draggedPresetId != null) {
                int endIndex = catalog.getUserCategoryPresetCount(categoryId);
                catalog.moveUserPreset(draggedPresetId, categoryId, endIndex);
                showStatus("已移动预设", true);
            }
            ImGui.endDragDropTarget();
        }
        ImGui.popID();
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

        if (openEditPresetPopup) {
            ImGui.openPopup("EditPreset");
            openEditPresetPopup = false;
        }
        if (ImGui.beginPopupModal("EditPreset", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("编辑预设");
            ImGui.text("名称");
            ImGui.setNextItemWidth(320.0f);
            boolean submit = ImGui.inputText("##edit_preset_name", renameBuffer);
            ImGui.text("描述");
            ImGui.setNextItemWidth(320.0f);
            ImGui.inputText("##edit_preset_desc", descriptionBuffer);
            ImGui.spacing();
            submit |= ImGui.button("保存", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (submit && contextPresetId != null) {
                String name = renameBuffer.get().trim();
                if (name.isEmpty()) {
                    showStatus("名称不能为空", false);
                } else if (catalog.renameUserPreset(
                        contextPresetId,
                        name,
                        descriptionBuffer.get().trim())) {
                    showStatus("已更新预设", true);
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

        if (openDeletePresetConfirm) {
            ImGui.openPopup("ConfirmDeletePreset");
            openDeletePresetConfirm = false;
        }
        if (ImGui.beginPopupModal("ConfirmDeletePreset", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = pendingDeletePresetName != null ? pendingDeletePresetName : "该预设";
            ImGui.textColored(1.0f, 0.75f, 0.2f, 1.0f, "警告");
            ImGui.textWrapped("确定要删除预设「" + name + "」吗？");
            ImGui.textWrapped("此操作无法撤销。");
            ImGui.spacing();
            boolean confirm = ImGui.button("删除", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (confirm && contextPresetId != null) {
                if (catalog.deleteUserPreset(contextPresetId)) {
                    showStatus("已删除预设", true);
                }
                contextPresetId = null;
                pendingDeletePresetName = null;
                ImGui.closeCurrentPopup();
            } else if (cancel) {
                pendingDeletePresetName = null;
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }

        if (openDeleteCategoryConfirm) {
            ImGui.openPopup("ConfirmDeleteCategory");
            openDeleteCategoryConfirm = false;
        }
        if (ImGui.beginPopupModal("ConfirmDeleteCategory", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = pendingDeleteCategoryName != null ? pendingDeleteCategoryName : "该分类";
            int presetCount = contextCategoryId != null ? catalog.getUserCategoryPresetCount(contextCategoryId) : 0;
            ImGui.textColored(1.0f, 0.75f, 0.2f, 1.0f, "警告");
            ImGui.textWrapped("确定要删除分类「" + name + "」吗？");
            if (presetCount > 0) {
                ImGui.textWrapped("分类内的 " + presetCount + " 个预设将移动到「我的预设」。");
            }
            ImGui.textWrapped("此操作无法撤销。");
            ImGui.spacing();
            boolean confirm = ImGui.button("删除", 120.0f, 0.0f);
            ImGui.sameLine();
            boolean cancel = ImGui.button("取消", 120.0f, 0.0f);
            if (confirm && contextCategoryId != null) {
                if (catalog.deleteUserCategory(contextCategoryId)) {
                    showStatus("已删除分类", true);
                }
                pendingDeleteCategoryName = null;
                ImGui.closeCurrentPopup();
            } else if (cancel) {
                pendingDeleteCategoryName = null;
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
