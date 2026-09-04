# 阶段 4 Task 5 总结：study_progress 摄入（每日打卡）（2026-09-02）

> 今日状态：Task 5 完成，全量 73 个测试绿，已提交

## 今日范围
- `ExtractedStudyProgress.java`
- `ExtractedItem.java`
- `Prompts.java`
- `ExtractionEngine.java`
- `ExtractionEngineTest`

## 今日知识点

### 1. 打卡日期"默认今天"：LLM 识别、系统算日期

- LLM负责识别日期，有日期换成具体日期输出字段，没日期由系统算日期
- LLM不知道今天是几号，系统知道

### 2. 相对日期不能入库

- "昨天""前天"这种相对概念入库前必须换算成具体日期
- 原因：周复盘查studyProgressBetween近七天是滚动窗口，相对概念无法比较

### 3. EXTRACT 按字段组织，同一键不重复定义

- 现象：EXTRACT里已经有content字段，那么study_progress就不重复写content，只给特有字段加说明
- 原因：防止LLM困惑

### 4. 命名统一（StudyLog → StudyProgress 系）

- 表study_progress、实体StudyProgress、读模型StudyProgressItem、repo内的方法insertStudyProgress/studyProgressBetween、
  record类名ExtractedStudyProgress、抽取type"study_progress"全链路统一
- 阶段计划文档同步更新

## 文件与方法链路

打卡链路：
handle(测试)/handleStreaming(网页) → classify(record) → engine.extractAndStore  //抽取拼提示词给LLM
-> llm.chatJson(EXTRACT) -> study_progress JSON -> ExtractedItem.fromJson -> 跳过泛化校验 → store() study_progress 分支  //LLM返回学习打卡JSON，程序解析
 → ExtractedStudyProgress.fromJson+validate(LocalDate d = studyDate 空 ? LocalDate.now() : parse)  //解析JSON字段校验
-> repo.insertStudyProgress(d, content, msgId) → "学习打卡已录入：...   //入库回执

## 今天踩坑记录

- 1.EtractedStudyProgress 悬空分号：if(studyDate...);{ errors.add } 分号让校验无条件执行
- 2.blankToNull 写成 public，应该是private
- 3.record 参数顺序和 EXTRACT 字段说明不一致(content在前,studyDate在后 → 调换)；
- 4.文案写混,study_progress是学习打卡不是学习计划
- 5.studyDate字段说明漏写 yyyy-MM-dd 格式

## 下一步

- Task 6：三个按需求职能力（match_analysis/study_plan/weekly_review，均流式）——阶段 4 最重的一块
