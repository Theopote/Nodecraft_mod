package com.nodecraft.gui.components.property.layout;

import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.components.node.NodeConstants;
import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyEditSession;
import com.nodecraft.gui.components.property.core.PropertyRenderer;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;

import java.util.List;

/**
 * Renders a list of node properties without repeating table headers for each category.
 */
public final class PropertyGroupRenderer {

    private PropertyGroupRenderer() {
    }

    public static void renderGroup(
            PropertyPanelComponent panel,
            INode node,
            List<PropertyDescriptor> props,
            String categoryInternalName,
            PropertyEditSession editSession
    ) {
        if (props == null || props.isEmpty()) {
            return;
        }

        float availableWidth = ImGui.getContentRegionAvailX();
        if (PropertyPanelMetrics.useStackedLayout(availableWidth)) {
            renderStackedGroup(panel, node, props, editSession);
            return;
        }

        if (ImGui.beginTable(
                "propertiesTable_" + categoryInternalName,
                2,
                ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersInnerV)) {
            try {
                float labelWidth = PropertyPanelMetrics.labelColumnWidth(availableWidth);
                ImGui.tableSetupColumn("label", ImGuiTableColumnFlags.WidthFixed, labelWidth);
                ImGui.tableSetupColumn("value", ImGuiTableColumnFlags.WidthStretch);

                for (PropertyDescriptor prop : props) {
                    renderPropertyRow(panel, node, prop, editSession, true);
                }
            } finally {
                ImGui.endTable();
            }
        }
    }

    private static void renderStackedGroup(
            PropertyPanelComponent panel,
            INode node,
            List<PropertyDescriptor> props,
            PropertyEditSession editSession
    ) {
        for (PropertyDescriptor prop : props) {
            ImGui.pushID(node.getId() + "_" + prop.name + "_stacked");
            ImGui.text(prop.displayName);
            renderPropertyTooltip(node, prop, editSession);
            ImGui.pushItemWidth(-1.0f);
            renderPropertyControl(panel, node, prop, editSession);
            ImGui.popItemWidth();
            ImGui.popID();
            ImGui.spacing();
        }
    }

    private static void renderPropertyRow(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            PropertyEditSession editSession,
            boolean tableLayout
    ) {
        boolean isDisabled = editSession.isPropertyDisabled(node, prop.name, NodeConstants.ERROR_THRESHOLD);

        if (tableLayout) {
            ImGui.tableNextRow();
            ImGui.tableSetColumnIndex(0);
        }

        ImGui.alignTextToFramePadding();
        ImGui.text(prop.displayName);
        renderPropertyTooltip(node, prop, editSession);

        if (tableLayout) {
            ImGui.tableSetColumnIndex(1);
        }

        String uniqueId = node.getId().toString() + "_" + prop.name;
        ImGui.pushID(uniqueId);
        ImGui.pushItemWidth(-1.0f);
        try {
            renderPropertyControl(panel, node, prop, editSession, isDisabled);
        } finally {
            ImGui.popItemWidth();
            ImGui.popID();
        }
    }

    private static void renderPropertyControl(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            PropertyEditSession editSession
    ) {
        boolean isDisabled = editSession.isPropertyDisabled(node, prop.name, NodeConstants.ERROR_THRESHOLD);
        renderPropertyControl(panel, node, prop, editSession, isDisabled);
    }

    private static void renderPropertyControl(
            PropertyPanelComponent panel,
            INode node,
            PropertyDescriptor prop,
            PropertyEditSession editSession,
            boolean isDisabled
    ) {
        try {
            PropertyRenderer renderer = prop.renderer != null ? prop.renderer : panel.getRendererForType(prop.type);
            renderer.render(panel, node, prop, isDisabled);
        } catch (Throwable e) {
            panel.handlePropertyError(node, prop, e);
        }
    }

    private static void renderPropertyTooltip(
            INode node,
            PropertyDescriptor prop,
            PropertyEditSession editSession
    ) {
        if (!ImGui.isItemHovered()) {
            return;
        }
        StringBuilder tooltip = new StringBuilder();
        if (prop.description != null && !prop.description.isEmpty()) {
            tooltip.append(prop.description);
        }
        if (prop.setter == null) {
            if (!tooltip.isEmpty()) {
                tooltip.append("\n");
            }
            tooltip.append("Read-only");
        }
        if (editSession.isPropertyDisabled(node, prop.name, NodeConstants.ERROR_THRESHOLD)) {
            if (!tooltip.isEmpty()) {
                tooltip.append("\n");
            }
            tooltip.append("Temporarily disabled after repeated errors. Reselect the node to reset.");
        }
        if (!tooltip.isEmpty()) {
            ImGui.setTooltip(tooltip.toString());
        }
    }
}
