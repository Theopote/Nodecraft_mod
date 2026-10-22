package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.nodes.material.block_state.BlockStateValidationUtils;
import imgui.ImGui;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Compact properties text editor with optional enum combos for common block properties.
 */
public final class BlockStateCompactWidget {

    private static final int TEXT_BUF = 512;
    private static final String[] COMMON_PROPERTIES = {"facing", "axis", "half", "waterlogged"};
    private static final float MULTILINE_HEIGHT_FACTOR = 2.5f;
    private static final float COMBO_ROW_SPACING = 2.0f;

    private BlockStateCompactWidget() {
    }

    public static float multilineEditorHeight(float frameHeight) {
        return frameHeight * MULTILINE_HEIGHT_FACTOR;
    }

    public static int countVisiblePropertyRows(@Nullable String blockType) {
        if (blockType == null || blockType.isBlank() || !BlockStateValidationUtils.containsBlockId(blockType)) {
            return 0;
        }
        int count = 0;
        for (String propertyName : COMMON_PROPERTIES) {
            if (!listPropertyValues(blockType, propertyName).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static float preferredHeight(
            @Nullable String blockType,
            boolean editable,
            @Nullable String propertiesText,
            float frameHeight,
            float textLineHeight,
            float contentWidth,
            float padding
    ) {
        if (!editable) {
            float height = textLineHeight + estimateWrappedTextHeight(propertiesText, contentWidth, textLineHeight);
            return height + padding;
        }
        float height = multilineEditorHeight(frameHeight) + padding;
        height += countVisiblePropertyRows(blockType) * (frameHeight + COMBO_ROW_SPACING);
        return height;
    }

    private static float estimateWrappedTextHeight(
            @Nullable String text,
            float contentWidth,
            float textLineHeight
    ) {
        String display = text == null || text.isBlank() ? "(no overrides)" : text;
        float charWidth = Math.max(4.0f, textLineHeight * 0.55f);
        int charsPerLine = Math.max(8, (int) (contentWidth / charWidth));
        int lines = Math.max(1, (display.length() + charsPerLine - 1) / charsPerLine);
        return lines * textLineHeight;
    }

    public static boolean renderPropertiesText(
            ImString buffer,
            String propertiesText,
            boolean editable,
            Consumer<String> onTextChanged,
            float width
    ) {
        if (!editable) {
            ImGui.textWrapped(propertiesText == null || propertiesText.isBlank()
                ? "(no overrides)"
                : propertiesText);
            return false;
        }

        if (!ImGui.isAnyItemActive() && !propertiesText.equals(buffer.get())) {
            buffer.set(propertiesText);
        }

        ImGui.setNextItemWidth(Math.max(64.0f, width));
        if (ImGui.inputTextMultiline(
                "##properties_text",
                buffer,
                width,
                multilineEditorHeight(ImGui.getFrameHeight()))) {
            onTextChanged.accept(buffer.get());
            return true;
        }
        return false;
    }

    public static boolean renderEnumShortcuts(
            @Nullable String blockType,
            boolean editable,
            Consumer<String> propertiesTextUpdater,
            String currentPropertiesText
    ) {
        if (!editable || blockType == null || blockType.isBlank()) {
            return false;
        }
        if (!BlockStateValidationUtils.containsBlockId(blockType)) {
            ImGui.textDisabled("Connect a valid block type for property shortcuts");
            return false;
        }

        boolean changed = false;
        String text = currentPropertiesText == null ? "" : currentPropertiesText;
        for (String propertyName : COMMON_PROPERTIES) {
            List<String> values = listPropertyValues(blockType, propertyName);
            if (values.isEmpty()) {
                continue;
            }
            String current = readPropertyValue(text, propertyName);
            int index = current == null ? 0 : Math.max(0, values.indexOf(current));
            ImGui.setNextItemWidth(120.0f);
            if (ImGui.beginCombo("##prop_" + propertyName, current != null ? current : values.getFirst())) {
                for (int i = 0; i < values.size(); i++) {
                    boolean selected = i == index;
                    if (ImGui.selectable(values.get(i), selected)) {
                        text = upsertProperty(text, propertyName, values.get(i));
                        propertiesTextUpdater.accept(text);
                        changed = true;
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
            ImGui.sameLine();
            ImGui.text(propertyName);
        }
        return changed;
    }

    private static @Nullable String readPropertyValue(String text, String propertyName) {
        if (text.isBlank()) {
            return null;
        }
        for (String pair : text.split(",")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(propertyName)) {
                return kv[1].trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static String upsertProperty(String text, String propertyName, String value) {
        List<String> pairs = new ArrayList<>();
        boolean replaced = false;
        if (!text.isBlank()) {
            for (String pair : text.split(",")) {
                String trimmed = pair.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] kv = trimmed.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(propertyName)) {
                    pairs.add(propertyName + "=" + value);
                    replaced = true;
                } else {
                    pairs.add(trimmed);
                }
            }
        }
        if (!replaced) {
            pairs.add(propertyName + "=" + value);
        }
        return String.join(",", pairs);
    }

    private static List<String> listPropertyValues(String blockType, String propertyName) {
        List<String> values = new ArrayList<>();
        try {
            Identifier id = Identifier.of(blockType.trim().toLowerCase(Locale.ROOT));
            if (!Registries.BLOCK.containsId(id)) {
                return values;
            }
            Block block = Registries.BLOCK.get(id);
            Property<?> property = findProperty(block, propertyName);
            if (property == null) {
                return values;
            }
            addPropertyValueNames(property, values);
        } catch (Throwable ignored) {
            // Headless / registry unavailable
        }
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPropertyValueNames(Property<?> property, List<String> values) {
        for (Object value : property.getValues()) {
            values.add(((Property) property).name((Comparable) value));
        }
    }

    private static @Nullable Property<?> findProperty(Block block, String name) {
        for (Property<?> property : block.getDefaultState().getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }
}
