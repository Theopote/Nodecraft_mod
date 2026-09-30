package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.math.SelectionResult;
import com.nodecraft.nodesystem.util.StrictBooleanUtils;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for Logic v2 nodes.
 * <p>
 * Port types are the sole semantics source: no Number/String/Object coercion.
 */
final class LogicUtils {

    enum SwitchIndexKind {
        ValidIndex,
        OutOfRange,
        Invalid
    }

    record SwitchIndexParse(SwitchIndexKind kind, int index) {
        static SwitchIndexParse invalid() {
            return new SwitchIndexParse(SwitchIndexKind.Invalid, -1);
        }

        static SwitchIndexParse outOfRange(int index) {
            return new SwitchIndexParse(SwitchIndexKind.OutOfRange, index);
        }

        static SwitchIndexParse validIndex(int index) {
            return new SwitchIndexParse(SwitchIndexKind.ValidIndex, index);
        }
    }

    private LogicUtils() {
    }

    static ComparisonResult parseBooleanInput(@Nullable Object value, boolean driven) {
        if (!driven) {
            return ComparisonResult.invalid();
        }
        Boolean parsed = StrictBooleanUtils.requireExactBoolean(value);
        if (parsed == null) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(parsed);
    }

    static ComparisonResult and(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        ComparisonResult parsedLeft = parseBooleanInput(left, drivenLeft);
        ComparisonResult parsedRight = parseBooleanInput(right, drivenRight);
        if (!parsedLeft.valid() || !parsedRight.valid()) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(parsedLeft.result() && parsedRight.result());
    }

    static ComparisonResult or(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        ComparisonResult parsedLeft = parseBooleanInput(left, drivenLeft);
        ComparisonResult parsedRight = parseBooleanInput(right, drivenRight);
        if (!parsedLeft.valid() || !parsedRight.valid()) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(parsedLeft.result() || parsedRight.result());
    }

    static ComparisonResult xor(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        ComparisonResult parsedLeft = parseBooleanInput(left, drivenLeft);
        ComparisonResult parsedRight = parseBooleanInput(right, drivenRight);
        if (!parsedLeft.valid() || !parsedRight.valid()) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(parsedLeft.result() ^ parsedRight.result());
    }

    static ComparisonResult not(@Nullable Object value, boolean driven) {
        ComparisonResult parsed = parseBooleanInput(value, driven);
        if (!parsed.valid()) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(!parsed.result());
    }

    static SwitchIndexParse parseSwitchIndex(@Nullable Object value, boolean driven) {
        if (!driven) {
            return SwitchIndexParse.invalid();
        }
        Integer index = StrictIntegerUtils.requireExactInteger(value);
        if (index == null) {
            return SwitchIndexParse.invalid();
        }
        if (index >= 0 && index <= 3) {
            return SwitchIndexParse.validIndex(index);
        }
        return SwitchIndexParse.outOfRange(index);
    }

    static SelectionResult selectIf(
            @Nullable Object conditionValue,
            boolean conditionDriven,
            @Nullable Object trueValue,
            boolean trueValueDriven,
            @Nullable Object falseValue,
            boolean falseValueDriven
    ) {
        ComparisonResult condition = parseBooleanInput(conditionValue, conditionDriven);
        if (!condition.valid()) {
            return SelectionResult.invalid("Condition must be an exact Boolean");
        }

        if (condition.result()) {
            return resolveSelectedValue(trueValue, trueValueDriven, "True Value");
        }
        return resolveSelectedValue(falseValue, falseValueDriven, "False Value");
    }

    static SelectionResult selectSwitchIndex(
            SwitchIndexParse indexParse,
            @Nullable Object item0,
            @Nullable Object item1,
            @Nullable Object item2,
            @Nullable Object item3,
            @Nullable Object defaultValue
    ) {
        if (indexParse.kind() == SwitchIndexKind.Invalid) {
            return SelectionResult.invalid("Index must be an exact Integer");
        }

        Object selected = switch (indexParse.kind()) {
            case ValidIndex -> switch (indexParse.index()) {
                case 0 -> item0;
                case 1 -> item1;
                case 2 -> item2;
                case 3 -> item3;
                default -> defaultValue;
            };
            case OutOfRange -> defaultValue;
            case Invalid -> null;
        };

        return SelectionResult.ok(selected);
    }

    private static SelectionResult resolveSelectedValue(
            @Nullable Object value,
            boolean driven,
            String portName
    ) {
        if (driven && value == null) {
            return SelectionResult.invalid(portName + " is connected but invalid");
        }
        return SelectionResult.ok(value);
    }
}
