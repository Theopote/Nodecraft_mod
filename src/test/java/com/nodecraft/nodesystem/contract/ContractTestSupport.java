package com.nodecraft.nodesystem.contract;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for catalog contract tests running in {@link com.nodecraft.nodesystem.api.ContractTestEnvironment#UNIT}.
 */
final class ContractTestSupport {

    private ContractTestSupport() {
    }

    static void assertRatioBelowCeiling(
            List<String> failures,
            int denominator,
            double maxRatio,
            String metricName,
            String phase
    ) {
        assertTrue(denominator > 0, "expected a positive denominator for " + metricName);
        if (maxRatio <= 0.0) {
            assertTrue(
                    failures.isEmpty(),
                    phase + " " + metricName + " must be zero (found " + failures.size()
                            + "/" + denominator + "): " + preview(failures)
            );
            return;
        }
        double ratio = failures.isEmpty() ? 0.0 : (double) failures.size() / (double) denominator;
        assertTrue(
                ratio < maxRatio,
                phase + " " + metricName + " ratio " + formatPercent(ratio)
                        + " exceeds ceiling " + formatPercent(maxRatio)
                        + " (" + failures.size() + "/" + denominator + "): "
                        + preview(failures)
        );
    }

    static String preview(List<String> items) {
        int limit = Math.min(12, items.size());
        String body = String.join("; ", items.subList(0, limit));
        if (items.size() > limit) {
            body += "; ... +" + (items.size() - limit) + " more";
        }
        return body;
    }

    static String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static String formatPercent(double ratio) {
        return String.format("%.2f%%", ratio * 100.0);
    }
}
