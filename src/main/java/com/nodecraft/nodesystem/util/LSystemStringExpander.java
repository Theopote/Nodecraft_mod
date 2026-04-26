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

    private LSystemStringExpander() {
    }

    /**
     * Expands {@code axiom} for {@code iterations} rounds. Longest matching rule symbol wins at each position.
     * Probabilities are relative weights among rules sharing the same matched symbol prefix.
     */
    public static String expand(String axiom, List<LSystemRule> rules, int iterations, long seed) {
        if (axiom == null) {
            return "";
        }
        if (iterations < 1) {
            return axiom;
        }
        List<LSystemRule> sorted = new ArrayList<>(rules == null ? List.of() : rules);
        sorted.removeIf(r -> r == null || r.getSymbol() == null || r.getSymbol().isEmpty());
        sorted.sort(Comparator.comparingInt((LSystemRule r) -> r.getSymbol().length()).reversed());
        if (sorted.isEmpty()) {
            return axiom;
        }

        Random random = new Random(seed);
        String current = axiom;
        for (int it = 0; it < iterations; it++) {
            current = expandOnce(current, sorted, random);
        }
        return current;
    }

    private static String expandOnce(String current, List<LSystemRule> sortedRules, Random random) {
        StringBuilder out = new StringBuilder(current.length() * 2);
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
            out.append(pickProduction(candidates, random));
            i += matchLen;
        }
        return out.toString();
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
