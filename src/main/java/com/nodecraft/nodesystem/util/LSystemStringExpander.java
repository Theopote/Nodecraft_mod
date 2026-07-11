package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LSystemRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Deterministic / seeded stochastic expansion of L-system strings from {@link LSystemRule} lists.
 * Context-sensitive fields on rules are ignored in this MVP.
 */
public final class LSystemStringExpander {

    public static final int DEFAULT_MAX_EXPANDED_LENGTH = 1_000_000;

    private LSystemStringExpander() {
    }

    public record ExpandResult(String text, boolean hitLimit, int iterationsApplied) {
    }

    /**
     * Expands {@code axiom} for {@code iterations} rounds. Longest matching rule symbol wins at each position.
     * Probabilities are relative weights among rules sharing the same matched symbol prefix.
     */
    public static ExpandResult expand(String axiom, List<LSystemRule> rules, int iterations, long seed) {
        return expand(axiom, rules, iterations, seed, DEFAULT_MAX_EXPANDED_LENGTH);
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
            return new ExpandResult("", false, 0);
        }
        if (axiom.length() > safeMaxLength) {
            return new ExpandResult("", true, 0);
        }
        if (iterations < 1) {
            return new ExpandResult(axiom, false, 0);
        }

        List<LSystemRule> sorted = new ArrayList<>(rules == null ? List.of() : rules);
        sorted.removeIf(r -> r == null || r.getSymbol() == null || r.getSymbol().isEmpty());
        sorted.sort(Comparator.comparingInt((LSystemRule r) -> r.getSymbol().length()).reversed());
        if (sorted.isEmpty()) {
            return new ExpandResult(axiom, false, 0);
        }

        Random random = new Random(seed);
        String current = axiom;
        int applied = 0;
        for (int it = 0; it < iterations; it++) {
            ExpandOnceResult next = expandOnce(current, sorted, random, safeMaxLength);
            if (next.hitLimit()) {
                return new ExpandResult(current, true, applied);
            }
            current = next.text();
            applied++;
        }
        return new ExpandResult(current, false, applied);
    }

    private record ExpandOnceResult(String text, boolean hitLimit) {
    }

    private static ExpandOnceResult expandOnce(
            String current,
            List<LSystemRule> sortedRules,
            Random random,
            int maxLength
    ) {
        StringBuilder out = new StringBuilder(Math.min(current.length() * 2, maxLength));
        int i = 0;
        while (i < current.length()) {
            LSystemRule chosen = null;
            int matchLen = 0;
            for (LSystemRule rule : sortedRules) {
                String sym = rule.getSymbol();
                if (sym.isEmpty()) {
                    continue;
                }
                if (i + sym.length() <= current.length() && current.startsWith(sym, i)) {
                    chosen = rule;
                    matchLen = sym.length();
                    break;
                }
            }
            if (chosen == null) {
                if (out.length() + 1 > maxLength) {
                    return new ExpandOnceResult(out.toString(), true);
                }
                out.append(current.charAt(i));
                i++;
                continue;
            }
            List<LSystemRule> candidates = new ArrayList<>();
            for (LSystemRule rule : sortedRules) {
                if (rule.getSymbol().equals(chosen.getSymbol())) {
                    candidates.add(rule);
                }
            }
            String production = pickProduction(candidates, random);
            int productionLength = production != null ? production.length() : 0;
            if (out.length() + productionLength > maxLength) {
                return new ExpandOnceResult(out.toString(), true);
            }
            if (productionLength > 0) {
                out.append(production);
            }
            i += matchLen;
        }
        return new ExpandOnceResult(out.toString(), false);
    }

    private static String pickProduction(List<LSystemRule> candidates, Random random) {
        if (candidates.isEmpty()) {
            return "";
        }
        if (candidates.size() == 1) {
            return candidates.getFirst().getProduction() != null ? candidates.getFirst().getProduction() : "";
        }
        double total = 0.0d;
        for (LSystemRule r : candidates) {
            total += Math.max(0.0d, r.getProbability());
        }
        if (total <= 1.0e-12d) {
            LSystemRule r = candidates.getFirst();
            return r.getProduction() != null ? r.getProduction() : "";
        }
        double pick = random.nextDouble() * total;
        double acc = 0.0d;
        for (LSystemRule r : candidates) {
            acc += Math.max(0.0d, r.getProbability());
            if (pick <= acc) {
                return r.getProduction() != null ? r.getProduction() : "";
            }
        }
        LSystemRule last = candidates.getLast();
        return last.getProduction() != null ? last.getProduction() : "";
    }
}
