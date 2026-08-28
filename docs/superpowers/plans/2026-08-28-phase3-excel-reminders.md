# 阶段 3：Excel 课表导入 + 主动提醒（早报/到期）· 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 两条新能力：① 网页上传课表 Excel 一键导入（不依赖固定列格式，逐行交给 LLM 抽取）；② 每日早报与到期提醒（今日课程 + 今日/3天内作业 + 7天内考试 + 今日待办），每天定时生成，打开网页即见，电脑没开机时访问页面即时补生成。

**Architecture:** EasyExcel 读行转"列名=值"文本 → 复用抽取管线（把抽取逻辑从 AssistantService 抽出为 `ExtractionEngine`，聊天与导入共用）→ 入库；`reports` 表存每日早报，`@Scheduled` 定时生成 + `/api/report/today` 懒生成兜底（幂等）。OCR 与 PDF 不在本阶段（3.5 再做）。

**Tech Stack:** EasyExcel 4.0.3、Spring Scheduling（@Scheduled + cron）、multipart 上传、既有 Spring Boot 3.5 + MyBatis-Plus + SQLite。

## 执行纪律

- 概念先行 + 费曼复述；代码为导师参考实现；TDD 红→绿→提交→笔记。
- 本阶段学员已熟悉全套模式：Task 3 的重构靠"测试保持绿"来证明行为不变，是重点体验。

## 文件结构总览

```
修改/新增:
├─ pom.xml                                   +easyexcel
├─ src/main/resources/schema.sql             +reports 表
├─ src/main/java/com/campus/agent/store/Database.java    SCHEMA +reports（CLI 路径同步）
├─ src/main/java/com/campus/agent/store/entity/Report.java / mapper/ReportMapper.java   新
├─ src/main/java/com/campus/agent/store/ItemRepository.java  +dueBetween(type,from,to)
├─ src/main/java/com/campus/agent/service/ExtractionEngine.java   新（从 AssistantService 抽出）
├─ src/main/java/com/campus/agent/service/AssistantService.java   改（record() 改用 engine）
├─ src/main/java/com/campus/agent/service/ExcelReader.java        新
├─ src/main/java/com/campus/agent/service/DailyReportService.java 新
├─ src/main/java/com/campus/agent/web/ImportController.java       新
├─ src/main/java/com/campus/agent/web/ReportController.java       新
├─ src/main/java/com/campus/agent/CampusAgentApplication.java     改（@EnableScheduling）
├─ src/main/resources/application.yml         +multipart 上限、report-cron
├─ src/main/resources/static/index.html       改（上传按钮+早报卡片）
测试:
├─ src/test/java/com/campus/agent/service/ExcelReaderTest.java    新
├─ src/test/java/com/campus/agent/service/ExtractionEngineTest.java 新
├─ src/test/java/com/campus/agent/web/ImportControllerTest.java   新
├─ src/test/java/com/campus/agent/service/DailyReportServiceTest.java 新
├─ src/test/java/com/campus/agent/web/ReportControllerTest.java   新
├─ 既有测试保持绿（尤其 AssistantServiceTest = 重构回归证明）
```

---

## Task 1: 概念启蒙（无代码）

**Files:** 无。产出：阶段 3 概念笔记。

- [ ] **Step 1: 概念清单（每项学员能复述才算过）**
  - **POI 与 EasyExcel**：POI 是 Apache 的底层库（读写单元格，代码繁琐）；EasyExcel 是阿里在 POI 之上的封装（注解/回调式读，一行回调一行，内存占用低）——国内招聘 JD 常见
  - **回调式读取**（AnalysisEventListener）：不是"读进一个大集合再处理"，而是每读一行回调一次——大文件也不爆内存（流式思想，阶段 2 的 SSE 是同一个思想的下游版）
  - **multipart/form-data**：HTTP 上传文件的编码方式；Spring 用 `MultipartFile` 接
  - **@Scheduled + cron**：Spring 定时任务；cron 表达式 `秒 分 时 日 月 周`，`0 0 7 * * ?` = 每天 07:00:00
  - **幂等 + 懒生成兜底**：定时任务依赖"电脑恰好开机"，不可靠；所以"访问当天报告时若不存在则现场生成"——两种触发路径殊途同归，生成逻辑只写一份
  - **抽取逻辑复用**：聊天摄入和文件导入都要"文本 → LLM 抽取 → 入库"，抽成 `ExtractionEngine` 一个组件供两处调用（DRY；也是 Task 3 重构的理由）

---

## Task 2: ExcelReader（EasyExcel 读行转文本）

**Files:** pom.xml、service/ExcelReader.java、service/ExcelReaderTest.java

- [ ] **Step 1: pom 加依赖**

```xml
<dependency>
  <groupId>com.alibaba</groupId>
  <artifactId>easyexcel</artifactId>
  <version>4.0.3</version>
</dependency>
```

- [ ] **Step 2: 写失败测试**（测试里用 EasyExcel 自己生成夹具 xlsx——"用写验证读"，自给自足）

```java
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
}
```

Run → 红。

- [ ] **Step 3: 写 ExcelReader**

```java
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
```

（概念点：`invokeHeadMap` 只触发一次拿表头；`headRowNumber` 默认 1；`row` 是 `Map<列索引, 单元格文本>`。）

- [ ] **Step 4: 验证 + Commit**：`mvn -q -Dtest=ExcelReaderTest test` 绿 → 全量绿。Commit：`feat: excel reader turning rows into llm-friendly text`

---

## Task 3: ExtractionEngine 重构（抽取逻辑共用）

**Files:** service/ExtractionEngine.java（新）、service/AssistantService.java（改 record()）、config/AppBeans.java（加 bean）、service/ExtractionEngineTest.java（新）

- [ ] **Step 1: 讲概念**
  - **重构的定义**：不改变外部行为，只改善内部结构；**判定标准 = 既有测试保持绿**
  - **提取公共逻辑**：聊天"记录"与 Excel"导入"都要"文本→抽取→校验→按类型入库→失败兜底"，把它整体搬进 `ExtractionEngine`，两处调用
  - **依赖方向**：engine 只依赖 LlmClient + ItemRepository（不依赖 AssistantService，避免循环）

- [ ] **Step 2: 写 ExtractionEngine（导师先带学员把 AssistantService.record() 里那段"for 循环重试 + 分支"代码逐行读一遍，指出要搬哪些行）**

```java
package com.campus.agent.service;

import com.campus.agent.Prompts;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.model.ExtractedOverride;
import com.campus.agent.model.ExtractedSemesterSetting;
import com.campus.agent.store.ItemRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** 抽取引擎：文本 → LLM 抽取（重试 1 次）→ 校验 → 按类型入库 → 失败进 raw_inbox。聊天与导入共用。 */
public class ExtractionEngine {

    private final LlmClient llm;
    private final ItemRepository repo;

    public ExtractionEngine(LlmClient llm, ItemRepository repo) {
        this.llm = llm;
        this.repo = repo;
    }

    public record Outcome(boolean ok, String summary, String error) {
    }

    public Outcome extractAndStore(String input, long sourceMessageId) {
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String json = llm.chatJson(Prompts.extract(), input);
                ExtractedItem item = ExtractedItem.fromJson(json);
                List<String> errs = item.validate();
                if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
                return store(item, json, sourceMessageId);
            } catch (Exception e) {
                lastErrors = List.of(e.getMessage());
            }
        }
        try {
            repo.insertRaw(input, String.join("；", lastErrors));
        } catch (SQLException e) {
            throw new RuntimeException("写 raw_inbox 失败: " + e.getMessage(), e);
        }
        return new Outcome(false, null, String.join("；", lastErrors));
    }

    private Outcome store(ExtractedItem item, String json, long sourceMessageId) throws SQLException {
        if ("course_override".equals(item.type())) {
            ExtractedOverride ov = ExtractedOverride.fromJson(json);
            List<String> errs = ov.validate();
            if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
            repo.insertOverrideByTitle(ov.courseTitle(), LocalDate.parse(ov.overrideDate()), ov.kind(),
                    ov.newStartTime(), ov.newEndTime(), ov.newLocation(), ov.note(), sourceMessageId);
            return new Outcome(true, "变动：" + ov.courseTitle() + " " + ov.overrideDate() + " " + ov.kind(), null);
        }
        if ("semester_setting".equals(item.type())) {
            ExtractedSemesterSetting st = ExtractedSemesterSetting.fromJson(json);
            List<String> errs = st.validate();
            if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
            repo.setSemester(LocalDate.parse(st.startDate()), st.totalWeeks());
            return new Outcome(true, "学期：" + st.startDate() + " 开始，共 " + st.totalWeeks() + " 周", null);
        }
        repo.insert(item, sourceMessageId);
        return new Outcome(true, summarize(item), null);
    }

    private String summarize(ExtractedItem item) {
        StringBuilder sb = new StringBuilder(typeLabel(item.type()) + "《" + item.title() + "》");
        if (item.course() != null) sb.append("（课程：").append(item.course()).append("）");
        if (item.location() != null) sb.append("，地点：").append(item.location());
        if (item.dueDate() != null) {
            sb.append("，日期：").append(item.dueDate());
            if (item.dueTime() != null) sb.append(" ").append(item.dueTime());
        }
        return sb.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "assignment" -> "作业";
            case "exam" -> "考试";
            case "todo" -> "待办";
            case "course" -> "课程";
            case "event" -> "日程";
            default -> "记录";
        };
    }
}
```

- [ ] **Step 3: AssistantService 改用 engine**（删掉原 record() 里的抽取循环与 store 分支；`summarize/typeLabel` 若只有删除回执还用 `typeLabel`，保留一个私有版或复用 engine 的——推荐 service 里留自己的 `typeLabel`，避免过度耦合。新的 record()：）

```java
private String record(String input, long messageId) {
    ExtractionEngine.Outcome o = engine.extractAndStore(input, messageId);
    String reply = o.ok()
            ? "✅ 已记录：" + o.summary()
            : "⚠️ 这条我没能解析成结构化记录（原因：" + o.error() + "），原文已存档。你可以换个说法再说一次。";
    try {
        repo.insertMessage("assistant", reply);
    } catch (SQLException e) {
        throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
    }
    return reply;
}
```

（注意：semester_setting 的回执文案从"已设置学期"变为"已记录：学期…"——若 AssistantServiceTest 断言旧文案，同步更新断言；行为等价。）

- [ ] **Step 4: AppBeans 装配**（增加 `@Bean ExtractionEngine extractionEngine(LlmClient, ItemRepository)`；AssistantService 构造器改为 `(LlmClient, ItemRepository, ExtractionEngine, Database)`——Database 参数若已闲置可顺带移除，但注意 CLI Main 与测试的构造调用同步改）。

- [ ] **Step 5: 新测试 ExtractionEngineTest**（FakeLlm 驱动：有效抽取 → 入库 + ok；两次坏 JSON → raw_inbox + fail；semester_setting 分支 → 学期写入；course_override 分支 → 变动写入）

- [ ] **Step 6: 回归 + Commit**：重点——`mvn -q test` 全量绿，**AssistantServiceTest 一个断言都不改（除文案）就绿 = 重构成功**。Commit：`refactor: extract ExtractionEngine shared by chat and import`

---

## Task 4: 导入端点 POST /api/import/excel

**Files:** web/ImportController.java（新）、application.yml（multipart 上限）、web/ImportControllerTest.java（新）

- [ ] **Step 1: 讲概念**：`MultipartFile`（Spring 对上传文件的抽象：名字/大小/内容流）；`@RequestParam("file")`；`spring.servlet.multipart.max-file-size` 配置防超大文件；导入不经过意图分类（**直接**抽取——设计上导入的内容必然是要入库的信息）。

- [ ] **Step 2: 写失败测试**（MockMvc + 内存中的 xlsx 字节 + FakeLlm 预置 N 条 course 抽取）

```java
package com.campus.agent.web;

import com.alibaba.excel.EasyExcel;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ImportControllerTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("import-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.deleteIfExists(dbPath);
        Files.deleteIfExists(Path.of(dbPath + "-wal"));
        Files.deleteIfExists(Path.of(dbPath + "-shm"));
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlm() {
            FakeLlm f = new FakeLlm();
            f.json("""
                    {"type":"course","title":"高数","course":"","teacher":"","content":"",
                     "location":"A201","dueDate":"","dueTime":"","startTime":"08:00","endTime":"09:40",
                     "weekday":1,"weeks":"1-16"}
                    """);
            f.json("""
                    {"type":"course","title":"英语","course":"","teacher":"","content":"",
                     "location":"B101","dueDate":"","dueTime":"","startTime":"10:00","endTime":"11:40",
                     "weekday":3,"weeks":"1-16"}
                    """);
            return f;
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void importExcelInsertsCoursesAndReportsCounts() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).sheet("课表").doWrite(List.of(
                List.of("课程名", "星期", "开始", "结束", "教室", "周次"),
                List.of("高数", "周一", "08:00", "09:40", "A201", "1-16"),
                List.of("英语", "周三", "10:00", "11:40", "B101", "1-16")));
        MockMultipartFile file = new MockMultipartFile("file", "schedule.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        mvc.perform(multipart("/api/import/excel").file(file)
                        .header("X-Access-Token", "change-me-please"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.ok").value(2))
                .andExpect(jsonPath("$.failed").value(0));
    }
}
```

Run → 红。

- [ ] **Step 3: application.yml 增配**

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```

- [ ] **Step 4: 写 ImportController**

```java
package com.campus.agent.web;

import com.campus.agent.service.ExcelReader;
import com.campus.agent.service.ExtractionEngine;
import com.campus.agent.store.ItemRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImportController {

    private final ExtractionEngine engine;
    private final ItemRepository repo;

    public ImportController(ExtractionEngine engine, ItemRepository repo) {
        this.engine = engine;
        this.repo = repo;
    }

    @PostMapping("/import/excel")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("error", "文件为空");
        }
        List<String> rows;
        try (InputStream in = file.getInputStream()) {
            rows = ExcelReader.readRowsAsText(in);
        } catch (Exception e) {
            return Map.of("error", "读取 Excel 失败: " + e.getMessage());
        }
        long messageId;
        try {
            messageId = repo.insertMessage("user", "【Excel导入】" + file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("保存导入记录失败", e);
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (String row : rows) {
            ExtractionEngine.Outcome o = engine.extractAndStore(row, messageId);
            if (o.ok()) ok++; else errors.add(row + " → " + o.error());
        }
        try {
            repo.insertMessage("assistant", "📥 导入完成：成功 " + ok + " 条，失败 " + errors.size() + " 条");
        } catch (Exception e) {
            throw new RuntimeException("保存导入回执失败", e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("ok", ok);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }
}
```

- [ ] **Step 5: 验证 + Commit**：ImportControllerTest 绿 → 全量绿。Commit：`feat: excel import endpoint with per-row extraction and summary`

---

## Task 5: 前端上传按钮 + 结果提示

**Files:** static/index.html

- [ ] **Step 1: 讲概念**：`FormData`（浏览器拼 multipart 请求体）；`<input type="file" accept=".xlsx">`（hidden 触发）；fetch 上传带 token 头（沿用阶段 2 的口令机制）。

- [ ] **Step 2: 前端实现**（页头加按钮 + 隐藏文件框 + 结果条；参考实现）

```html
<header>🏫 校园助手 <button id="importBtn" style="float:right">📥 导入课表</button>
  <input type="file" id="importFile" accept=".xlsx" style="display:none"></header>
<div id="importResult" style="display:none; padding:8px 16px; background:#fffbe6; border-bottom:1px solid #e5e6eb"></div>
```

```javascript
const importBtn = document.getElementById("importBtn");
const importFile = document.getElementById("importFile");
const importResult = document.getElementById("importResult");
importBtn.onclick = () => importFile.click();
importFile.onchange = async () => {
  const file = importFile.files[0];
  if (!file) return;
  const fd = new FormData();
  fd.append("file", file);
  importResult.style.display = "block";
  importResult.textContent = "正在导入 " + file.name + " …";
  try {
    const resp = await fetch("/api/import/excel", { method: "POST", headers: { "X-Access-Token": token }, body: fd });
    const data = await resp.json();
    importResult.textContent = data.error
        ? "导入失败：" + data.error
        : "📥 导入完成：成功 " + data.ok + " 条，失败 " + data.failed + " 条" + (data.failed ? "（失败行已存档，可在记录面板查看）" : "");
  } catch (err) {
    importResult.textContent = "导入出错：" + err;
  } finally {
    importFile.value = "";
  }
};
```

- [ ] **Step 3: 验证 + Commit**：浏览器用真实课表 xlsx（或临时造的）走一遍：上传 → 成功 N 条 → 记录面板出现课程 → "今天有什么课"能答。Commit：`feat: frontend excel upload button with result banner`

---

## Task 6: reports 表 + DailyReportService（早报生成）

**Files:** schema.sql、Database.java、entity/Report.java、mapper/ReportMapper.java、ItemRepository.java（+dueBetween）、service/DailyReportService.java、service/DailyReportServiceTest.java

- [ ] **Step 1: 讲概念**
  - **纯模板 vs LLM 生成**：早报数据是确定的（课程/截止日期都在库里），**拼字符串就够了**——"不要什么都用 LLM"是重要工程判断（快、零成本、绝不幻觉）
  - **UNIQUE 约束 + 幂等**：report_date 唯一，同一天生成两次不产生重复行
  - **仓库查询方法 dueBetween**：区间查询（ge/le）在 MyBatis-Plus 里用 LambdaQueryWrapper

- [ ] **Step 2: schema**

```sql
CREATE TABLE IF NOT EXISTS reports(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  report_date TEXT NOT NULL UNIQUE,
  content TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')));
```

- [ ] **Step 3: ItemRepository.dueBetween**

```java
/** 某类型记录中 due_date 落在 [from, to] 区间的列表（按 due_date, due_time 排序）。 */
public List<StoredItem> dueBetween(String type, LocalDate from, LocalDate to) {
    return switch (type) {
        case "assignment" -> assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                        .ge(Assignment::getDueDate, from.toString())
                        .le(Assignment::getDueDate, to.toString())
                        .orderByAsc(Assignment::getDueDate, Assignment::getDueTime))
                .stream().map(this::toStored).toList();
        case "exam" -> /* 同理 Exam */;
        case "todo" -> /* 同理 Todo */;
        default -> throw new IllegalArgumentException("不支持的区间查询类型: " + type);
    };
}
```

（需要一个 `toStored(entity)` 转换辅助——导师带学员把 Task 8 阶段 2 里写过的 entity→StoredItem 映射逻辑复用/抽出来。）

- [ ] **Step 4: 写失败测试 DailyReportServiceTest**

```java
@Test
void reportContainsTodayCoursesAndDueItems(@TempDir Path tmp) throws Exception {
    try (Database db = new Database(tmp.resolve("r.db"))) {
        ItemRepository repo = new ItemRepository(db);
        DailyReportService svc = new DailyReportService(repo);
        LocalDate today = LocalDate.of(2026, 8, 31); // 周一
        repo.setSemester(LocalDate.of(2026, 8, 31), 16);
        repo.insert(new ExtractedItem("course", "高数", null, null, null, "A201", null, null,
                "08:00", "09:40", 1, "1-16"), 1);
        repo.insert(new ExtractedItem("assignment", "习题5.2", "高数", null, null, null,
                today.toString(), "23:59", null, null, null, null), 2);
        repo.insert(new ExtractedItem("exam", "期中", "英语", null, null, "D105",
                today.plusDays(5).toString(), "09:00", null, null, null, null), 3);

        String report = svc.buildReport(today);
        assertTrue(report.contains("高数"), report);
        assertTrue(report.contains("08:00"), report);
        assertTrue(report.contains("习题5.2"), report);
        assertTrue(report.contains("期中"), report);
    }
}

@Test
void emptyDayStillBuilds(@TempDir Path tmp) throws Exception {
    try (Database db = new Database(tmp.resolve("r2.db"))) {
        String report = new DailyReportService(new ItemRepository(db)).buildReport(LocalDate.of(2026, 8, 31));
        assertTrue(report.contains("今日课程"));
        assertTrue(report.contains("（无）"), report);
    }
}
```

Run → 红。

- [ ] **Step 5: 写 DailyReportService**

```java
package com.campus.agent.service;

import com.campus.agent.store.CourseOccurrence;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;

import java.time.LocalDate;
import java.util.List;

/** 每日早报：纯模板拼接（数据确定，不需要 LLM）。 */
public class DailyReportService {

    private final ItemRepository repo;

    public DailyReportService(ItemRepository repo) {
        this.repo = repo;
    }

    public String buildReport(LocalDate today) {
        String weekLabel = "周" + "一二三四五六日".charAt(today.getDayOfWeek().getValue() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(today).append(" ").append(weekLabel).append("\n");

        sb.append("\n【今日课程】\n");
        List<CourseOccurrence> courses = repo.coursesOn(today);
        if (courses.isEmpty()) sb.append("（无）\n");
        else for (CourseOccurrence c : courses) {
            sb.append("- ").append(nvl(c.startTime(), "?"))
              .append("-").append(nvl(c.endTime(), "?"))
              .append(" ").append(c.title())
              .append(c.location() != null ? " @ " + c.location() : "")
              .append(c.note() != null ? "（" + c.note() + "）" : "").append("\n");
        }

        appendDue(sb, "【今日到期的作业】", repo.dueBetween("assignment", today, today));
        appendDue(sb, "【3 天内到期的作业】", repo.dueBetween("assignment", today, today.plusDays(3)));
        appendDue(sb, "【7 天内的考试】", repo.dueBetween("exam", today, today.plusDays(7)));
        appendDue(sb, "【今日待办】", repo.dueBetween("todo", today, today));
        return sb.toString();
    }

    private void appendDue(StringBuilder sb, String title, List<StoredItem> items) {
        sb.append("\n").append(title).append("\n");
        if (items.isEmpty()) sb.append("（无）\n");
        else for (StoredItem s : items) {
            sb.append("- ").append(s.title())
              .append(s.dueDate() != null ? "（截止 " + s.dueDate() : "")
              .append(s.dueTime() != null ? " " + s.dueTime() + "）" : s.dueDate() != null ? "）" : "")
              .append(s.course() != null ? "，" + s.course() : "").append("\n");
        }
    }

    private String nvl(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }
}
```

（注意：`repo.dueBetween` 抛 SQLException 时 buildReport 如何传播——服务方法统一包成 RuntimeException，导师讲"checked 异常在 Spring 层的处理惯例"。）

- [ ] **Step 6: 验证 + Commit**：DailyReportServiceTest 绿 → 全量绿。Commit：`feat: daily report builder with today courses and due items`

---

## Task 7: 定时生成 + 懒生成兜底 + 前端早报卡片

**Files:** web/ReportController.java（新）、service/DailyReportService.java（+存取）、CampusAgentApplication.java（@EnableScheduling）、application.yml（report-cron）、static/index.html、web/ReportControllerTest.java（新）

- [ ] **Step 1: 讲概念**：`@EnableScheduling` 开启定时引擎；`@Scheduled(cron=...)` 挂方法；**懒生成兜底**（访问时不存在才生成——解决"定时没跑"的可靠性）；cron 放配置（`${app.report-cron:...}` 带默认值）。

- [ ] **Step 2: 实现**

application.yml：
```yaml
app:
  report-cron: "0 0 7 * * ?"
```

CampusAgentApplication 加 `@EnableScheduling`。

DailyReportService 增加存取（注入 ReportMapper）：
```java
/** 取当天报告；不存在则现场生成并保存（幂等：一天一份）。 */
public String todayReport() {
    LocalDate today = LocalDate.now();
    Report existing = reportMapper.selectOne(
            new LambdaQueryWrapper<Report>().eq(Report::getReportDate, today.toString()));
    if (existing != null) return existing.getContent();
    String content = buildReport(today);
    Report r = new Report();
    r.setReportDate(today.toString());
    r.setContent(content);
    reportMapper.insert(r);
    return content;
}

@Scheduled(cron = "${app.report-cron:0 0 7 * * ?}")
public void generateDaily() {
    todayReport();   // 定时触发；幂等，重复跑无害
}
```

ReportController：
```java
@RestController
@RequestMapping("/api")
public class ReportController {
    private final DailyReportService reportService;
    public ReportController(DailyReportService reportService) { this.reportService = reportService; }

    @GetMapping("/report/today")
    public Map<String, String> today() {
        return Map.of("date", LocalDate.now().toString(), "content", reportService.todayReport());
    }
}
```

前端：`index.html` 的 `<div id="importResult">` 上方加 `<div id="report"></div>`；脚本加载时：
```javascript
async function loadReport() {
  try {
    const resp = await fetch("/api/report/today", { headers: { "X-Access-Token": token } });
    if (resp.status === 401) return;
    const data = await resp.json();
    const el = document.getElementById("report");
    el.style.display = "block";
    el.textContent = data.content;
  } catch (e) { /* 早报失败不阻塞聊天 */ }
}
loadReport();
```
（CSS：`#report { white-space: pre-wrap; background:#fff; border-bottom:1px solid #e5e6eb; padding:12px 16px; }`）

- [ ] **Step 3: 测试**（ReportControllerTest：FakeLlm 不需要——早报不调 LLM；往库插数据后 GET /api/report/today 断言 content 含课程名；再 GET 一次断言同一内容且 reports 表只有一行=幂等）

- [ ] **Step 4: 验证 + Commit**：测试绿；浏览器打开 → 顶部出现今日早报；刷新不重复。Commit：`feat: scheduled daily report with lazy on-demand fallback`

---

## Task 8: 阶段 3 端到端验收 + 文档归档（压轴）

**Files:** 无新代码；设计文档、笔记、交接文档

- [ ] **Step 1: 全链路走查**（浏览器，连贯）
  1. 上传真实课表 Excel → "成功 N 条" → 记录面板有课程 → "今天有什么课"答对
  2. 打开页面 → 顶部早报含今日课程与到期项
  3. 录一条明天到期的作业 → 早报"3 天内到期"出现它
  4. 纠正/删除/停课功能回归（与导入、早报无冲突）
  5. `mvn test` 全量绿
  6. 手机同 WiFi 验证早报卡片与上传按钮窄屏可用
- [ ] **Step 2: 文档归档**：设计文档更新（数据模型 +reports；路线图：阶段 3=Excel+提醒、3.5=OCR、4=实习搜集+求职引擎；7.3 定时任务标注"提醒已实现于阶段 3，实习搜集仍在 4"）；新增阶段 3 笔记（EasyExcel 回调式读、重构与回归、cron、幂等懒生成、"不要什么都用 LLM"）；更新交接文档。提交：`docs: phase 3 acceptance notes and roadmap update`
- [ ] **Step 3: 回 v4-pro**：汇报验收，讨论阶段 3.5（OCR 选型）与阶段 4 优先级。

## 阶段 3 完成验收清单（Task 8 统一核对）

- [ ] `mvn test` 全绿
- [ ] 真实课表 Excel 上传导入成功，列格式不同也能吃（LLM 抽取兜底）
- [ ] 早报包含：今日课程 / 今日与 3 天内作业 / 7 天内考试 / 今日待办，空态显示"（无）"
- [ ] 定时生成 + 刷新幂等（同一天只有一份报告）
- [ ] 纠正/删除/停课/周次过滤与导入、早报无冲突
- [ ] 手机端可用
- [ ] 学员能复述：EasyExcel 与 POI 的关系；回调式读为什么省内存；重构怎么证明"行为没变"；cron 表达式含义；懒生成兜底解决什么问题；为什么早报不用 LLM 拼
