package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LSystemRule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic / seeded stochastic expansion of L-system strings from {@link LSystemRule} lists.
 * Weights are relative among competing rules for the same symbol.
 */
public final class LSystemStringExpander {

    /** @deprecated Use {@link GenerationLimits#MAX_LSYSTEM_EXPANDED_LENGTH}. */
    @Deprecated
    public static final int DEFAULT_MAX_EXPANDED_LENGTH = GenerationLimits.MAX_LSYSTEM_EXPANDED_LENGTH;

    private LSystemStringExpander() {
    }

    /**
     * @param error non-null when expansion failed closed (not a length Hit Limit success)
     */
    public record ExpandResult(String text, boolean hitLimit, int iterationsApplied, @Nullable String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private record CompiledRules(List<String> symbolsByLengthDesc, Map<String, List<LSystemRule>> groups) {
    }

    public static ExpandResult expand(String axiom, List<LSystemRule> rules, int iterations, long seed) {
        return expand(axiom, rules, iterations, seed, GenerationLimits.MAX_LSYSTEM_EXPANDED_LENGTH);
    }

    public static ExpandResult expand(
            String axiom,
            List<LSystemRule> rules,
            int iterations,
            long seed,
            int maxLength
    ) {
        int safeMaxLength = Math.max(1, maxLength);
        if (axiom == null) {
            return new ExpandResult("", false, 0, "Axiom is required");
        }
        if (axiom.length() > safeMaxLength) {
            return new ExpandResult("", true, 0, "Axiom exceeds MAX_LSYSTEM_EXPANDED_LENGTH");
        }
        if (iterations == 0) {
            return new ExpandResult(axiom, false, 0, null);
        }
        if (iterations < 0) {
            return new ExpandResult("", false, 0, "Iterations must be non-negative");
        }

        List<LSystemRule> sorted = new ArrayList<>(rules == null ? List.of() : rules);
        sorted.removeIf(r -> r == null || r.symbol() == null || r.symbol().isEmpty());
        sorted.sort(Comparator.comparingInt((LSystemRule r) -> r.symbol().length()).reversed());
        if (sorted.isEmpty()) {
            return new ExpandResult(axiom, false, 0, null);
        }

        String weightError = validateFiniteWeightTotals(sorted);
        if (weightError != null) {
            return new ExpandResult("", false, 0, weightError);
        }

        CompiledRules compiled = compileRuleGroups(sorted);
        Random random = new Random(seed);
        String current = axiom;
        int applied = 0;
        int ruleCount = sorted.size();
        long consumed = 0L;
        for (int it = 0; it < iterations; it++) {
            consumed = GenerationLimits.addLSystemRewriteWork(consumed, current.length(), ruleCount);
            if (consumed < 0L) {
                return new ExpandResult("", false, 0, "L-System rewrite match budget exceeded");
            }
            ExpandOnceResult next = expandOnce(current, compiled, random, safeMaxLength);
            if (next.hitLimit()) {
                return new ExpandResult(current, true, applied, null);
            }
            current = next.text();
            applied++;
        }
        return new ExpandResult(current, false, applied, null);
    }

    private static CompiledRules compileRuleGroups(List<LSystemRule> sortedByLengthDesc) {
        Map<String, List<LSystemRule>> groups = new LinkedHashMap<>();
        for (LSystemRule rule : sortedByLengthDesc) {
            groups.computeIfAbsent(rule.symbol(), key -> new ArrayList<>()).add(rule);
        }
        return new CompiledRules(List.copyOf(groups.keySet()), groups);
    }

    private static @Nullable String validateFiniteWeightTotals(List<LSystemRule> rules) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (LSystemRule rule : rules) {
            if (rule.weight() <= 0.0d) {
                continue;
            }
            String symbol = rule.symbol();
            double next = totals.getOrDefault(symbol, 0.0d) + rule.weight();
            if (!Double.isFinite(next)) {
                return "Competing rule weight sum must be finite";
            }
            totals.put(symbol, next);
        }
        return null;
    }

    private record ExpandOnceResult(String text, boolean hitLimit) {
    }

    private static ExpandOnceResult expandOnce(
            String current,
            CompiledRules compiled,
            Random random,
            int maxLength
    ) {
        StringBuilder out = new StringBuilder(Math.min(current.length() * 2, maxLength));
        int i = 0;
        while (i < current.length()) {
            String matched = null;
            for (String symbol : compiled.symbolsByLengthDesc()) {
                if (symbol.isEmpty()) {
                    continue;
                }
                if (i + symbol.length() <= current.length() && current.startsWith(symbol, i)) {
                    matched = symbol;
                    break;
                }
            }
            if (matched == null) {
                if (out.length() + 1 > maxLength) {
                    return new ExpandOnceResult(out.toString(), true);
                }
                out.append(current.charAt(i));
                i++;
                continue;
            }
            int matchLen = matched.length();
            String production = pickProduction(compiled.groups().get(matched), random);
            if (production == null) {
                if (out.length() + matchLen > maxLength) {
                    return new ExpandOnceResult(out.toString(), true);
                }
                out.append(current, i, i + matchLen);
            } else {
                int productionLength = production.length();
                if (out.length() + productionLength > maxLength) {
                    return new ExpandOnceResult(out.toString(), true);
                }
                if (productionLength > 0) {
                    out.append(production);
                }
            }
            i += matchLen;
        }
        return new ExpandOnceResult(out.toString(), false);
    }

    /**
     * @return production string, or {@code null} when all candidate weights are &lt;= 0 (keep symbol)
     */
    private static @Nullable String pickProduction(List<LSystemRule> candidates, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<LSystemRule> weighted = new ArrayList<>(candidates.size());
        double total = 0.0d;
        for (LSystemRule rule : candidates) {
            if (rule.weight() > 0.0d) {
                weighted.add(rule);
                total += rule.weight();
            }
        }
        if (weighted.isEmpty() || !Double.isFinite(total) || total <= 1.0e-12d) {
            return null;
        }
        if (weighted.size() == 1) {
            String production = weighted.getFirst().production();
            return production != null ? production : "";
        }
        double pick = random.nextDouble() * total;
        double acc = 0.0d;
        for (LSystemRule rule : weighted) {
            acc += rule.weight();
            if (pick <= acc) {
                String production = rule.production();
                return production != null ? production : "";
            }
        }
        String production = weighted.getLast().production();
        return production != null ? production : "";
    }
}
