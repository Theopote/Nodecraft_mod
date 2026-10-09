package com.nodecraft.nodesystem.recommendation;

import java.util.List;
import java.util.Map;

/**
 * In-memory model for {@code node_recommendations.json}.
 * Lives in nodesystem so semantic catalog and GUI recommendation share one core model.
 */
public final class NodeRecommendationRules {

    public int version = 1;
    public Defaults defaults = new Defaults();
    public Map<String, SourceNodeRule> sourceNodes = Map.of();
    public Map<String, SourceCategoryRule> sourceCategories = Map.of();
    public Map<String, OutputTypeRule> outputTypes = Map.of();
    public Placement placement = new Placement();

    public static final class Defaults {
        public int limit = 8;
        public List<String> workflowOrder = List.of();
        public List<String> excludeCategories = List.of();
        /** Categories hidden from Selection/Context Suggested unless an exact sourceNodes rule targets them. */
        public List<String> selectionExcludeCategories = List.of();
        /**
         * Source data-type ids that stay silent on Selection/Context unless an exact sourceNodes rule
         * targets the port (Port Drag still uses full type compatibility). Also disables workflow
         * category bonus for these types on all triggers.
         */
        public List<String> selectionSilentTypes = List.of();
        public List<String> excludeNodeIds = List.of();
    }

    public static final class SourceNodeRule {
        public Map<String, PortDirectionRule> outputs = Map.of();
        public Map<String, PortDirectionRule> inputs = Map.of();
    }

    public static final class SourceCategoryRule {
        public Map<String, PortDirectionRule> outputTypes = Map.of();
        public Map<String, PortDirectionRule> inputTypes = Map.of();
    }

    public static final class OutputTypeRule {
        public List<RuleEntry> downstream = List.of();
        public List<RuleEntry> upstream = List.of();
    }

    public static final class PortDirectionRule {
        public List<RuleEntry> downstream = List.of();
        public List<RuleEntry> upstream = List.of();
    }

    public static final class RuleEntry {
        public String nodeId;
        public int order = 0;
        public String reason;
        public String connectPortId;
    }

    public static final class Placement {
        public Offset defaultOffset = new Offset(280, 0);
        public Offset portDragOffset = new Offset(220, 0);
        public Offset stackOffset = new Offset(0, 120);
    }

    public record Offset(float dx, float dy) {
        public Offset() {
            this(280, 0);
        }
    }
}
