package com.campus.agent.store;

import java.util.LinkedHashMap;
import java.util.Map;

/** 数据库查询出的一行记录（五种业务表统一形状）。 */
public record StoredItem(
        long id, String type, String title, String course, String teacher,
        String content, String location, String dueDate, String dueTime,
        String startTime, String endTime, String status) {

    /** 转成紧凑 Map（跳过 null），用于拼给 LLM 的 JSON 上下文。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("title", title);
        if (course != null) m.put("course", course);
        if (teacher != null) m.put("teacher", teacher);
        if (content != null) m.put("content", content);
        if (location != null) m.put("location", location);
        if (dueDate != null) m.put("dueDate", dueDate);
        if (dueTime != null) m.put("dueTime", dueTime);
        if (startTime != null) m.put("startTime", startTime);
        if (endTime != null) m.put("endTime", endTime);
        if (status != null) m.put("status", status);
        return m;
    }
}