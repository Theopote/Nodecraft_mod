package com.nodecraft.gui.editor.session;

import com.nodecraft.core.NodeCraft;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Session-level editor presentation state: open flag, display mode, colors, disabled/hidden.
 * <p>
 * See {@code docs/architecture/imgui-node-editor-breakup.md} (Phase N).
 */
public final class EditorSession {

    public static final int DISPLAY_MODE_FULL = 0;
    public static final int DISPLAY_MODE_COMPACT = 1;
    public static final int DISPLAY_MODE_ICON_ONLY = 2;
    public static final int DISPLAY_MODE_TEXT_ONLY = 3;

    public interface Host {
        void notifyStructureDirty();

        void clearNodePreviewArtifacts(UUID nodeId);
    }

    private static final Host NOOP_HOST = new Host() {
        @Override
        public void notifyStructureDirty() {
        }

        @Override
        public void clearNodePreviewArtifacts(UUID nodeId) {
        }
    };

    private boolean open = false;
    private int nodeDisplayMode = DISPLAY_MODE_FULL;
    private boolean showNodePreviews = true;
    private final Map<UUID, Integer> nodeCustomColors = new HashMap<>();
    private final Set<UUID> disabledNodes = new HashSet<>();
    private final Set<UUID> hiddenNodes = new HashSet<>();

    private final Host host;

    public EditorSession() {
        this(NOOP_HOST);
    }

    public EditorSession(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public int getNodeDisplayMode() {
        return nodeDisplayMode;
    }

    public void setNodeDisplayMode(int mode) {
        this.nodeDisplayMode = mode;
        NodeCraft.LOGGER.debug("设置节点显示模式: {}", mode);
    }

    public boolean isShowNodePreviews() {
        return showNodePreviews;
    }

    public void setShowNodePreviews(boolean show) {
        this.showNodePreviews = show;
        NodeCraft.LOGGER.debug("设置节点预览显示: {}", show);
    }

    public void setNodeCustomColor(UUID nodeId, int color) {
        if (nodeId != null) {
            nodeCustomColors.put(nodeId, color);
            NodeCraft.LOGGER.debug("设置节点 {} 的自定义颜色: {}", nodeId, String.format("0x%08X", color));
        }
    }

    public @Nullable Integer getNodeCustomColor(UUID nodeId) {
        return nodeCustomColors.get(nodeId);
    }

    public void removeNodeCustomColor(UUID nodeId) {
        if (nodeId != null) {
            nodeCustomColors.remove(nodeId);
            NodeCraft.LOGGER.debug("移除节点 {} 的自定义颜色", nodeId);
        }
    }

    public boolean hasNodeCustomColor(UUID nodeId) {
        return nodeCustomColors.containsKey(nodeId);
    }

    public boolean toggleNodeDisabled(UUID nodeId) {
        if (nodeId == null) {
            return false;
        }
        boolean wasDisabled = disabledNodes.contains(nodeId);
        if (wasDisabled) {
            disabledNodes.remove(nodeId);
            NodeCraft.LOGGER.info("启用节点: {}", nodeId);
        } else {
            disabledNodes.add(nodeId);
            host.clearNodePreviewArtifacts(nodeId);
            NodeCraft.LOGGER.info("禁用节点: {}", nodeId);
        }
        host.notifyStructureDirty();
        return !wasDisabled;
    }

    public void setNodeDisabled(UUID nodeId, boolean disabled) {
        if (nodeId == null) {
            return;
        }
        if (disabled) {
            disabledNodes.add(nodeId);
            host.clearNodePreviewArtifacts(nodeId);
        } else {
            disabledNodes.remove(nodeId);
        }
        NodeCraft.LOGGER.debug("设置节点 {} 禁用状态: {}", nodeId, disabled);
        host.notifyStructureDirty();
    }

    public boolean isNodeDisabled(UUID nodeId) {
        return nodeId != null && disabledNodes.contains(nodeId);
    }

    public boolean toggleNodeVisible(UUID nodeId) {
        if (nodeId == null) {
            return true;
        }
        boolean wasHidden = hiddenNodes.contains(nodeId);
        if (wasHidden) {
            hiddenNodes.remove(nodeId);
            NodeCraft.LOGGER.info("显示节点: {}", nodeId);
        } else {
            hiddenNodes.add(nodeId);
            NodeCraft.LOGGER.info("隐藏节点: {}", nodeId);
        }
        host.notifyStructureDirty();
        return !wasHidden;
    }

    public void setNodeVisible(UUID nodeId, boolean visible) {
        if (nodeId == null) {
            return;
        }
        if (visible) {
            hiddenNodes.remove(nodeId);
        } else {
            hiddenNodes.add(nodeId);
        }
        NodeCraft.LOGGER.debug("设置节点 {} 可见性: {}", nodeId, visible);
        host.notifyStructureDirty();
    }

    public boolean isNodeVisible(UUID nodeId) {
        return nodeId == null || !hiddenNodes.contains(nodeId);
    }
}
