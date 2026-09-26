package com.nodecraft.nodesystem.preset;

import java.util.List;

/**
 * Thumbnail and preview image paths for a preset.
 */
public record PresetThumbnails(String main, List<String> previews) {
    public PresetThumbnails(String main, List<String> previews) {
        this.main = main;
        this.previews = previews != null ? previews : List.of();
    }
}
