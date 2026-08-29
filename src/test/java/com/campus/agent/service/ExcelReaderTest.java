package com.campus.agent.service;

import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelReaderTest {

    @Test
    void readsRowsAsTextIgnoringColumnOrder(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("schedule.xlsx");
        List<List<String>> data = List.of(
                List.of("课程名", "星期", "开始时间", "结束时间", "教室", "周次"),
                List.of("高数", "周一", "08:00", "09:40", "A201", "1-16"),
                List.of("英语", "周三", "10:00", "11:40", "B101", "1-8,10-16"));
        EasyExcel.write(xlsx.toFile()).sheet("课表").doWrite(data);

        List<String> rows;
        try (InputStream in = Files.newInputStream(xlsx)) {
            rows = ExcelReader.readRowsAsText(in);
        }
        assertEquals(2, rows.size(), "两行数据都该读出");
        assertTrue(rows.get(0).contains("高数"), "实际: " + rows.get(0));
        assertTrue(rows.get(0).contains("08:00"));
        assertTrue(rows.get(0).contains("周一"));
        assertTrue(rows.get(1).contains("1-8,10-16"));
    }

    @Test
    void emptySheetGivesEmptyList(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("empty.xlsx");
        EasyExcel.write(xlsx.toFile()).sheet("空").doWrite(List.of(List.of("列A")));
        try (InputStream in = Files.newInputStream(xlsx)) {
            assertTrue(ExcelReader.readRowsAsText(in).isEmpty(), "只有表头没有数据 → 空列表");
        }
    }

    @Test
    void skipsLeadingEmptyRowsBeforeHeader(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("leading-empty.xlsx");
        List<List<String>> data = List.of(
                List.of(),
                List.of(),
                List.of("课程名", "星期", "开始时间", "结束时间", "教室", "周次"),
                List.of("高数", "周一", "08:00", "09:40", "A201", "1-16"));
        EasyExcel.write(xlsx.toFile()).sheet("课表").doWrite(data);

        List<String> rows;
        try (InputStream in = Files.newInputStream(xlsx)) {
            rows = ExcelReader.readRowsAsText(in);
        }
        assertEquals(1, rows.size(), "应跳过两个空行，只读出高数");
        assertTrue(rows.get(0).contains("高数"), "实际: " + rows.get(0));
        assertTrue(rows.get(0).contains("08:00"));
    }

    @Test
    void readsMatrixSchedulePerCell(@TempDir Path tmp) throws Exception {
        Path xlsx = tmp.resolve("matrix.xlsx");
        List<List<String>> data = List.of(
                List.of("", "星期一", "星期二"),
                List.of("08:30~09:50", "操作系统", "英语"),
                List.of("10:10~12:15", "高数", ""));
        EasyExcel.write(xlsx.toFile()).sheet("课表").doWrite(data);

        List<String> rows;
        try (InputStream in = Files.newInputStream(xlsx)) {
            rows = ExcelReader.readRowsAsText(in);
        }
        assertEquals(3, rows.size(), "三个非空格子都应生成一条");
        assertTrue(rows.get(0).contains("星期一"), "实际: " + rows.get(0));
        assertTrue(rows.get(0).contains("08:30~09:50"));
        assertTrue(rows.get(0).contains("操作系统"));
        assertTrue(rows.get(1).contains("星期二"));
        assertTrue(rows.get(1).contains("英语"));
        assertTrue(rows.get(2).contains("高数"));
    }


}
