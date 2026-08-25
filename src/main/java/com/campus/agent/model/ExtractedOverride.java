package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 课程临时变动的抽取结果（独立于 ExtractedItem，字段语义不同）。 */
public record ExtractedOverride(
        String courseTitle, String overrideDate, String kind,
        String newStartTime, String newEndTime, String newLocation, String note) {

    public static final Set<String> VALID_KINDS = Set.of("cancel", "move");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedOverride fromJson(String json) {
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("变动抽取结果不是合法 JSON: " + e.getMessage());
        }
        return new ExtractedOverride(
                blankToNull(n.path("courseTitle").asText("")),
                blankToNull(n.path("overrideDate").asText("")),
                blankToNull(n.path("kind").asText("")),
                blankToNull(n.path("newStartTime").asText("")),
                blankToNull(n.path("newEndTime").asText("")),
                blankToNull(n.path("newLocation").asText("")),
                blankToNull(n.path("note").asText("")));
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (courseTitle == null || courseTitle.isBlank()) errors.add("courseTitle 不能为空（要变动的课程名）");
        if (overrideDate == null || !overrideDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            errors.add("overrideDate 格式应为 yyyy-MM-dd，收到: " + overrideDate);
        if (kind == null || !VALID_KINDS.contains(kind)) errors.add("kind 必须是 cancel 或 move，收到: " + kind);
        if (newStartTime != null && !newStartTime.matches("\\d{2}:\\d{2}"))
            errors.add("newStartTime 格式应为 HH:mm，收到: " + newStartTime);
        if (newEndTime != null && !newEndTime.matches("\\d{2}:\\d{2}"))
            errors.add("newEndTime 格式应为 HH:mm，收到: " + newEndTime);
        return errors;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
