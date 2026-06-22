package com.nodecraft.gui.components.panel;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.EditorComponent;
import imgui.ImGui;
import imgui.flag.ImGuiTabItemFlags;
import imgui.flag.ImGuiWindowFlags;

/**
 * Left sidebar with tabbed Node Library and Preset Library panels.
 */
public class LeftPanelComponent implements EditorComponent {

    public static final String COMPONENT_ID = "left_panel";

    private final NodeLibraryComponent nodeLibraryComponent;
    private final PresetLibraryPanel presetLibraryPanel = new PresetLibraryPanel();
    private boolean visible = true;
    private int activeTabIndex = PresetLibraryPanel.loadPreferredTabIndex();
    private boolean applyInitialTab = true;

    public LeftPanelComponent(NodeLibraryComponent.NodeSelectCallback selectCallback) {
        this.nodeLibraryComponent = new NodeLibraryComponent(selectCallback);
    }

    public NodeLibraryComponent getNodeLibraryComponent() {
        return nodeLibraryComponent;
    }

    @Override
    public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        if (!visible) {
            return;
        }

        ImGui.text("Browser");
        ImGui.separator();

        if (ImGui.beginTabBar("leftPanelTabs")) {
            int nodeLibraryTabFlags = applyInitialTab && activeTabIndex == 0
                    ? ImGuiTabItemFlags.SetSelected
                    : 0;
            if (ImGui.beginTabItem("节点库", nodeLibraryTabFlags)) {
                applyInitialTab = false;
                if (activeTabIndex != 0) {
                    activeTabIndex = 0;
                    PresetLibraryPanel.savePreferredTabIndex(activeTabIndex);
                }
                renderNodeLibraryTab(width, height, paddingX);
                ImGui.endTabItem();
            }

            int presetLibraryTabFlags = applyInitialTab && activeTabIndex == 1
                    ? ImGuiTabItemFlags.SetSelected
                    : 0;
            if (ImGui.beginTabItem("预设库", presetLibraryTabFlags)) {
                applyInitialTab = false;
                if (activeTabIndex != 1) {
                    activeTabIndex = 1;
                    PresetLibraryPanel.savePreferredTabIndex(activeTabIndex);
                }
                renderPresetLibraryTab();
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }
    }

    private void renderNodeLibraryTab(float width, float height, float paddingX) {
        if (ImGui.beginChild(
                "leftPanelNodeLibrary",
                0,
                0,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            try {
                nodeLibraryComponent.renderContent(width, height, paddingX);
            } finally {
                ImGui.endChild();
            }
        }
    }

    private void renderPresetLibraryTab() {
        if (ImGui.beginChild(
                "leftPanelPresetLibrary",
                0,
                0,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            try {
                presetLibraryPanel.render();
            } finally {
                ImGui.endChild();
            }
        }
    }

    @Override
    public void init() {
        nodeLibraryComponent.init();
        presetLibraryPanel.initialize();
        NodeCraft.LOGGER.debug("LeftPanelComponent initialized");
    }

    @Override
    public void cleanup() {
        nodeLibraryComponent.cleanup();
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        nodeLibraryComponent.setVisible(visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public String getComponentId() {
        return COMPONENT_ID;
    }

    @Override
    public boolean handleEvent(String eventType, Object data) {
        return nodeLibraryComponent.handleEvent(eventType, data);
    }
}
