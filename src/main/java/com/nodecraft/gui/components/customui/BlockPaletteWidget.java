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
import java.util.function.BiConsumer;
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

    public static float rowHeight() {
        return Math.max(SWATCH_SIZE + ROW_GAP, ImGui.getFrameHeight() + ROW_GAP);
    }

    public static float preferredHeight(BlockPaletteData palette, float padding) {
        int rows = Math.max(1, palette == null ? 1 : palette.size());
        return rows * rowHeight() + padding + ImGui.getFrameHeight();
    }

    public static boolean renderReadOnly(BlockPaletteData palette, float width) {
        return render(palette, width, false, null, null);
    }

    public static boolean renderEditable(
            Supplier<BlockPaletteData> paletteSupplier,
            BiConsumer<List<BlockPaletteEntry>, Boolean> paletteConsumer,
            float width
    ) {
        BlockPaletteData palette = paletteSupplier.get();
        return render(palette, width, true, paletteSupplier, paletteConsumer);
    }

    private static boolean render(
            @Nullable BlockPaletteData palette,
            float width,
            boolean editable,
            @Nullable Supplier<BlockPaletteData> paletteSupplier,
            @Nullable BiConsumer<List<BlockPaletteEntry>, Boolean> paletteConsumer
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
                if (editable) {
                    if (BlockPickerPopup.renderButton(
                            "palette_row_" + i,
                            entry.blockId(),
                            pickerWidth,
                            newId -> {
                                entries.set(rowIndex, new BlockPaletteEntry(newId, rowWeight));
                                paletteConsumer.accept(entries, true);
                            })) {
                        changed = true;
                    }
                    ImGui.sameLine();
                    ImDouble weightInput = new ImDouble(entry.weight());
                    ImGui.setNextItemWidth(WEIGHT_INPUT_WIDTH);
                    if (ImGui.inputDouble("##weight", weightInput, 0.0d, 0.0d, "%.2f")) {
                        double weight = weightInput.get();
                        if (Double.isFinite(weight) && weight >= 0.0d) {
                            entries.set(i, new BlockPaletteEntry(entry.blockId(), weight));
                            paletteConsumer.accept(entries, true);
                            changed = true;
                        }
                    }
                    ImGui.sameLine();
                    if (ImGui.smallButton("-")) {
                        entries.remove(i);
                        paletteConsumer.accept(entries, true);
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

        if (editable && paletteSupplier != null && paletteConsumer != null) {
            if (ImGui.button("+ Add Block", Math.min(width, 120.0f), ImGui.getFrameHeight())) {
                List<BlockPaletteEntry> entries = new ArrayList<>(paletteSupplier.get().entries());
                entries.add(new BlockPaletteEntry("minecraft:stone"));
                paletteConsumer.accept(entries, true);
                changed = true;
            }
        }

        return changed;
    }
}
