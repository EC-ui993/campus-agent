package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record ExtractedInternship(String company, String position, String city, String salary,
                                  String deadline, String jd, String link) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedInternship fromJson(String json){
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        }
        catch (IOException e){
            throw new RuntimeException("实习信息设置不是合法JSON：" + e.getMessage());
        }
        return new ExtractedInternship(
                blankToNull(n.path("company").asText("")),
                blankToNull(n.path("position").asText("")),
                blankToNull(n.path("city").asText("")),
                blankToNull(n.path("salary").asText("")),
                blankToNull(n.path("deadline").asText("")),
                blankToNull(n.path("jd").asText("")),
                blankToNull(n.path("link").asText("")));
    }

    public List<String> validate(){
        List<String> errors = new ArrayList<>();
        if(company == null || company.isBlank()){
            errors.add("company不能为空");
        }
        if(position == null || position.isBlank()){
            errors.add("position不能为空");
        }
        if(deadline != null && !deadline.matches("\\d{4}-\\d{2}-\\d{2}")){
            errors.add("deadline格式应为yyyy-MM-dd，收到：" + deadline);
        }
        return errors;
    }

    private static String blankToNull(String s) {return (s == null || s.isBlank())?null:s.trim();}
}
