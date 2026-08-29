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
        List<Map<Integer, String>> headHolder = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        EasyExcel.read(in, new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                headHolder.add(headMap);
            }

            @Override
            public void invoke(Map<Integer, String> row, AnalysisContext context) {
                Map<Integer, String> head = headHolder.isEmpty() ? Map.of() : headHolder.get(0);
                String text = row.entrySet().stream()
                        .map(e -> head.getOrDefault(e.getKey(), "列" + e.getKey()) + "=" + e.getValue())
                        .collect(Collectors.joining("；"));
                rows.add(text);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        return rows;
    }
}
