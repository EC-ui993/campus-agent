package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record ExtractedProfile(String skills, String targetRole, String targetCity, String grade, String note) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedProfile fromJson(String json){
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        }
        catch (IOException e){
            throw new RuntimeException("个人档案设置不是合法JSON：" + e.getMessage());
        }
        return new ExtractedProfile(
                blankToNull(n.path("skills").asText("")),
                blankToNull(n.path("targetRole").asText("")),
                blankToNull(n.path("targetCity").asText("")),
                blankToNull(n.path("grade").asText("")),
                blankToNull(n.path("note").asText("")));
    }

    public List<String> validate(){
        List<String> errors = new ArrayList<>();
        if (skills == null && targetRole == null && targetCity == null && grade == null && note == null){
            errors.add("至少一个非空");
        }
        return errors;
    }

    private static String blankToNull(String s){return (s == null || s.isBlank()) ? null : s.trim();}
}
