package com.nodecraft.gui.preset;

import java.util.List;
import java.util.Map;

public final class GraphPresetRules {

    public int version = 1;
    public List<PresetCategory> categories = List.of();

    public static final class PresetCategory {
        public String id;
        public String displayName;
        public List<GraphPresetDefinition> presets = List.of();
    }

    public static final class GraphPresetDefinition {
        public String id;
        public String displayName;
        public String description;
        public String kind = "placeholder";
        /** Optional teaching difficulty: beginner / intermediate / advanced. */
        public String difficulty;
        /** Free-form search tags for the preset library filter. */
        public List<String> tags = List.of();
        /** Short teaching note shown in the library tooltip. */
        public String learningNotes;
        /** Optional tip strings shown under the tooltip description. */
        public List<String> tips = List.of();
        public List<PresetNode> nodes = List.of();
        public List<PresetConnection> connections = List.of();
    }

    public static final class PresetNode {
        public String ref;
        public String typeId;
        public float x;
        public float y;
        /** Optional node property bag applied via {@link com.nodecraft.nodesystem.api.INode#setNodeState(Object)}. */
        public Map<String, Object> state;
    }

    public static final class PresetConnection {
        public String fromRef;
        public String fromPort;
        public String toRef;
        public String toPort;
    }
}
