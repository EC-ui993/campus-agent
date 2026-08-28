package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record ExtractedSemesterSetting(String startDate, Integer totalWeeks, String note) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedSemesterSetting fromJson(String json) {
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("学期设置不是合法 JSON: " + e.getMessage());
        }
        return new ExtractedSemesterSetting(
                blankToNull(n.path("startDate").asText("")),
                n.path("totalWeeks").isInt() ? n.path("totalWeeks").asInt() : null,
                blankToNull(n.path("note").asText("")));
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (startDate == null || !startDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            errors.add("startDate 格式应为 yyyy-MM-dd，收到: " + startDate);
        if (totalWeeks == null || totalWeeks < 1 || totalWeeks > 30)
            errors.add("totalWeeks 应为 1-30 的整数，收到: " + totalWeeks);
        return errors;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
