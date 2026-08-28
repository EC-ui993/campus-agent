package com.campus.agent.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeekRangesTest {

    @Test
    void parseAndMatchRanges() {
        assertTrue(WeekRanges.contains("1-16", 3));
        assertFalse(WeekRanges.contains("1-16", 17));
        assertTrue(WeekRanges.contains("1-8,10-16", 10));
        assertFalse(WeekRanges.contains("1-8,10-16", 9));
        assertTrue(WeekRanges.contains("5", 5));
        assertFalse(WeekRanges.contains("5", 6));
        assertTrue(WeekRanges.contains("1-16单", 3));
        assertFalse(WeekRanges.contains("1-16单", 4));
        assertTrue(WeekRanges.contains("1-16双", 4));
        assertFalse(WeekRanges.contains("1-16双", 3));
        assertTrue(WeekRanges.contains("", 3), "空=全程");
        assertTrue(WeekRanges.contains(null, 3), "null=全程");
    }
}
