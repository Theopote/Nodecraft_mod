package com.nodecraft.gui.components.search;

import java.util.function.Consumer;

import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.registry.NodeRegistry.NodeCategory;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;

/**
 * Node search bar state and highlight helpers for the node library.
 */
public class NodeSearchManager {
    public static final int SEARCH_QUERY_BUFFER_SIZE = 256;
    public static final String SEARCH_HINT_TEXT = "Search nodes...";

    private final ImString searchQuery;
    private String lastSearchTerm = "";

    public NodeSearchManager() {
        this.searchQuery = new ImString("", SEARCH_QUERY_BUFFER_SIZE);
    }

    /**
     * Renders the search bar.
     *
     * @return true when the search term changed
     */
    public boolean renderSearchBar(Consumer<String> onSearchChanged) {
        ImGui.pushItemWidth(-1);

        ImGui.pushStyleColor(ImGuiCol.FrameBg, ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1.0f));
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1.0f));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1.0f));

        String oldSearchContent = this.searchQuery.get();
        boolean searchChanged = ImGui.inputTextWithHint("##searchNodes", SEARCH_HINT_TEXT, searchQuery);

        ImGui.popStyleColor(3);
        ImGui.popItemWidth();

        String newSearchContent = this.searchQuery.get();
        boolean contentChanged = !oldSearchContent.equals(newSearchContent);
        if (contentChanged) {
            searchChanged = true;
        }

        String currentSearchTerm = searchQuery.get().toLowerCase();
        boolean termChanged = !currentSearchTerm.equals(lastSearchTerm);

        if (termChanged || searchChanged) {
            lastSearchTerm = currentSearchTerm;
            if (onSearchChanged != null) {
                onSearchChanged.accept(currentSearchTerm);
            }
            return true;
        }

        return false;
    }

    public boolean matchesNode(NodeInfo node, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return true;
        }
        return NodeSearchMatcher.matchesNode(node, searchTerm);
    }

    public boolean matchesCategory(NodeCategory category, String searchTerm) {
        if (category == null) {
            return false;
        }
        if (searchTerm == null || searchTerm.isEmpty()) {
            return true;
        }
        return NodeSearchMatcher.matchesCategory(category.getId(), category.getDisplayName(), searchTerm);
    }

    public void renderHighlightedText(ImDrawList drawList, String text, float x, float y,
                                      String searchTerm, int defaultColor, int highlightColor) {
        if (text == null || text.isEmpty() || searchTerm == null || searchTerm.isEmpty()) {
            drawList.addText(x, y, defaultColor, text);
            return;
        }

        String lowerText = text.toLowerCase();
        String lowerSearchTerm = searchTerm.toLowerCase().trim();
        if (lowerSearchTerm.isEmpty() || !lowerText.contains(lowerSearchTerm)) {
            drawList.addText(x, y, defaultColor, text);
            return;
        }

        int matchPos = lowerText.indexOf(lowerSearchTerm);
        int matchEnd = matchPos + lowerSearchTerm.length();
        float currentX = x;

        if (matchPos > 0) {
            String prefix = text.substring(0, matchPos);
            drawList.addText(currentX, y, defaultColor, prefix);
            currentX += ImGui.calcTextSize(prefix).x;
        }

        String match = text.substring(matchPos, matchEnd);
        drawList.addText(currentX, y, highlightColor, match);
        currentX += ImGui.calcTextSize(match).x;

        if (matchEnd < text.length()) {
            String suffix = text.substring(matchEnd);
            drawList.addText(currentX, y, defaultColor, suffix);
        }
    }

    public void renderNoMatchesMessage() {
        if (!lastSearchTerm.isEmpty()) {
            ImGui.text("  No matching nodes");
            ImGui.spacing();
            ImGui.text("  Tips:");
            ImGui.bullet();
            ImGui.text("Try a shorter keyword");
            ImGui.bullet();
            ImGui.text("Match against name, id, or category");
            ImGui.bullet();
            ImGui.text("Use multiple tokens: wall stair");
        }
    }

    public String getSearchTerm() {
        return lastSearchTerm;
    }

    public void clearSearch() {
        searchQuery.set("");
        lastSearchTerm = "";
    }
}
