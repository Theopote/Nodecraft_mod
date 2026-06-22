package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GraphPresetSaveDialog {

    private final ImString nameBuffer = new ImString("", 128);
    private final ImString descriptionBuffer = new ImString("", 256);
    private final ImString newCategoryBuffer = new ImString("", 64);

    private boolean openRequested;
    private Set<UUID> pendingSelection = Set.of();
    private int selectedCategoryIndex;
    private boolean createNewCategory;
    private String statusMessage = "";

    public void requestSave(Set<UUID> selection) {
        if (selection == null || selection.isEmpty()) {
            return;
        }
        pendingSelection = Set.copyOf(selection);
        nameBuffer.set("我的预设");
        descriptionBuffer.set("");
        newCategoryBuffer.set("");
        createNewCategory = false;
        selectedCategoryIndex = 0;
        statusMessage = "";
        openRequested = true;
    }

    public void render(ICanvasEditor editor) {
        if (openRequested) {
            ImGui.openPopup("Save Graph Preset");
            openRequested = false;
        }

        if (!ImGui.beginPopupModal("Save Graph Preset", ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }

        ImGui.text("将当前选区保存为预设（包含选区内的连线）。");
        ImGui.spacing();

        ImGui.text("名称");
        ImGui.setNextItemWidth(320.0f);
        boolean submit = ImGui.inputText("##preset_save_name", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue);

        ImGui.text("描述");
        ImGui.setNextItemWidth(320.0f);
        ImGui.inputText("##preset_save_desc", descriptionBuffer);

        List<GraphPresetCatalog.CategoryView> userCategories =
                GraphPresetCatalog.getInstance().getUserCategories();
        if (userCategories.isEmpty()) {
            GraphPresetCatalog.getInstance().createUserCategory("我的预设");
            userCategories = GraphPresetCatalog.getInstance().getUserCategories();
        }

        ImGui.text("分类");
        ImGui.setNextItemWidth(320.0f);
        if (ImGui.beginCombo("##preset_save_category", currentCategoryLabel(userCategories))) {
            for (int i = 0; i < userCategories.size(); i++) {
                GraphPresetCatalog.CategoryView category = userCategories.get(i);
                String label = category.category().displayName != null
                        ? category.category().displayName
                        : category.category().id;
                if (ImGui.selectable(label, selectedCategoryIndex == i)) {
                    selectedCategoryIndex = i;
                    createNewCategory = false;
                }
            }
            ImGui.endCombo();
        }

        createNewCategory = ImGui.checkbox("新建分类", createNewCategory);
        if (createNewCategory) {
            ImGui.setNextItemWidth(320.0f);
            ImGui.inputText("##preset_save_new_category", newCategoryBuffer);
        }

        if (!statusMessage.isBlank()) {
            ImGui.textColored(1.0f, 0.35f, 0.35f, 1.0f, statusMessage);
        }

        ImGui.spacing();
        submit |= ImGui.button("保存", 120.0f, 0.0f);
        ImGui.sameLine();
        boolean cancel = ImGui.button("取消", 120.0f, 0.0f);

        if (submit) {
            if (trySave(editor, userCategories)) {
                ImGui.closeCurrentPopup();
            }
        } else if (cancel) {
            statusMessage = "";
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    private String currentCategoryLabel(List<GraphPresetCatalog.CategoryView> userCategories) {
        if (userCategories.isEmpty()) {
            return "我的预设";
        }
        int index = Math.max(0, Math.min(selectedCategoryIndex, userCategories.size() - 1));
        GraphPresetCatalog.CategoryView category = userCategories.get(index);
        return category.category().displayName != null
                ? category.category().displayName
                : category.category().id;
    }

    private boolean trySave(ICanvasEditor editor, List<GraphPresetCatalog.CategoryView> userCategories) {
        String name = nameBuffer.get().trim();
        if (name.isEmpty()) {
            statusMessage = "请输入预设名称";
            return false;
        }

        GraphPresetRules.GraphPresetDefinition exported = GraphPresetExporter.exportSelection(
                editor,
                pendingSelection,
                name,
                descriptionBuffer.get().trim());
        if (exported == null) {
            statusMessage = "无法导出选区，请确认节点有效";
            return false;
        }

        String categoryId;
        if (createNewCategory) {
            String newCategoryName = newCategoryBuffer.get().trim();
            if (newCategoryName.isEmpty()) {
                statusMessage = "请输入新分类名称";
                return false;
            }
            categoryId = GraphPresetCatalog.getInstance().createUserCategory(newCategoryName);
            if (categoryId == null) {
                statusMessage = "创建分类失败";
                return false;
            }
        } else {
            if (userCategories.isEmpty()) {
                categoryId = GraphPresetCatalog.DEFAULT_USER_CATEGORY_ID;
            } else {
                int index = Math.max(0, Math.min(selectedCategoryIndex, userCategories.size() - 1));
                categoryId = userCategories.get(index).category().id;
            }
        }

        String presetId = GraphPresetCatalog.getInstance().addUserPreset(categoryId, exported);
        if (presetId == null) {
            statusMessage = "保存预设失败";
            return false;
        }

        NodeCraft.LOGGER.info("Saved user graph preset {} in category {}", presetId, categoryId);
        statusMessage = "";
        pendingSelection = Set.of();
        return true;
    }
}
