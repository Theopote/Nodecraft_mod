package com.nodecraft.gui.editor.document;

import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.preset.GraphPresetApplier;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preset.BundledPresetLocator;
import com.nodecraft.nodesystem.preset.PresetDefinition;
import com.nodecraft.nodesystem.preset.PresetInstantiator;
import com.nodecraft.nodesystem.preset.PresetRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Loads quickstart full-graph presets into a fresh editor document.
 */
public final class EditorExampleLoader {

    public static final String QUICKSTART_CATEGORY_ID = "quickstart";

    private EditorExampleLoader() {
    }

    public static void ensureQuickstartPresetsRegistered() {
        BundledPresetLocator.registerBundledQuickstartPresetsIfAbsent(PresetRegistry.getInstance());
    }

    public static List<EditorExampleEntry> listQuickstartExamples() {
        ensureQuickstartPresetsRegistered();

        List<EditorExampleEntry> examples = new ArrayList<>();
        for (PresetDefinition preset : PresetRegistry.getInstance().getPresetsByCategory(QUICKSTART_CATEGORY_ID)) {
            if (preset == null || preset.getPresetId() == null) {
                continue;
            }
            examples.add(new EditorExampleEntry(
                    preset.getPresetId(),
                    preset.getMetadata().getName(),
                    preset.getMetadata().getDescription()
            ));
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
        ensureQuickstartPresetsRegistered();
        if (presetId == null || presetId.isBlank()) {
            return GraphPresetApplier.ApplyResult.failure("示例 ID 无效");
        }

        PresetDefinition preset = PresetRegistry.getInstance().getPreset(normalizeId(presetId));
        if (preset == null) {
            return GraphPresetApplier.ApplyResult.failure("未找到示例: " + presetId);
        }
        return loadFullGraphExample(editor, preset);
    }

    private static GraphPresetApplier.ApplyResult loadFullGraphExample(
            ImGuiNodeEditor editor,
            PresetDefinition preset) {
        if (editor == null) {
            return GraphPresetApplier.ApplyResult.failure("Editor is not ready");
        }

        editor.getHistory().pauseRecording();
        try {
            PresetInstantiator.InstantiateResult instantiated = PresetInstantiator.instantiateWithLayout(preset);
            NodeGraph graph = instantiated.graph();
            graph.setName(preset.getMetadata().getName());

            editor.setCurrentGraph(graph);
            editor.setNodePositions(toEditorPositions(instantiated.nodePositions()));
            editor.getHistory().clear();
            editor.getDocument().markDirty();
            editor.setCanvasView(1.0f, 0.0f, 0.0f);

            return GraphPresetApplier.ApplyResult.success(
                    "已加载示例: " + preset.getMetadata().getName(),
                    List.copyOf(instantiated.nodePositions().keySet())
            );
        } catch (PresetInstantiator.PresetInstantiationException e) {
            return GraphPresetApplier.ApplyResult.failure("示例加载失败: " + e.getMessage());
        } finally {
            editor.getHistory().resumeRecording();
        }
    }

    private static Map<UUID, NodePosition> toEditorPositions(
            Map<UUID, PresetInstantiator.LayoutPoint> layout) {
        Map<UUID, NodePosition> positions = new HashMap<>();
        for (Map.Entry<UUID, PresetInstantiator.LayoutPoint> entry : layout.entrySet()) {
            PresetInstantiator.LayoutPoint point = entry.getValue();
            positions.put(entry.getKey(), new NodePosition(point.x(), point.y()));
        }
        return positions;
    }

    private static String normalizeId(String presetId) {
        return presetId.trim().toLowerCase(Locale.ROOT);
    }
}
