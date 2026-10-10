package com.nodecraft.gui.ai.compose;

import com.nodecraft.gui.ai.model.AiGraphPlan;
import com.nodecraft.nodesystem.semantic.NodeCapability;

import java.util.List;
import java.util.Set;

public record AiComposeResult(
        boolean success,
        boolean abstained,
        String abstainCode,
        String message,
        AiGraphPlan plan,
        Set<NodeCapability> covered,
        Set<NodeCapability> missing,
        List<String> reasons,
        double totalCost
) {
    public static final String ABSTAIN_CODE = "semantic_composer_no_confident_plan";

    public AiComposeResult {
        covered = covered == null ? Set.of() : Set.copyOf(covered);
        missing = missing == null ? Set.of() : Set.copyOf(missing);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        message = message == null ? "" : message;
    }

    public static AiComposeResult success(
            AiGraphPlan plan,
            Set<NodeCapability> covered,
            List<String> reasons,
            double totalCost
    ) {
        String message = reasons == null || reasons.isEmpty()
                ? "Composer built a Preview-first plan."
                : String.join(" ", reasons);
        return new AiComposeResult(true, false, null, message, plan, covered, Set.of(), reasons, totalCost);
    }

    public static AiComposeResult abstain(String message) {
        return abstain(ABSTAIN_CODE, message, Set.of(), Set.of());
    }

    public static AiComposeResult abstain(
            String code,
            String message,
            Set<NodeCapability> covered,
            Set<NodeCapability> missing
    ) {
        return new AiComposeResult(false, true, code, message, null, covered, missing, List.of(), 0);
    }

    public boolean isSuccess() {
        return success && !abstained && plan != null && plan.isValid();
    }

    /** Compatibility alias used by older call sites. */
    public String abstainMessage() {
        return abstained ? message : null;
    }
}
