package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.datatypes.DataTreeData;
import imgui.ImGui;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bounded read-only branch list preview for data trees.
 */
public final class TreePreviewWidget {

    private TreePreviewWidget() {
    }

    public static float preferredHeight(@Nullable DataTreeData tree, int maxLines, float lineHeight, float padding) {
        if (tree == null || tree.getBranchCount() == 0) {
            return lineHeight + padding;
        }
        int lines = Math.min(tree.getBranchCount(), maxLines);
        return lines * lineHeight + lineHeight + padding;
    }

    public static void render(
            @Nullable DataTreeData tree,
            @Nullable DataTreeData.TreePreview preview,
            int maxLines
    ) {
        if (tree == null || tree.getBranchCount() == 0) {
            ImGui.textDisabled("No tree connected");
            return;
        }

        ImGui.text(String.format(
                "Branches: %d  Items: %d  Depth: %d",
                tree.getBranchCount(),
                tree.getItemCount(),
                tree.getMaxDepth()));

        List<DataTreeData.Branch> branches = tree.getBranches();
        int shown = 0;
        for (DataTreeData.Branch branch : branches) {
            if (shown >= maxLines) {
                break;
            }
            ImGui.text(formatBranchLine(branch));
            shown++;
        }

        boolean truncated = preview != null && preview.truncated();
        if (truncated || shown < tree.getBranchCount()) {
            ImGui.textDisabled("... preview truncated");
        }
    }

    private static String formatBranchLine(DataTreeData.Branch branch) {
        List<Integer> path = branch.path();
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(path.get(i));
        }
        builder.append("}: ").append(branch.items().size()).append(" items");
        return builder.toString();
    }
}
