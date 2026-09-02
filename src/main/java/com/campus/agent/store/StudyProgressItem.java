package com.campus.agent.store;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public record StudyProgressItem(long id, LocalDate studyDate, String content) {
    public Map<String,Object> toMap(){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id);
        if (studyDate != null) m.put("studyDate", studyDate);
        if (content != null) m.put("content", content);
        return m;
    }
}
