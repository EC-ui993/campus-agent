# 阶段 4 Task 4 总结：profile_setting 摄入（求职档案）（2026-09-02）

> 今日状态：Task 4 完成，全量 71 个测试绿，已提交

## 今日范围

- `ExtractedProfile.java` 
- `ExtractedItem.java`
- `Prompts.java`
- `ExtractionEngine.java`
- `ExtractionEngineTest`

## 今日知识点

### 1. 档案为什么走聊天设置（同 semester）

- 因为档案是用户的事实，属于产品能力不是配置文件
- 用自然语言直接入库不用重启配置
- 和semester一样单行表覆盖

### 2. 单行覆盖的坑：分次设置会清掉旧值
- 现象：用户没提到的字段会直接覆盖原有的值变成null
- 处理：v1先直接覆盖，若真实使用需要经常分词设置再考虑合并式更新(像correct的pick)

### 3. EXTRACT 是一份总字段模板

- EXTRACT提示词中列了所有字段，每次把这个模板发给LLM按type选填
- note等通用字段先被course_override写了一次，语境偏course_override
- 处理：新类型的非核心字段v1不单独写说明，用户没说就默认不填

### 4. 多值字段 v1 存原文

- 需求：目标城市填多个
- 处理：v1程序不做精确匹配，LLM分析时能理解填入targetCity字段，比如"广州和深圳"→存"广州、深圳"(提示词引导逗号或顿号分隔)
- 原因：以后做按城市筛JD这种程序逻辑的时候才需要精确的结构化多值

### 5. 回执别拼 null

- 现象：字符串拼接录入成功信息回执时拼接null很难看
- 处理：照summerize模式，按条件拼接StringBuilder，(if 字段 != null 才append)

### 6. Optional 取值三兄弟

- Optional返回值可能为空可能不为空，有三种方式可以接收
- 1.get()收到空值抛NoSuchElementException(报错原因不够清晰准确)
- 2.orElse()收到空值返回null，后面调用取字段的时候才NPE(延迟报错)
- 3.orElseThrow代表有值则接收，无值则立刻报错无值，最准确清晰及时，而且可以自定义异常信息，所以测试时用它来接收

## 文件与方法链路

设置档案链路：
handle(测试)/handleStreaming(网页) -> classify(record) -> engine.extractAndStore -> llm.chatJson(EXTRACT)  拼提示词扔给LLM
-> profile_setting JSON -> ExtractedItem.fromJson -> 跳过泛化检验走store()的profile_setting分支  对LLM返回的JSON字段解析
-> ExtractedProfile.fromJson+validate → repo.setProfile(deleteById(1L)+insert)→ 回执(条件拼接)   解析完校验入库回执

## 今天踩坑记录

- 1.engine分支漏调ep.validate()，new了个空列表
- 2.回执直接拼接null，应该用条件拼接

## 下一步

- Task 5：study_log 摄入（每日打卡）——同 Task 4 同构，日期默认今天（engine 兜底 LocalDate.now()）
