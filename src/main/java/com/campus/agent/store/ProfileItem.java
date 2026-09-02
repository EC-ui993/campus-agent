package com.campus.agent.store;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProfileItem(
        long id, String skills, String targetRole, String targetCity,
        String grade, String note) {

    public Map<String,Object> toMap(){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id);
        if (skills != null) m.put("skills", skills);
        if (targetRole != null) m.put("targetRole", targetRole);
        if (targetCity != null) m.put("targetCity", targetCity);
        if (grade != null) m.put("grade", grade);
        if (note != null) m.put("note", note);
        return m;
    }
}
