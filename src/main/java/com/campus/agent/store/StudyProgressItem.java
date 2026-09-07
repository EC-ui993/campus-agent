package com.campus.agent.store;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public record StudyProgressItem(long id, LocalDate studyDate, String context) {
    public Map<String,Object> toMap(){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id);
        if (studyDate != null) m.put("studyDate", studyDate);
        if (context != null) m.put("context", context);
        return m;
    }
}
