package com.nodecraft.gui.components.property.renderers;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

public final class LSystemRulePropertyRenderer {
    public static final PropertyRenderer RENDERER = LSystemRulePropertyRenderer::render;

    private LSystemRulePropertyRenderer() {
    }

    private static void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled) {
        if (isDisabled) {
            ImGui.textDisabled("(disabled)");
            return;
        }

        try {
            LSystemRule rule = (LSystemRule) prop.getter.invoke(node);
            if (rule == null) {
                ImGui.textDisabled("(null)");
                return;
            }

            boolean isReadOnly = prop.setter == null;
            String symbolKey = panel.getTempValueKey(node, prop.name + "_symbol");
            String productionKey = panel.getTempValueKey(node, prop.name + "_production");
            String weightKey = panel.getTempValueKey(node, prop.name + "_weight");

            ImString symbol = panel.getOrCreateTempValue(symbolKey, () -> new ImString(rule.symbol(), 64));
            ImString production = panel.getOrCreateTempValue(productionKey, () -> new ImString(rule.production(), 256));
            float[] weight = panel.getOrCreateTempValue(weightKey, () -> new float[]{(float) rule.weight()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                symbol.set(rule.symbol());
                production.set(rule.production());
                weight[0] = (float) rule.weight();
            }

            if (isReadOnly) {
                ImGui.beginDisabled();
            }
            boolean changed = false;
            changed |= ImGui.inputText("Symbol##" + prop.name, symbol, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.inputText("Production##" + prop.name, production, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.dragFloat("Weight##" + prop.name, weight, 0.01f, 0.0f, 100.0f, "%.3f");
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            if (ImGui.isItemDeactivated()) {
                panel.markPropertyEditingFinished(node, prop.name);
            }
            if (isReadOnly) {
                ImGui.endDisabled();
            }

            if (!isReadOnly && changed) {
                panel.applyPropertyValue(node, prop, new LSystemRule(
                        symbol.get(),
                        production.get(),
                        Math.max(0.0d, weight[0])
                ));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}
