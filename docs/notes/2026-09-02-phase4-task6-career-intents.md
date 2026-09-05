# 阶段 4 Task 6 总结：三个按需求职能力（流式）（2026-09-02）

> 今日状态：Task 6 完成，全量 76 个测试绿，已提交

## 今日范围

- `Prompts.java`
- `AssistantService.java`
- `AssistantServiceTest.java`

## 今日知识点

### 1. 三个能力 = "把对的数据喂给 LLM"

- match_analysis/study_plan共用最新实习(id最大)jd+档案; weekly_review用最近7天打卡
- 数据组装在服务端(repo查->JSON)，然后生成提示词给LLM
- 都走流式，和question共用SSE通道

### 2. 取"最新一条"用 id 最大，不用 seq

- "最新一条" -> 获取用allInternships().stream().max(Comparator.comparing(InternshipItem::id))得到最大id
-  seq 是按类型重数的显示编号，不代表新旧

### 3. 空数据提示也要走流式

- 空数据(无实习/无打卡)提示也要onDelta.accept发出 + 存messages
- 原因：前端只认流式，return字符串前端收不到

### 4. 数据 → JSON → replace 占位符（喂 LLM 的标准模式）

- repo查(Optional或List) → 空则提示 → 有则toMap → writeValueAsString → JSON字符串 → replace进占位符
- 单个对象(profile/JD)→ 单个JSON对象；List(打卡)→ stream.map(toMap).toList() → JSON数组
- 两个占位符链式replace：base.replace("{profile}",x).replace("{jd}",y)

### 5. Optional 链：isPresent 判断再 get

- 得到Optional先判isEmpty空再get()取值
- 不要直接get()空值抛NoSuchElementException

### 6. 骨架与方法抽取（Rule of Three）

- 三个新增意图合并成一个else-if块，在块内部再分类组prompt
- 内部按需要的数据分成两个分支，组prompt
- 最后流式骨架(chatStream + acc + 存messages + return)抽成 streamAnswer 方法，三个意图共用
- Rule of Three：重复出现才抽

## 文件与方法链路

求职能力链路(以match_analysis为例)
handleStreaming -> classify(match_analysis) → 三意图块 → 非 weekly 分支
-> allInternships().max(id)取最新JD → profile()取档案 → 有空则提示
→ jdJson/profileJson(toMap + writeValueAsString) → MATCH_ANALYSIS.replace({profile},profileJson).replace({jd},jdJson)
→ streamAnswer → chatStream 流式 → 存 messages → return

## 今天踩坑记录

- 1.replace 漏大括号：.replace("jd",x)应为.replace("{jd}",x)
- 2.现象：FakeLlm.chatStream中userPrompt未更新导致测试中输出一直是开头
- 3.根因+修法：FakeLlm.chatStream不记录systemPrompt → 断言 lastUserPrompt 找不到数据(FakeLlm中加了lastSystemPrompt字段解决)
- 4.后续排查：断言对象写错：lastUserPrompt(用户输入)vs lastSystemPrompt(拼好的提示词)——复制粘贴漏改，导致排查绕一大圈(ASCII断言、查编码……最后发现是对象写错，应该是lastSystemPrompt)

## 下一步

- Task 7：实习列表面板 + 端点（GET /api/internships + 前端折叠区）
