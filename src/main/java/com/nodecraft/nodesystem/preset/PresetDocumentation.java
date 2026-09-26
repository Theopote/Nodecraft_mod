package com.nodecraft.nodesystem.preset;

import java.util.List;

/**
 * Documentation and learning materials for a preset.
 */
public record PresetDocumentation(String learningNotes, List<String> tips, List<String> relatedPresets) {
    public PresetDocumentation(String learningNotes, List<String> tips, List<String> relatedPresets) {
        this.learningNotes = learningNotes;
        this.tips = tips != null ? tips : List.of();
        this.relatedPresets = relatedPresets != null ? relatedPresets : List.of();
    }
}
