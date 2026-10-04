package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.math.SelectionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicUtilsTest {

    @Test
    void parseBooleanAcceptsOnlyExactBooleanWhenDriven() {
        assertTrue(resultOf(LogicUtils.parseBooleanInput(Boolean.TRUE, true)));
        assertFalse(resultOf(LogicUtils.parseBooleanInput(Boolean.FALSE, true)));
        assertFalse(validOf(LogicUtils.parseBooleanInput(null, true)));
        assertFalse(validOf(LogicUtils.parseBooleanInput(1, true)));
        assertFalse(validOf(LogicUtils.parseBooleanInput("true", true)));
        assertFalse(validOf(LogicUtils.parseBooleanInput(null, false)));
    }

    @Test
    void notInvalidInputDoesNotBecomeTrue() {
        ComparisonResult result = LogicUtils.not(null, true);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = LogicUtils.not("invalid", true);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = LogicUtils.not(Boolean.FALSE, true);
        assertTrue(result.valid());
        assertTrue(result.result());
    }

    @Test
    void booleanAlgebraTruthTables() {
        assertTrue(resultOf(LogicUtils.and(true, true, true, true)));
        assertFalse(resultOf(LogicUtils.and(true, false, true, true)));

        assertTrue(resultOf(LogicUtils.or(false, true, true, true)));
        assertFalse(resultOf(LogicUtils.or(false, false, true, true)));

        assertTrue(resultOf(LogicUtils.xor(true, false, true, true)));
        assertFalse(resultOf(LogicUtils.xor(true, true, true, true)));
    }

    @Test
    void booleanAlgebraRejectsInvalidOperands() {
        assertFalse(validOf(LogicUtils.and(1, true, true, true)));
        assertFalse(validOf(LogicUtils.or(true, "x", true, true)));
        assertFalse(validOf(LogicUtils.xor(true, false, false, true)));
    }

    @Test
    void switchIndexDistinguishesInvalidFromOutOfRange() {
        LogicUtils.SwitchIndexParse invalid = LogicUtils.parseSwitchIndex(null, true);
        assertEquals(LogicUtils.SwitchIndexKind.Invalid, invalid.kind());

        LogicUtils.SwitchIndexParse outOfRange = LogicUtils.parseSwitchIndex(5, true);
        assertEquals(LogicUtils.SwitchIndexKind.OutOfRange, outOfRange.kind());
        assertEquals(5, outOfRange.index());

        LogicUtils.SwitchIndexParse valid = LogicUtils.parseSwitchIndex(2, true);
        assertEquals(LogicUtils.SwitchIndexKind.ValidIndex, valid.kind());
        assertEquals(2, valid.index());

        assertEquals(LogicUtils.SwitchIndexKind.Invalid,
                LogicUtils.parseSwitchIndex(1.9d, true).kind());
        assertEquals(LogicUtils.SwitchIndexKind.Invalid,
                LogicUtils.parseSwitchIndex("1", true).kind());
        assertEquals(LogicUtils.SwitchIndexKind.Invalid,
                LogicUtils.parseSwitchIndex(1L, true).kind());
        assertEquals(LogicUtils.SwitchIndexKind.Invalid,
                LogicUtils.parseSwitchIndex(null, false).kind());
    }

    @Test
    void selectSwitchIndexUsesDefaultForOutOfRange() {
        SelectionResult result = LogicUtils.selectSwitchIndex(
                LogicUtils.SwitchIndexParse.outOfRange(5),
                "item0", "item1", "item2", "item3", "default", true
        );
        assertTrue(result.valid());
        assertEquals("default", result.value());
    }

    @Test
    void selectSwitchIndexOutOfRangeUndrivenDefaultFailsClosed() {
        SelectionResult result = LogicUtils.selectSwitchIndex(
                LogicUtils.SwitchIndexParse.outOfRange(5),
                "item0", "item1", "item2", "item3", null, false
        );
        assertFalse(result.valid());
        assertNull(result.value());
    }

    @Test
    void selectSwitchIndexInvalidIndexFailsClosed() {
        SelectionResult result = LogicUtils.selectSwitchIndex(
                LogicUtils.SwitchIndexParse.invalid(),
                "item0", "item1", "item2", "item3", "default", true
        );
        assertFalse(result.valid());
        assertNull(result.value());
    }

    @Test
    void selectIfInvalidConditionDoesNotPickFalseBranch() {
        SelectionResult result = LogicUtils.selectIf(
                1, true,
                "true-branch", false,
                "false-branch", false
        );
        assertFalse(result.valid());
        assertNull(result.value());
    }

    @Test
    void selectIfFalseConditionSelectsFalseBranch() {
        SelectionResult result = LogicUtils.selectIf(
                false, true,
                "true-branch", true,
                "false-branch", true
        );
        assertTrue(result.valid());
        assertEquals("false-branch", result.value());
    }

    private static boolean resultOf(ComparisonResult result) {
        return result.result();
    }

    private static boolean validOf(ComparisonResult result) {
        return result.valid();
    }
}
