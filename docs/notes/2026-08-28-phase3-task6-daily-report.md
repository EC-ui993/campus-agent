# Task 6 总结：reports 表 + DailyReportService（早报生成）（2026-08-28）

> 今日状态：Task 6 完成，全量 57 个测试绿，已提交

## 今日范围

- `schema.sql`：新增 reports 表
- `entity/Report.java`：报告实体
- `mapper/ReportMapper.java`：报告 Mapper
- `ItemRepository.java`：+dueBetween
- `service/DailyReportService.java`：早报生成
- `service/DailyReportServiceTest.java`：早报测试

## 今日知识点

### 1. 纯模板 vs LLM 生成

- 早报数据是确定的，都在数据库里
- 拼字符串就够了，不需要 LLM
- 好处：快、零成本、绝不幻觉

### 2. report_date UNIQUE

- 保证同一天只能有一份报告
- 配合幂等：一天最多一份，不会重复
- 是“一天一份报告”的数据库保证

### 3. ItemRepository.dueBetween

- 查询 due_date 在 `[from, to]` 区间内的记录
- 使用 `LambdaQueryWrapper`
- 给 DailyReportService 生成早报用

### 4. DailyReportService.buildReport

- 拼出：
  - 日期 + 周几
  - 今日课程
  - 今日到期作业
  - 3 天内到期作业
  - 7 天内考试
  - 今日待办
- 空数据时显示“（无）”

### 5. 测试

- 有数据：报告包含课程、作业、考试
- 空数据：报告仍能生成，包含“（无）”

## 文件与方法链路

```text
DailyReportService.buildReport(today)
   ↓ repo.coursesOn(today)
今日课程
   ↓ repo.dueBetween(...)
到期作业/考试/待办
   ↓ 拼字符串
早报文本
```

## 下一步

- Task 7：定时生成 + 懒生成兜底 + 前端早报卡片。
