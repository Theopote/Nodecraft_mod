package com.nodecraft.nodesystem.bake;

/**
 * 放置模式，用于 Bake（烘焙）操作时控制方块如何写入世界。
 * 对应 Grasshopper/Dynamo 中预览到永久放置的行为差异。
 */
public enum PlacementMode {
    /**
     * 覆盖模式：直接替换目标位置的所有方块。
     * 无论原位置是空气还是已有方块，都会放置新方块。
     */
    OVERWRITE("覆盖", "直接替换目标位置方块"),

    /**
     * 增量模式：仅在空气位置放置。
     * 已有方块的坐标会被跳过，不覆盖现有建筑。
     */
    INCREMENTAL("增量", "仅在空气位置放置");

    private final String displayName;
    private final String description;

    PlacementMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
