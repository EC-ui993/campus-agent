# 阶段 4 Task 8 总结：端到端验收 + 排障收尾（2026-09-02）

> 今日状态：Task 8 走查完成，全量 78 个测试绿，阶段 4 验收通过

## 今日范围

- content -> context改名(schema/Database迁移/实体/读模型/record/engine/Prompts/ItemRepository/测试)
- jsr310修复(pom + AssistantService MAPPER + 测试)、ChatControllerTest 测试隔离修复
- index.html(实习列表默认折叠 + 手机拖拽 touch 兜底 + 触摸区加大)

## 今日知识点

### 1. 端到端走查 7 步（真实 LLM）

- 流程：档案设置 → 粘贴 JD 入库 → 匹配度分析 → 4周学习计划 → 打卡(默认今天) + 周复盘 → 删除实习 → 旧功能回归
- 发现的问题：实习列表在录入新的一条后不自动更新(手动刷新可见，v1可接受)
- LLM行为都走一遍才能暴露问题

### 2. 字段语义独立：content → context（产品决策）

- 现象：由于content是通用字段，被不止一个type使用，content存了"今天学了MyBatis-Plus"(混入时间词)
- 处理：把打卡字段改名成context作为它的独有字段，这样LLM在处理的时候不会含时间词
- 改动面：表/实体/读模型/EXTRACT/record/engine/repo/测试 全链路+老库迁移

### 3. 老库迁移：RENAME COLUMN + 幂等容错

- MIGRATIONS：存放给旧表加的新SQL语句的string数组，每次Database启动时跑一遍
- 现象：新库(scheme.sql)更新context列，MIGRATIONS的RENAME报no such column
- 原因：在新库更新context列之后，MIGRATIONS在执行RENAME COLUMN content TO context的时候发现根本没有content(被更新成context了)，所以报错
- 处理：在tryMIGRATIONS的catch中把容错匹配改宽"no such column: content" -> "no such column"
- 验证：DB Browser列名改变+重启两次不变(幂等)
- 注：所有库都先执行 schema（CREATE IF NOT EXISTS，新库建表/旧库跳过），再都执行 MIGRATIONS（新库吞错/旧库真改）

### 4. Jackson 序列化 LocalDate：jsr310 模块

- 现象：周复盘 500，InvalidDefinitionException：LocalDate not supported by default
- 原因：Jackson默认不序列化Java 8时间类型，需注册jackson-datatype-jsr310模块(无论序列化还是反序列化，只要处理的时候有localdate对象就需要)
- 为什么之前没发现：因为weekly_review测试只覆盖了无打卡分支，没走序列化，测试覆盖有缺口
- 修法：pom加依赖+ObjectMapper.registerModule(new JavaTimeModule())+补有打卡的测试

### 5. 测试隔离：@DynamicPropertySource 属性名

- 现象：ChatControllerTest断言position失败，实际值是真实库的数据
- 原因：@DynamicPropertySource只设了app.db-path，没设spring.datasource.url，导致Hikarii(连接池)连了application.yml的真实库
- 修法：设spring.datasource.url指向临时库 registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
- 教训：测试连真实库会导致假绿或莫名红，测试的时候必须隔离指向临时库

### 6. 手机端拖拽：touch 兜底 + 触摸区

- 现象：手机端早报拖拽触发页面滚动(滚到底才能滑)
- 原因：部分移动浏览器touch-action:none不生效，浏览器抢占手势
- 修法：touchmove preventDefault + passive:false 兜底；padding 6px→14px 加大触摸区

### 7. UI 默认态设计：实习列表默认折叠

- 现象：实习列表默认展开占位大(手机上)
- 修法：panel初始collapsed类，CSS管显示(.collapsed #internshipList display:none)，onclick只toggle类
- 单一状态源：显示状态只由collapsed类决定，JS不重复管display

## 文件与方法链路

新功能真实走查路径：
粘贴jd -> handleStreaming -> classify(record) -> ExtractEngine.extractAndStore -> "internship".equal(item.type()) -> 抽取校验入库回执 -> 前端渲染实习面板(刷新)
分析匹配度 -> handleStreaming -> classify(match_analysis) -> 取最新jd+档案 -> MATCH_ANALYSIS提示词 -> 流式回复
打卡 -> handleStreaming -> classify(record) -> 抽取校验入库回执
周复盘 -> handleStreaming -> classify(weekly_review) -> studyProgressBetween(7天) -> WEEKLY_REVIEW提示词 -> 序列化(jsr310)打卡记录 -> LLM返回结果 -> 流式输出

## 今天答错的点（复习重点）

- 1.打卡content语义：以为"今天学了"该进content → 应只存"学了什么"，时间词是日期线索(studyDate)
- 2.报错除了定位错误类型还要看Caused by
- 3.测试连真实库：以为@DynamicPropertySource设了db-path就隔离 → DataSource用的是spring.datasource.url

## 排障记录

- 1.周复盘 500：InvalidDefinitionException LocalDate → jsr310模块(补测试堵缺口)
- 2.ChatControllerTest红：测试连真实库(spring.datasource.url未指向临时库)
- 3.手机拖拽触发页面滚动：touchmove preventDefault + passive:false
- 4.content改名后启动失败：no such column(容错要匹配带引号的错误信息)

## 明日接续点

- 阶段 4 验收完成：回 v4-pro 汇报（对照验收清单逐项打勾）
- 讨论：3.5 OCR / 4.5 小修（状态标记、JD 去重）/ 阶段 5 可选演进
- 遗留观察：实习列表录入后自动刷新（可选改进）；批量删除实习（待规划层）
