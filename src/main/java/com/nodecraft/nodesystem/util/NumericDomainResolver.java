package com.nodecraft.nodesystem.util;

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
        if (domainValue instanceof NumericRangeData domain) {
            return NumericRangeData.canonical(domain.start(), domain.end());
        }
        return NumericRangeData.canonical(defaultStart, defaultEnd);
    }

    public static @Nullable NumericRangeData resolveDomainOrBounds(@Nullable Object domainValue,
                                                                   @Nullable Object startValue,
                                                                   @Nullable Object endValue,
                                                                   double defaultStart,
                                                                   double defaultEnd) {
        if (domainValue instanceof NumericRangeData domain) {
            return NumericRangeData.canonical(domain.start(), domain.end());
        }
        double start = startValue instanceof Number n ? n.doubleValue() : defaultStart;
        double end = endValue instanceof Number n ? n.doubleValue() : defaultEnd;
        return NumericRangeData.canonical(start, end);
    }
}
