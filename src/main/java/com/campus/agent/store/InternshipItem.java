package com.campus.agent.store;

import java.util.LinkedHashMap;
import java.util.Map;

public record InternshipItem(
                long id, String company, String position, String city, String salary,
                String deadline, String jd, String link, String status){

    public Map<String,Object> toMap(){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",id);
        m.put("company",company);
        m.put("position",position);
        if(city != null) m.put("city",city);
        if(salary != null) m.put("salary",salary);
        if(deadline != null) m.put("deadline",deadline);
        if(jd != null) m.put("jd",jd);
        if(link != null) m.put("link",link);
        if(status != null) m.put("status",status);
        return m;
    }
}

