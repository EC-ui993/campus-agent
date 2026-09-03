# 阶段 4 Task 3 总结：internship 摄入 + 删除支持（2026-09-02）

> 今日状态：Task 3 完成，全量 69 个测试绿，已提交

## 今日范围

- `ExtractedInternship.java`
- `ExtractedItem.java`
- `Prompts.java`
- `ExtractionEngine.java`
- `AssistantService.java`
- `AssistantServiceTest.java`
- `ExtractionEngineTest.java`

## 今日知识点

### 1. JD 的"原文 + 结构化索引"双轨

- jd字段存原文，匹配度分析和学习计划制定的时候使用
- 其它字段是索引，列表筛选的时候使用
- 和raw_inbox兜底思想同源：原文保底+索引增值
- 注意不要让LLM概括JD，不然会丢失关键信息 

### 2. engine 的"跳过泛化校验"分流

- ExtractionEngine中前五种类型业务在extractAndStore校验入库
- 其它类型(course_override/semester_setting/internship)跳过泛化校验，在store中的各自分支校验入库
- 原因：因为ExtractedItem.validate不认识其它表的特有字段，会误报
- 注意ExtractedItem.VALID_TYPES要加新type(fromJson 解析需要)

### 3. 实习删除的方案 B：进 recordsWithSeq 上下文

- 现象：实习是独立体系，不在StoredItem里，allItems()也没有实习，按序号显示和删除时LLM找不到上下文
- 方案：在recordsWithSeq()遍历完StoredItem后面追加实习的遍历，每条遍历前补上type="internship" + title=company+position必填项，然后计数
- seq用同一张counters独立计数(实习第N条从1开始计数)；
- 注：不能给StoredItem加实习字段(污染统一模型，所有调用方要处理 null)

### 4. Optional 安全用法

- 先判断Optional是否为空(isEmpty())才能取值get()
- 否则get()在值为空时会抛NoSuchElementException异常而不是友好提示

### 5. 大小写与字段选择陷阱

- type必须小写，否则匹配不上
- title用company+position(岗位)才能分辨哪个岗

### 6. handle vs handleStreaming 测试选择

- handle和handlleStreaming在逻辑上用的方法是同一份，只是回复送达方式不同
- 测试业务中测试流式特有行为才用handleStreaming测
- 其它情况都用handle，因为简单，可以直接断言返回值

### 7. merge 计数

- counters.merge("internship", 1, Integer::sum) 键不存在放1，存在旧值+1
- 三个参数：键、初始值、合并函数(Integer::sum = 旧值+新值)

## 文件与方法链路

粘贴JD链路：
handle -> classify(record) -> record() ->     分辨出用户意图，开始record链路
engine.extractAndStore -> llm.chatJson(EXTRACT) -> FakeLlm/DeepSeek返回internship的JSON字段 ->
ExtractedItem.fromJson → type=internship → store()internship分支 ->    得到JSON字段形式的用户消息所有信息
ExtractedInternship.fromJson+validate → repo.insertInternship → Outcome(ok)   解析校验入库返回结果
删除实习链路：
handle -> classify(delete) -> recordsWithSeq(5类+实习段) -> DELETE prompt ->   分辨出意图，查序号和对应记录拼出提示词给LLM(补{records}部分内容)   
LLM输出type和id -> id经过resolveIdBySeq(快照)映射成真实数据库id -> findInternship → deleteInternship -> 回执    根据LLM输出的type和id执行查找删除操作，最后返回回执

## 今天踩坑记录

- 1.Internship是实习信息，文案写成了"简历设置"
- 2.type是小写单词，是"internship"，写成了Internship
- 3.title用company+position(岗位)才能分辨实习信息
- 4.delete()特判把get()提到判空前，会报错NoSuchElementException，应先isEmpty检查
- 5.回执文案company/position之间漏空格 + "实习…的实习记录"重复

## 下一步

- Task 4：profile_setting 摄入（求职档案，仿写 ExtractedProfile 已有，补 EXTRACT 字段 + engine 分支 + 测试）
