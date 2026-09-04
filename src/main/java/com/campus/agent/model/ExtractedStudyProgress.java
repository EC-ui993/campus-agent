package com.campus.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record ExtractedStudyProgress(String studyDate, String content) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ExtractedStudyProgress fromJson(String json){
        final JsonNode n;
        try {
            n = MAPPER.readTree(json);
        }
        catch (IOException e){
            throw new RuntimeException("学习打卡设置不是合法JSON：" + e.getMessage());
        }

        return new ExtractedStudyProgress(
                blankToNull(n.path("studyDate").asText("")),
                blankToNull(n.path("content").asText("")));
    }

    public List<String> validate(){
        List<String> errors = new ArrayList<>();
        if(studyDate != null && !studyDate.matches("\\d{4}-\\d{2}-\\d{2}")){
            errors.add("日期格式错误");
        }
        if(content == null || content.isBlank()){
            errors.add("内容不能为空");
        }
        return errors;
    }

    private static String blankToNull(String s){return (s == null || s.isBlank())?null:s.trim();}
}
