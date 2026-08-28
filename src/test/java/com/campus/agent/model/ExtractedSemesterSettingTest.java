package com.campus.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractedSemesterSettingTest {

    @Test
    void parsesValidJson() {
        ExtractedSemesterSetting s = ExtractedSemesterSetting.fromJson(
                "{\"type\":\"semester_setting\",\"startDate\":\"2026-09-01\",\"totalWeeks\":16,\"note\":\"\"}");
        assertEquals("2026-09-01", s.startDate());
        assertEquals(16, s.totalWeeks());
        assertTrue(s.validate().isEmpty());
    }

    @Test
    void rejectsBadDate() {
        ExtractedSemesterSetting s = ExtractedSemesterSetting.fromJson(
                "{\"type\":\"semester_setting\",\"startDate\":\"9月1日\",\"totalWeeks\":16,\"note\":\"\"}");
        assertTrue(s.validate().stream().anyMatch(e -> e.contains("startDate")));
    }

    @Test
    void rejectsBadWeekCount() {
        ExtractedSemesterSetting s = ExtractedSemesterSetting.fromJson(
                "{\"type\":\"semester_setting\",\"startDate\":\"2026-09-01\",\"totalWeeks\":99,\"note\":\"\"}");
        assertTrue(s.validate().stream().anyMatch(e -> e.contains("totalWeeks")));
    }
}
