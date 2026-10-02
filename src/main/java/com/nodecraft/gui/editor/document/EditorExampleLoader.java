package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.preset.GraphPresetApplier;
import com.nodecraft.gui.preset.GraphPresetCatalog;
import com.nodecraft.gui.preset.GraphPresetRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Loads quickstart full-graph presets into a fresh editor document.
 * Uses {@link GraphPresetCatalog} / {@code graph_presets.json} — the same source as the preset library panel.
 */
public final class EditorExampleLoader {

    public static final String QUICKSTART_CATEGORY_ID = "quickstart";

    private EditorExampleLoader() {
    }

    public static List<EditorExampleEntry> listQuickstartExamples() {
        GraphPresetCatalog catalog = GraphPresetCatalog.getInstance();
        List<EditorExampleEntry> examples = new ArrayList<>();

        for (GraphPresetCatalog.CategoryView categoryView : catalog.getCategories()) {
            if (categoryView.category() == null
                    || !QUICKSTART_CATEGORY_ID.equals(categoryView.category().id)) {
                continue;
            }
            if (categoryView.category().presets == null) {
                continue;
            }
            for (GraphPresetRules.GraphPresetDefinition preset : categoryView.category().presets) {
                if (preset == null || preset.id == null) {
                    continue;
                }
                if (!"composite".equalsIgnoreCase(preset.kind)) {
                    continue;
                }
                examples.add(new EditorExampleEntry(
                        preset.id,
                        preset.displayName,
                        preset.description
                ));
            }
        }

        examples.sort(Comparator.comparing(EditorExampleEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(examples);
    }

    public static @Nullable EditorExampleEntry findQuickstartEntry(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return null;
        }
        String normalized = normalizeId(presetId);
        return listQuickstartExamples().stream()
                .filter(entry -> normalizeId(entry.id()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public static GraphPresetApplier.ApplyResult loadQuickstartExample(ImGuiNodeEditor editor, String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return GraphPresetApplier.ApplyResult.failure("示例 ID 无效");
        }

        GraphPresetCatalog.PresetView view = GraphPresetCatalog.getInstance().findPreset(normalizeId(presetId));
        if (view == null || !QUICKSTART_CATEGORY_ID.equals(view.categoryId())) {
            return GraphPresetApplier.ApplyResult.failure("未找到示例: " + presetId);
        }
        if (!view.isApplicable()) {
            return GraphPresetApplier.ApplyResult.failure("示例不可用: " + presetId);
        }

        return GraphPresetApplier.loadAsNewDocument(editor, view.preset());
    }

    private static String normalizeId(String presetId) {
        return presetId.trim().toLowerCase(Locale.ROOT);
    }
}
