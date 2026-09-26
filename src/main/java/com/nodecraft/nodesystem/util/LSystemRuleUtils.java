package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LSystemRule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LSystemRuleUtils {

    private LSystemRuleUtils() {
    }

    public static boolean isValidRule(@Nullable LSystemRule rule) {
        return rule != null
                && !rule.symbol().trim().isEmpty()
                && Double.isFinite(rule.weight())
                && rule.weight() >= 0.0d;
    }

    public static @Nullable List<LSystemRule> resolveStrictRuleList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<LSystemRule> rules = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            if (!(entry instanceof LSystemRule rule) || !isValidRule(rule)) {
                return null;
            }
            rules.add(rule);
        }
        return rules;
    }

    public static @Nullable List<LSystemRule> collectValidPortRules(List<LSystemRule> portRules) {
        List<LSystemRule> valid = new ArrayList<>(portRules.size());
        for (LSystemRule rule : portRules) {
            if (rule == null) {
                continue;
            }
            if (!isValidRule(rule)) {
                return null;
            }
            valid.add(rule);
        }
        return valid;
    }

    public static List<LSystemRule> mergeRules(@Nullable List<LSystemRule> listRules, List<LSystemRule> portRules) {
        List<LSystemRule> merged = new ArrayList<>();
        if (listRules != null) {
            merged.addAll(listRules);
        }
        merged.addAll(portRules);
        return merged;
    }
}
