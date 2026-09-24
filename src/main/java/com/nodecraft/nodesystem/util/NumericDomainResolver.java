package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves {@link NumericRangeData} from port values and node defaults.
 */
public final class NumericDomainResolver {

    private NumericDomainResolver() {
    }

    public static @Nullable NumericRangeData resolveDomain(@Nullable Object domainValue,
                                                           double defaultStart,
                                                           double defaultEnd) {
        if (domainValue instanceof NumericRangeData domain) {
            return domain;
        }
        return new NumericRangeData(defaultStart, defaultEnd);
    }

    public static NumericRangeData resolveDomainOrBounds(@Nullable Object domainValue,
                                                         @Nullable Object startValue,
                                                         @Nullable Object endValue,
                                                         double defaultStart,
                                                         double defaultEnd) {
        if (domainValue instanceof NumericRangeData domain) {
            return domain;
        }
        double start = startValue instanceof Number n ? n.doubleValue() : defaultStart;
        double end = endValue instanceof Number n ? n.doubleValue() : defaultEnd;
        return new NumericRangeData(start, end);
    }
}
