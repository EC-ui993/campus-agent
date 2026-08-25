package com.campus.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractedItemTest {

    @Test
    void parsesValidJson() {
        ExtractedItem item = ExtractedItem.fromJson("""
                {"type":"assignment","title":"习题5.2","course":"高数","teacher":"王老师",
                 "content":"完成5.2全部习题","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                """);
        assertEquals("assignment", item.type());
        assertEquals("习题5.2", item.title());
        assertEquals("高数", item.course());
        assertEquals("2026-11-20", item.dueDate());
        assertTrue(item.validate().isEmpty());
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"todo\",\"title\":\"领材料\",\"course\":\"  \",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        assertNull(item.course());
        assertNull(item.dueDate());
        assertTrue(item.validate().isEmpty());
    }

    @Test
    void rejectsMissingTitle() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"assignment\",\"title\":\"\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("title")), "应报 title 错误，实际: " + errors);
    }

    @Test
    void rejectsInvalidType() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"poem\",\"title\":\"随便\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("type")), "应报 type 错误，实际: " + errors);
    }

    @Test
    void rejectsBadDateFormat() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"exam\",\"title\":\"期中\",\"course\":\"\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"11月20日\",\"dueTime\":\"\"}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("dueDate")), "应报 dueDate 错误，实际: " + errors);
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> ExtractedItem.fromJson("这不是JSON"));
    }

    @Test
    void acceptsValidWeekday() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"course\",\"title\":\"高数\",\"weekday\":1}");
        assertTrue(item.validate().isEmpty());
    }

    @Test
    void rejectsInvalidWeekday() {
        ExtractedItem item = ExtractedItem.fromJson(
                "{\"type\":\"course\",\"title\":\"高数\",\"weekday\":8}");
        List<String> errors = item.validate();
        assertTrue(errors.stream().anyMatch(e -> e.contains("weekday")), "应报 weekday 错误，实际: " + errors);
    }

    @Test
    void courseOverrideTypeIsValid() {
        assertTrue(ExtractedItem.VALID_TYPES.contains("course_override"));
    }

}