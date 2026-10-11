package com.nodecraft.gui.components.property.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListPagingTest {

    private static final int PAGE = 32;

    @Test
    void emptyListStartsAtZero() {
        assertEquals(0, ListPaging.maxStart(0, PAGE));
        assertEquals(0, ListPaging.clampStart(0, PAGE, 10));
    }

    @Test
    void thirtyThreeItemsSecondPageStartsAtThirtyTwo() {
        assertEquals(32, ListPaging.maxStart(33, PAGE));
        assertEquals(32, ListPaging.clampStart(33, PAGE, 40));
        assertEquals(0, ListPaging.clampStart(33, PAGE, 10));
    }

    @Test
    void sixtyFourItemsExactlyTwoPages() {
        assertEquals(32, ListPaging.maxStart(64, PAGE));
        assertEquals(32, ListPaging.clampStart(64, PAGE, 32));
    }

    @Test
    void sixtyFiveItemsThirdPageStartsAtSixtyFour() {
        assertEquals(64, ListPaging.maxStart(65, PAGE));
        assertEquals(64, ListPaging.clampStart(65, PAGE, 100));
    }

    @Test
    void shrinkingListAlignsToLastFullPageStart() {
        assertEquals(64, ListPaging.clampStart(100, PAGE, 64));
        // list shrinks to 40 → last page start is 32, not 39
        assertEquals(32, ListPaging.clampStart(40, PAGE, 64));
        assertEquals(0, ListPaging.clampStart(10, PAGE, 32));
    }

    @Test
    void nonAlignedStartSnapsDownToPageBoundary() {
        assertEquals(32, ListPaging.clampStart(100, PAGE, 40));
        assertEquals(0, ListPaging.clampStart(100, PAGE, 7));
    }
}
