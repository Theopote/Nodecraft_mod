package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Palette swatch rows with optional +/- editing.
 */
public final class BlockPaletteWidget {

    private static final float SWATCH_SIZE = 14.0f;
    private static final String[] QUICK_BLOCKS = {
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:oak_planks",
        "minecraft:glass",
        "minecraft:dirt",
        "minecraft:grass_block"
    };

    private BlockPaletteWidget() {
    }

    public static float preferredHeight(BlockPaletteData palette, float rowHeight, float padding) {
        int rows = Math.max(1, palette == null ? 1 : palette.size());
        return rows * rowHeight + padding + ImGui.getFrameHeight();
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
        float rowHeight = Math.max(SWATCH_SIZE + 4.0f, ImGui.getTextLineHeight() + 4.0f);

        if (resolved.isEmpty()) {
            ImGui.textDisabled(editable ? "Empty palette — add a block" : "Empty palette");
        } else {
            List<BlockPaletteEntry> entries = new ArrayList<>(resolved.entries());
            for (int i = 0; i < entries.size(); i++) {
                BlockPaletteEntry entry = entries.get(i);
                ImVec2 rowStart = ImGui.getCursorScreenPos();
                ImDrawList drawList = ImGui.getWindowDrawList();
                BlockSwatchHelper.drawSwatch(drawList, rowStart.x, rowStart.y + 1.0f, SWATCH_SIZE, entry.blockId());

                ImGui.setCursorScreenPos(rowStart.x + SWATCH_SIZE + 6.0f, rowStart.y);
                String label = entry.blockId();
                if (Math.abs(entry.weight() - 1.0d) > 1.0e-6d) {
                    label += String.format(" (%.2f)", entry.weight());
                }
                ImGui.text(label);

                if (editable) {
                    ImGui.sameLine(width - 52.0f);
                    ImGui.pushID(i);
                    if (ImGui.smallButton("-")) {
                        entries.remove(i);
                        paletteConsumer.accept(entries, true);
                        changed = true;
                        ImGui.popID();
                        break;
                    }
                    ImGui.popID();
                }

                ImGui.setCursorScreenPos(rowStart.x, rowStart.y + rowHeight);
            }
        }

        if (editable && paletteSupplier != null && paletteConsumer != null) {
            if (ImGui.button("+ Add Block", Math.min(width, 120.0f), ImGui.getFrameHeight())) {
                List<BlockPaletteEntry> entries = new ArrayList<>(paletteSupplier.get().entries());
                String next = QUICK_BLOCKS[entries.size() % QUICK_BLOCKS.length];
                entries.add(new BlockPaletteEntry(next));
                paletteConsumer.accept(entries, true);
                changed = true;
            }
        }

        return changed;
    }
}
