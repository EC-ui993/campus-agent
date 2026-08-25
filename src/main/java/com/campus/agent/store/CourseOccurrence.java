package com.campus.agent.store;

import java.util.LinkedHashMap;
import java.util.Map;

/** 某一天的"有效课程"（已应用临时变动后的最终结果）。 */
public record CourseOccurrence(
        long courseId, String title, String teacher, String location,
        String startTime, String endTime, String note) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseId", courseId);
        m.put("title", title);
        if (teacher != null) m.put("teacher", teacher);
        if (location != null) m.put("location", location);
        if (startTime != null) m.put("startTime", startTime);
        if (endTime != null) m.put("endTime", endTime);
        if (note != null) m.put("note", note);
        return m;
    }
}
