package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.core.BaseNode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Result of rebuilding a {@link NodeGraph} from {@link com.nodecraft.nodesystem.io.SavedGraph}.
 */
public record GraphLoadResult(
    NodeGraph graph,
    Map<String, BaseNode> nodesBySavedId,
    int skippedUnknownNodeTypes,
    List<String> warnings
) {
    private static final String STATE_RESTORE_WARNING = "部分节点状态不兼容，已使用默认值回退加载。";

    public static GraphLoadResult empty(String graphName) {
        return new GraphLoadResult(new NodeGraph(graphName), Map.of(), 0, List.of());
    }

    public boolean hasLoadedNodes() {
        return nodesBySavedId != null && !nodesBySavedId.isEmpty();
    }

    @Nullable
    public String userMessage() {
        if (skippedUnknownNodeTypes > 0) {
            return "已部分加载：" + skippedUnknownNodeTypes + " 个节点类型未注册，已跳过。";
        }
        if (warnings != null) {
            for (String warning : warnings) {
                if (warning != null && !warning.isBlank()) {
                    return warning;
                }
            }
        }
        return null;
    }

    static void addStateRestoreWarning(List<String> warnings) {
        if (!warnings.contains(STATE_RESTORE_WARNING)) {
            warnings.add(STATE_RESTORE_WARNING);
        }
    }
}
