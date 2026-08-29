package com.campus.agent.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 把 Excel 每行读成 "列名=值；列名=值" 的文本；不假设列顺序/列名，交给 LLM 抽取。 */
public final class ExcelReader {

    private ExcelReader() {
    }

    public static List<String> readRowsAsText(InputStream in) {
        List<Map<Integer, String>> allRows = new ArrayList<>();
        EasyExcel.read(in, new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> row, AnalysisContext context) {
                allRows.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().headRowNumber(0).doRead();

        int headIndex = -1;
        for (int i = 0; i < allRows.size(); i++) {
            if (!allRows.get(i).isEmpty()) {
                headIndex = i;
                break;
            }
        }
        if (headIndex < 0) return List.of();

        Map<Integer, String> head = allRows.get(headIndex);
        if (isMatrixHeader(head)) {
            return readMatrix(allRows, headIndex);
        }

        List<String> rows = new ArrayList<>();
        for (int i = headIndex + 1; i < allRows.size(); i++) {
            Map<Integer, String> row = allRows.get(i);
            if (row.isEmpty()) continue;
            String text = row.entrySet().stream()
                    .map(e -> head.getOrDefault(e.getKey(), "列" + e.getKey()) + "=" + e.getValue())
                    .collect(Collectors.joining("；"));
            rows.add(text);
        }
        return rows;
    }

    private static boolean isMatrixHeader(Map<Integer, String> head) {
        for (String v : head.values()) {
            if (v != null && v.contains("星期") && v.trim().length() > 2) return true;
        }
        return false;
    }

    private static List<String> readMatrix(List<Map<Integer, String>> allRows, int headIndex) {
        Map<Integer, String> weekdayCols = new java.util.LinkedHashMap<>();
        Map<Integer, String> head = allRows.get(headIndex);
        for (Map.Entry<Integer, String> e : head.entrySet()) {
            if (e.getValue() != null && e.getValue().contains("星期") && e.getValue().trim().length() > 2) {
                weekdayCols.put(e.getKey(), e.getValue().trim());
            }
        }

        List<String> rows = new ArrayList<>();
        for (int i = headIndex + 1; i < allRows.size(); i++) {
            Map<Integer, String> row = allRows.get(i);
            if (row.isEmpty()) continue;
            String time = null;
            for (Map.Entry<Integer, String> e : row.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank() && !weekdayCols.containsKey(e.getKey())) {
                    time = e.getValue().trim();
                    break;
                }
            }
            for (Map.Entry<Integer, String> e : row.entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) continue;
                if (!weekdayCols.containsKey(e.getKey())) continue;
                rows.add("星期=" + weekdayCols.get(e.getKey())
                        + "；时间=" + time
                        + "；课程=" + e.getValue().trim());
            }
        }
        return rows;
    }
}
