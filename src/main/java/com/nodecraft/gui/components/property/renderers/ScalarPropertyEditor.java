package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyConstraints;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyValueFormatters;
import com.nodecraft.gui.components.property.support.PropertyConstraintResolver;
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
        PropertyConstraints constraints = PropertyConstraintResolver.resolve(node, prop);
        boolean isReadOnly = prop.setter == null;

        switch (kind) {
            case INT -> renderInt(panel, node, prop, isReadOnly, constraints);
            case FLOAT -> renderFloat(panel, node, prop, isReadOnly, mode, constraints);
            case DOUBLE -> renderDouble(panel, node, prop, isReadOnly, mode, constraints);
            default -> throw new IllegalStateException("Unsupported scalar kind: " + kind);
        }
    }

    private static void renderInt(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isReadOnly,
            PropertyConstraints constraints
    ) throws Throwable {
        int currentValue = (int) prop.getter.invoke(node);
        if (isReadOnly) {
            PropertyEditorUi.renderReadOnlyInt(currentValue);
            return;
        }

        int[] valArr = {currentValue};
        int min = constraints.hasMin() ? (int) Math.ceil(constraints.min()) : Integer.MIN_VALUE;
        int max = constraints.hasMax() ? (int) Math.floor(constraints.max()) : Integer.MAX_VALUE;
        float speed = constraints.dragSpeedForInt();
        ImGui.dragInt("##" + prop.name, valArr, speed, min, max);
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }
        if (valArr[0] != currentValue && ImGui.isItemActive()) {
            int clamped = constraints.clampInt(valArr[0]);
            panel.applyPropertyValue(node, prop, clamped);
            NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), clamped);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
        }
    }

    private static void renderFloat(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            boolean isReadOnly,
            PropertySemanticHints.ScalarMode mode,
            PropertyConstraints constraints
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
        float min = constraints.hasMin() ? constraints.min().floatValue() : Float.NEGATIVE_INFINITY;
        float max = constraints.hasMax() ? constraints.max().floatValue() : Float.POSITIVE_INFINITY;
        float speed = constraints.dragSpeedForFloat();
        switch (mode) {
            case PERCENT -> ImGui.sliderFloat("##" + prop.name, valArr, min, max, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            case ANGLE -> ImGui.dragFloat("##" + prop.name, valArr, speed, min, max, "%.1f°");
            default -> ImGui.dragFloat("##" + prop.name, valArr, speed, min, max, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
        }

        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }
        if (valArr[0] != currentValue && ImGui.isItemActive()) {
            float clamped = constraints.clampFloat(valArr[0]);
            panel.applyPropertyValue(node, prop, clamped);
            NodeCraft.LOGGER.debug("Saved property '{}' on node {}: {}", prop.name, node.getId(), clamped);
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
        }

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
            PropertySemanticHints.ScalarMode mode,
            PropertyConstraints constraints
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
            float min = constraints.hasMin() ? constraints.min().floatValue() : 0.0f;
            float max = constraints.hasMax() ? constraints.max().floatValue() : 1.0f;
            float[] percentValue = {(float) currentValue};
            ImGui.sliderFloat("##" + prop.name, percentValue, min, max, PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT);
            if (ImGui.isItemActive()) {
                panel.refreshPropertyBeingEdited(node, prop.name);
            }
            if (PropertyValueFormatters.doubleValueChanged(percentValue[0], currentValue) && ImGui.isItemActive()) {
                double clamped = constraints.clampDouble(percentValue[0]);
                panel.applyPropertyValue(node, prop, clamped);
            }
            ImGui.sameLine();
            ImGui.text(String.format(Locale.ROOT, "%d%%", Math.round(percentValue[0] * 100.0f)));
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }
            return;
        }

        if (mode == PropertySemanticHints.ScalarMode.ANGLE) {
            float min = constraints.hasMin() ? constraints.min().floatValue() : Float.NEGATIVE_INFINITY;
            float max = constraints.hasMax() ? constraints.max().floatValue() : Float.POSITIVE_INFINITY;
            float[] angleValue = {(float) currentValue};
            ImGui.dragFloat("##" + prop.name, angleValue, constraints.dragSpeedForFloat(), min, max, "%.1f°");
            if (ImGui.isItemActive()) {
                panel.refreshPropertyBeingEdited(node, prop.name);
            }
            if (PropertyValueFormatters.doubleValueChanged(angleValue[0], currentValue) && ImGui.isItemActive()) {
                double clamped = constraints.clampDouble(angleValue[0]);
                panel.applyPropertyValue(node, prop, clamped);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }
            return;
        }

        renderPreciseDouble(panel, node, prop, currentValue, constraints);
    }

    private static void renderPreciseDouble(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            double currentValue,
            PropertyConstraints constraints
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

        ImGui.inputText(
                "##" + prop.name,
                textValue,
                ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.EnterReturnsTrue);
        boolean wasDeactivated = ImGui.isItemDeactivated();
        boolean wasBeingEdited = panel.isPropertyBeingEdited(node, prop.name);
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
        }

        boolean shouldSave = ImGui.isItemDeactivatedAfterEdit();
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
                double newValue = constraints.clampDouble(Double.parseDouble(textValue.get()));
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
        float[] dragValue = panel.getOrCreateTempValue(tempKey + ":drag", () -> new float[]{0.0f});
        ImGui.dragFloat(
                "##drag_" + prop.name,
                dragValue,
                constraints.dragSpeedForFloat(),
                0.0f,
                0.0f,
                PropertyValueFormatters.DECIMAL_DISPLAY_FORMAT
        );
        if (ImGui.isItemActive()) {
            panel.refreshPropertyBeingEdited(node, prop.name);
            double liveValue = (double) prop.getter.invoke(node);
            double newValue = constraints.clampDouble(liveValue + dragValue[0]);
            if (PropertyValueFormatters.doubleValueChanged(newValue, liveValue)) {
                textValue.set(PropertyValueFormatters.formatEditNumber(newValue));
                panel.applyPropertyValue(node, prop, newValue);
            }
            dragValue[0] = 0.0f;
        }
        if (ImGui.isItemDeactivated()) {
            panel.markPropertyEditingFinished(node, prop.name);
            dragValue[0] = 0.0f;
        }
        ImGui.popItemWidth();
    }
}
