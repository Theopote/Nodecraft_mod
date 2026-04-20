package com.nodecraft.gui.components;

import com.nodecraft.nodesystem.api.INode;

@FunctionalInterface
interface PropertyRenderer {
    void render(PropertyPanelComponent panel, INode node, PropertyDescriptor prop, boolean isDisabled);
}
