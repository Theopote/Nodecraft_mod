package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.datatypes.LSystemRule;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

final class LSystemRulePropertyRenderer {

    static final PropertyRenderer RENDERER = LSystemRulePropertyRenderer::render;

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
            String contextKey = panel.getTempValueKey(node, prop.name + "_context");
            String probabilityKey = panel.getTempValueKey(node, prop.name + "_probability");

            ImString symbol = panel.getOrCreateTempValue(symbolKey, () -> new ImString(rule.getSymbol(), 64));
            ImString production = panel.getOrCreateTempValue(productionKey, () -> new ImString(rule.getProduction(), 256));
            ImString context = panel.getOrCreateTempValue(
                    contextKey,
                    () -> new ImString(rule.getContext() != null ? rule.getContext() : "", 128)
            );
            float[] probability = panel.getOrCreateTempValue(probabilityKey, () -> new float[]{rule.getProbability()});

            if (!panel.isPropertyBeingEdited(node, prop.name)) {
                symbol.set(rule.getSymbol());
                production.set(rule.getProduction());
                context.set(rule.getContext() != null ? rule.getContext() : "");
                probability[0] = rule.getProbability();
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
            changed |= ImGui.inputText("Context##" + prop.name, context, ImGuiInputTextFlags.EnterReturnsTrue);
            if (ImGui.isItemActive()) {
                panel.markPropertyBeingEdited(node, prop.name);
            }
            changed |= ImGui.dragFloat("Probability##" + prop.name, probability, 0.01f, 0.0f, 1.0f, "%.2f");
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
                String contextValue = context.get().trim();
                panel.applyPropertyValue(node, prop, new LSystemRule(
                        symbol.get(),
                        production.get(),
                        Math.max(0.0f, Math.min(1.0f, probability[0])),
                        contextValue.isEmpty() ? null : contextValue
                ));
            }

            panel.clearPropertyError(prop.name);
        } catch (Throwable e) {
            panel.handlePropertyError(prop, e);
        }
    }
}
