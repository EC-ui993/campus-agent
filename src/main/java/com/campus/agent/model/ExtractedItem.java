package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LLM 抽取结果。type 取值：assignment 作业 / exam 考试 / todo 待办 / course 课程 / event 日程。
 * 除 type、title 外的字段可为 null（表示无该信息）。
 */
public record ExtractedItem(
        String type, String title, String course, String teacher,
        String content, String location, String dueDate, String dueTime,
        String startTime, String endTime, Integer weekday, String weeks) {

    public static final Set<String> VALID_TYPES =
            Set.of("assignment", "exam", "todo", "course", "event","course_override");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedItem fromJson(String json) {
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("抽取结果不是合法 JSON: " + e.getMessage());
        }
        return new ExtractedItem(
                blankToNull(n.path("type").asText("")),
                blankToNull(n.path("title").asText("")),
                blankToNull(n.path("course").asText("")),
                blankToNull(n.path("teacher").asText("")),
                blankToNull(n.path("content").asText("")),
                blankToNull(n.path("location").asText("")),
                blankToNull(n.path("dueDate").asText("")),
                blankToNull(n.path("dueTime").asText("")),
                blankToNull(n.path("startTime").asText("")),
                blankToNull(n.path("endTime").asText("")),
                (n.path("weekday").asInt(0) == 0 ? null : n.path("weekday").asInt(0)),
                blankToNull(n.path("weeks").asText("")));
    }

    /** 校验抽取结果；返回错误列表，空列表表示合法。 */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (type == null || !VALID_TYPES.contains(type)) {
            errors.add("type 必须是 assignment/exam/todo/course/event/course_override 之一，收到: " + type);
        }
        if (title == null || title.isBlank()) {
            errors.add("title 不能为空");
        }
        if (dueDate != null && !dueDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            errors.add("dueDate 格式应为 yyyy-MM-dd，收到: " + dueDate);
        }
        if (dueTime != null && !dueTime.matches("\\d{2}:\\d{2}")) {
            errors.add("dueTime 格式应为 HH:mm，收到: " + dueTime);
        }
        if (startTime != null && !startTime.matches("\\d{2}:\\d{2}")) {
            errors.add("startTime 格式应为 HH:mm，收到: " + startTime);
        }
        if (endTime != null && !endTime.matches("\\d{2}:\\d{2}")) {
            errors.add("endTime 格式应为 HH:mm，收到: " + endTime);
        }
        if(weekday != null && (weekday < 1 || weekday > 7)) {
            errors.add("weekday 必须在 1-7 之间，收到: " + weekday);
        }
        return errors;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}