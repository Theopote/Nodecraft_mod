package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImDouble;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Palette swatch rows with optional block picker, weight editing, and +/- controls.
 */
public final class BlockPaletteWidget {

    private static final float SWATCH_SIZE = 14.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float WEIGHT_INPUT_WIDTH = 52.0f;
    private static final float REMOVE_BUTTON_WIDTH = 22.0f;

    private BlockPaletteWidget() {
    }

    /**
     * Live preview during typing/drag; weight edits commit via {@link #commitWeightEdit()}.
     */
    public interface PaletteEditSink {
        /** Capture undo baseline when a weight field becomes active. */
        void beginWeightEdit();

        /** Live preview while weight changes; must not record undo. */
        void preview(List<BlockPaletteEntry> entries);

        /** End weight edit — one undo from baseline captured in {@link #beginWeightEdit()}. */
        void commitWeightEdit();

        /** Immediate structural edit (picker, +/-, add) with a single undo step. */
        void commit(List<BlockPaletteEntry> entries);
    }

    public static float rowHeight() {
        return Math.max(SWATCH_SIZE + ROW_GAP, CustomUIMetricsSafe.frameHeight() + ROW_GAP);
    }

    public static float preferredHeight(BlockPaletteData palette, float padding) {
        int rows = Math.max(1, palette == null ? 1 : palette.size());
        return rows * rowHeight() + padding + CustomUIMetricsSafe.frameHeight();
    }

    public static boolean renderReadOnly(BlockPaletteData palette, float width) {
        return render(palette, width, false, null, null);
    }

    public static boolean renderEditable(
            Supplier<BlockPaletteData> paletteSupplier,
            PaletteEditSink editSink,
            float width
    ) {
        BlockPaletteData palette = paletteSupplier.get();
        return render(palette, width, true, paletteSupplier, editSink);
    }

    private static boolean render(
            @Nullable BlockPaletteData palette,
            float width,
            boolean editable,
            @Nullable Supplier<BlockPaletteData> paletteSupplier,
            @Nullable PaletteEditSink editSink
    ) {
        BlockPaletteData resolved = palette != null ? palette : BlockPaletteData.empty();
        boolean changed = false;
        float rowHeight = rowHeight();
        float pickerWidth = Math.max(72.0f, width - SWATCH_SIZE - WEIGHT_INPUT_WIDTH - REMOVE_BUTTON_WIDTH - 24.0f);

        if (resolved.isEmpty()) {
            ImGui.textDisabled(editable ? "Empty palette — add a block" : "Empty palette");
        } else {
            List<BlockPaletteEntry> entries = new ArrayList<>(resolved.entries());
            for (int i = 0; i < entries.size(); i++) {
                BlockPaletteEntry entry = entries.get(i);
                final int rowIndex = i;
                final double rowWeight = entry.weight();
                ImVec2 rowStart = ImGui.getCursorScreenPos();
                ImDrawList drawList = ImGui.getWindowDrawList();
                BlockSwatchHelper.drawSwatch(drawList, rowStart.x, rowStart.y + 1.0f, SWATCH_SIZE, entry.blockId());

                ImGui.setCursorScreenPos(rowStart.x + SWATCH_SIZE + 4.0f, rowStart.y);
                ImGui.pushID(i);
                if (editable && editSink != null && paletteSupplier != null) {
                    if (BlockPickerPopup.renderButton(
                            "palette_row_" + i,
                            entry.blockId(),
                            pickerWidth,
                            newId -> {
                                List<BlockPaletteEntry> latest = new ArrayList<>(paletteSupplier.get().entries());
                                latest.set(rowIndex, new BlockPaletteEntry(newId, rowWeight));
                                editSink.commit(latest);
                            })) {
                        changed = true;
                    }
                    ImGui.sameLine();
                    ImDouble weightInput = new ImDouble(entry.weight());
                    ImGui.setNextItemWidth(WEIGHT_INPUT_WIDTH);
                    ImGui.inputDouble("##weight", weightInput, 0.0d, 0.0d, "%.2f");
                    if (ImGui.isItemActivated()) {
                        editSink.beginWeightEdit();
                    }
                    double weight = weightInput.get();
                    if (Double.isFinite(weight) && weight >= 0.0d
                            && Math.abs(weight - entry.weight()) > 1.0e-6d) {
                        entries.set(i, new BlockPaletteEntry(entry.blockId(), weight));
                        if (ImGui.isItemActive()) {
                            editSink.preview(new ArrayList<>(entries));
                            changed = true;
                        }
                    }
                    if (ImGui.isItemDeactivatedAfterEdit()) {
                        List<BlockPaletteEntry> latest = new ArrayList<>(paletteSupplier.get().entries());
                        if (i < latest.size()) {
                            BlockPaletteEntry current = latest.get(i);
                            if (Double.isFinite(weight) && weight >= 0.0d
                                    && Math.abs(weight - current.weight()) > 1.0e-6d) {
                                latest.set(i, new BlockPaletteEntry(current.blockId(), weight));
                                editSink.preview(latest);
                            }
                        }
                        editSink.commitWeightEdit();
                        changed = true;
                    }
                    ImGui.sameLine();
                    if (ImGui.smallButton("-")) {
                        List<BlockPaletteEntry> latest = new ArrayList<>(paletteSupplier.get().entries());
                        latest.remove(i);
                        editSink.commit(latest);
                        changed = true;
                        ImGui.popID();
                        break;
                    }
                } else {
                    String label = entry.blockId();
                    if (Math.abs(entry.weight() - 1.0d) > 1.0e-6d) {
                        label += String.format(" (%.2f)", entry.weight());
                    }
                    ImGui.text(label);
                }
                ImGui.popID();

                ImGui.setCursorScreenPos(rowStart.x, rowStart.y + rowHeight);
            }
        }

        if (editable && paletteSupplier != null && editSink != null) {
            if (ImGui.button("+ Add Block", Math.min(width, 120.0f), CustomUIMetricsSafe.frameHeight())) {
                List<BlockPaletteEntry> entries = new ArrayList<>(paletteSupplier.get().entries());
                entries.add(new BlockPaletteEntry("minecraft:stone"));
                editSink.commit(entries);
                changed = true;
            }
        }

        return changed;
    }
}
