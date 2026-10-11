package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.gui.components.property.support.PropertySemanticHints;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

import java.util.Locale;

final class ScalarPropertyEditor {

    enum ValueKind {
        FLOAT,
        DOUBLE,
        INT
    }

    private ScalarPropertyEditor() {
    }

    static void render(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isDisabled,
            ValueKind kind
    ) throws Throwable {
        if (isDisabled) {
            PropertyEditorUi.renderDisabled(prop.displayName);
            return;
        }

        PropertySemanticHints.ScalarMode mode = PropertySemanticHints.scalarMode(node, prop, prop.type);
        boolean isReadOnly = prop.setter == null;

        switch (kind) {
            case INT -> renderInt(panel, node, prop, isReadOnly, mode);
            case FLOAT -> renderFloat(panel, node, prop, isReadOnly, mode);
            case DOUBLE -> renderDouble(panel, node, prop, isReadOnly, mode);
            default -> throw new IllegalStateException("Unsupported scalar kind: " + kind);
        }
    }

    private static void renderInt(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isReadOnly,
            PropertySemanticHints.ScalarMode mode
    ) throws Throwable {
        int currentValue = (int) prop.getter.invoke(node);
        if (isReadOnly) {
            PropertyEditorUi.renderReadOnlyInt(currentValue);
            return;
        }

        int[] valArr = {currentValue};
        float speed = mode == PropertySemanticHints.ScalarMode.INTEGER_COUNT ? 1f : 1f;
        if (ImGui.dragInt("##" + prop.name, valArr, speed) && valArr[0] != currentValue) {
            panel.applyPropertyValue(node, prop, valArr[0]);
            NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), valArr[0]);
        }
        trackEditSession(panel, node, prop.name);
    }

    private static void renderFloat(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isReadOnly,
            PropertySemanticHints.ScalarMode mode
    ) throws Throwable {
        float currentValue = (float) prop.getter.invoke(node);
        if (isReadOnly) {
            if (mode == PropertySemanticHints.ScalarMode.PERCENT) {
                PropertyEditorUi.renderReadOnlyText(String.format(Locale.ROOT, "%s (%d%%)",
                        PropertyValueFormatters.formatDecimal(currentValue),
                        Math.round(currentValue * 100.0f)));
            } else if (mode == PropertySemanticHints.ScalarMode.ANGLE) {
                PropertyEditorUi.renderReadOnlyText(String.format(Locale.ROOT, "%.1f°", currentValue));
            } else {
                PropertyEditorUi.renderReadOnlyFloat(currentValue);
            }
            return;
        }

        float[] valArr = {currentValue};
        boolean changed = switch (mode) {
            case PERCENT -> ImGui.sliderFloat("##" + prop.name, valArr, 0.0f, 1.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            case ANGLE -> ImGui.dragFloat("##" + prop.name, valArr, 0.5f, 0.0f, 0.0f, "%.1f°");
            default -> ImGui.dragFloat("##" + prop.name, valArr, 0.01f, 0.0f, 0.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
        };

        if (changed && valArr[0] != currentValue) {
            panel.applyPropertyValue(node, prop, valArr[0]);
            NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), valArr[0]);
        }
        trackEditSession(panel, node, prop.name);

        if (mode == PropertySemanticHints.ScalarMode.PERCENT) {
            ImGui.sameLine();
            ImGui.text(String.format(Locale.ROOT, "%d%%", Math.round(valArr[0] * 100.0f)));
        }
    }

    private static void renderDouble(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isReadOnly,
            PropertySemanticHints.ScalarMode mode
    ) throws Throwable {
        double currentValue = (double) prop.getter.invoke(node);
        if (isReadOnly) {
            if (mode == PropertySemanticHints.ScalarMode.ANGLE) {
                PropertyEditorUi.renderReadOnlyText(String.format(Locale.ROOT, "%.1f°", currentValue));
            } else {
                PropertyEditorUi.renderReadOnlyDouble(currentValue);
            }
            return;
        }

        if (mode == PropertySemanticHints.ScalarMode.PERCENT) {
            float[] percentValue = {(float) currentValue};
            if (ImGui.sliderFloat("##" + prop.name, percentValue, 0.0f, 1.0f, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT)
                    && PropertyValueFormatters.doubleValueChanged(percentValue[0], currentValue)) {
                panel.applyPropertyValue(node, prop, (double) percentValue[0]);
            }
            ImGui.sameLine();
            ImGui.text(String.format(Locale.ROOT, "%d%%", Math.round(percentValue[0] * 100.0f)));
            trackEditSession(panel, node, prop.name);
            return;
        }

        if (mode == PropertySemanticHints.ScalarMode.ANGLE) {
            float[] angleValue = {(float) currentValue};
            if (ImGui.dragFloat("##" + prop.name, angleValue, 0.5f, 0.0f, 0.0f, "%.1f°")
                    && PropertyValueFormatters.doubleValueChanged(angleValue[0], currentValue)) {
                panel.applyPropertyValue(node, prop, (double) angleValue[0]);
            }
            trackEditSession(panel, node, prop.name);
            return;
        }

        renderPreciseDouble(panel, node, prop, currentValue);
    }

    private static void renderPreciseDouble(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            double currentValue
    ) throws Throwable {
        String tempKey = panel.getTempValueKey(node, prop.name);
        ImString textValue = panel.getOrReplaceTempValue(
                tempKey,
                ImString.class,
                () -> new ImString(PropertyValueFormatters.formatEditNumber(currentValue), 64));

        if (!panel.isPropertyBeingEdited(node, prop.name)) {
            try {
                double currentTextValue = Double.parseDouble(textValue.get());
                if (PropertyValueFormatters.doubleValueChanged(currentTextValue, currentValue)) {
                    textValue.set(PropertyValueFormatters.formatEditNumber(currentValue));
                }
            } catch (NumberFormatException e) {
                textValue.set(PropertyValueFormatters.formatEditNumber(currentValue));
            }
        }

        boolean changed = ImGui.inputText(
                "##" + prop.name,
                textValue,
                ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue);
        boolean wasDeactivated = ImGui.isItemDeactivated();
        boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }

        boolean shouldSave = changed;
        if (!shouldSave && wasDeactivated && wasBeingEdited) {
            try {
                double newValue = Double.parseDouble(textValue.get());
                shouldSave = PropertyValueFormatters.doubleValueChanged(newValue, currentValue);
            } catch (NumberFormatException ignored) {
                shouldSave = false;
            }
        }

        if (shouldSave) {
            try {
                double newValue = Double.parseDouble(textValue.get());
                if (PropertyValueFormatters.doubleValueChanged(newValue, currentValue)) {
                    panel.applyPropertyValue(node, prop, newValue);
                    textValue.set(PropertyValueFormatters.formatEditNumber(newValue));
                    NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), newValue);
                }
            } catch (NumberFormatException e) {
                ImGui.sameLine();
                ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "Invalid number");
            }
        }

        if (wasDeactivated && wasBeingEdited) {
            panel.markPropertyEditingFinished(node, prop.name);
            textValue.set(PropertyValueFormatters.formatEditNumber((double) prop.getter.invoke(node)));
        }

        ImGui.sameLine();
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.25f);
        float[] dragValue = new float[]{0.0f};
        if (ImGui.dragFloat(
                "##drag_" + prop.name,
                dragValue,
                0.01f,
                0.0f,
                0.0f,
                PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT
        )) {
            double liveValue = (double) prop.getter.invoke(node);
            double newValue = liveValue + dragValue[0];
            if (PropertyValueFormatters.doubleValueChanged(newValue, liveValue)) {
                textValue.set(PropertyValueFormatters.formatEditNumber(newValue));
                panel.applyPropertyValue(node, prop, newValue);
            }
            dragValue[0] = 0.0f;
        }
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
        }
        ImGui.popItemWidth();
    }

    private static void trackEditSession(PropertyPanelComponent panel, INode node, String propName) {
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, propName);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, propName);
        }
    }
}
