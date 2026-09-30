package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves {@link NumericRangeData} from port values and node defaults.
 * Unsafe domains (non-finite endpoints or overflow directed span) fail closed as {@code null}.
 */
public final class NumericDomainResolver {

    private NumericDomainResolver() {
    }

    public static @Nullable NumericRangeData resolveDomain(@Nullable Object domainValue,
                                                           double defaultStart,
                                                           double defaultEnd) {
        if (domainValue instanceof NumericRangeData(double start, double end)) {
            return NumericRangeData.canonical(start, end);
        }
        return NumericRangeData.canonical(defaultStart, defaultEnd);
    }

    /**
     * Connection-aware domain resolve: undriven → defaults; driven + valid → canonical;
     * driven + invalid → {@code null}.
     */
    public static @Nullable NumericRangeData resolveOptionalDomain(
            BaseNode node,
            String portId,
            double defaultStart,
            double defaultEnd
    ) {
        if (OptionalPortDrive.isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof NumericRangeData range)) {
                return null;
            }
            return NumericRangeData.canonical(range.start(), range.end());
        }
        Object injected = node.getInput(portId);
        if (injected instanceof NumericRangeData range) {
            return NumericRangeData.canonical(range.start(), range.end());
        }
        return NumericRangeData.canonical(defaultStart, defaultEnd);
    }

    public static @Nullable NumericRangeData resolveDomainOrBounds(@Nullable Object domainValue,
                                                                   @Nullable Object startValue,
                                                                   @Nullable Object endValue,
                                                                   double defaultStart,
                                                                   double defaultEnd) {
        if (domainValue instanceof NumericRangeData(double start1, double end1)) {
            return NumericRangeData.canonical(start1, end1);
        }
        double start = startValue instanceof Number n ? n.doubleValue() : defaultStart;
        double end = endValue instanceof Number n ? n.doubleValue() : defaultEnd;
        return NumericRangeData.canonical(start, end);
    }
}
