package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves {@link NumericRangeData} from port values and node defaults.
 * Non-finite endpoints fail closed as {@code null}. Overflow directed span is kept
 * (Remap/Smoothstep share overflow-safe normalization).
 */
public final class NumericDomainResolver {

    private NumericDomainResolver() {
    }

    public static @Nullable NumericRangeData resolveDomain(@Nullable Object domainValue,
                                                           double defaultStart,
                                                           double defaultEnd) {
        NumericRangeData resolved = finiteEndpoints(domainValue);
        if (resolved != null) {
            return resolved;
        }
        return NumericRangeData.canonical(defaultStart, defaultEnd);
    }

    /**
     * Connection-aware domain resolve: undriven → defaults; driven + finite endpoints → keep;
     * driven + invalid → {@code null}.
     */
    public static @Nullable NumericRangeData resolveOptionalDomain(
            BaseNode node,
            String portId,
            double defaultStart,
            double defaultEnd
    ) {
        if (OptionalPortDrive.isConnected(node, portId) || node.isInputPresent(portId)) {
            return finiteEndpoints(node.getInput(portId));
        }
        return NumericRangeData.canonical(defaultStart, defaultEnd);
    }

    public static @Nullable NumericRangeData resolveDomainOrBounds(@Nullable Object domainValue,
                                                                   @Nullable Object startValue,
                                                                   @Nullable Object endValue,
                                                                   double defaultStart,
                                                                   double defaultEnd) {
        NumericRangeData resolved = finiteEndpoints(domainValue);
        if (resolved != null) {
            return resolved;
        }
        Double start = StrictDoubleUtils.requireExactFiniteDouble(startValue);
        Double end = StrictDoubleUtils.requireExactFiniteDouble(endValue);
        double resolvedStart = start != null ? start : defaultStart;
        double resolvedEnd = end != null ? end : defaultEnd;
        return NumericRangeData.canonical(resolvedStart, resolvedEnd);
    }

    private static @Nullable NumericRangeData finiteEndpoints(@Nullable Object domainValue) {
        if (!(domainValue instanceof NumericRangeData range)) {
            return null;
        }
        if (!Double.isFinite(range.start()) || !Double.isFinite(range.end())) {
            return null;
        }
        return range;
    }
}
