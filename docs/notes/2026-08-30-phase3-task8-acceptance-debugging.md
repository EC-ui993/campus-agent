# Task 8 总结：阶段 3 端到端验收 + 排障沉淀（2026-08-30）

> 今日状态：Task 8 走查完成，60 个测试全绿，排障全部提交

## 今日范围
- `ChatController.java`
- `AssistantService.java`
- `Prompts.java`
- `index.html`
- `AssistantServiceTest.java`

- `Bug1`；未开学周课表起点不是开学第一周
- `Bug2`：询问某天课表只回复最后一节
- `Bug3`：序号错位
- `Bug4`：无回复、前端UI改进

## 今日知识点

### 1. 分层排障法（数据层 → 组装层 → 提示词层 → 显示层）

- 出bug时症状在最外层，但是根因可能在任何一层
- 应该从里到外分层排除找根因
- 每层都有独立的验证手段(查SQL数据库 / 打印变量或者LLM输出 / 读提示词原文 / 看NetWork响应体)

### 2. 序号错位：数据快照一致性（方案 B） 解决Bug3

- 原因：
- allItems()按类型分组排序、无ORDER BY，在recordsWithSeq()里调用(根据数据库返回前端序号)和resolveIdBySeq()里调用(根据前端序号映射真正的数据库id)时顺序可能不同
- recordsWithSeq()和resolveIdBySeq()各查一次的结果不同，所以序号和id对不上
- 修法：在LLM调用前，增加一个变量snapshot接收recordsWithSeq()(内部调用allItems())后的结果，定格这份快照，这样LLM解析和resolveIdBySeq()用的就是同一份快照
- resolveIdBySeq变成纯函数(只靠传入的参数，不靠外部库不碰repo)，确保不受数据库影响

### 3. SSE 事件边界 vs 流结束 解决Bug2

- 原因：
- 提示词层：给LLM的回复提示词写了"回答简洁"，所以直接省略了部分信息
- SSE层：服务器把完整回复装进event:done里发给前端，但是由于完整回复中有换行，所以就被拆成了多条data:语句，前端一看到event:done就置finished=true，于是后面的data:语句就
  执行textContent = 新值(整体替换)，最后只剩最后一句  
- 修法：统一使用流式方案，删掉done发送，服务器把含换行的文本拆成多条data:行发给前端，前端按照两条标准(空行=一条SSE事件结束 reader.read()的done=整个流结束)来对一条条事
  件攒行并还原换行，最后emitter.complete()让HTTP流关闭 -> 前端reader.read()读到done来感知流结束了。流结束后检查有没有不以空行为结尾的事件没被加上去，有的话加上

### 4. 非流式分支也要走 onDelta 解决Bug4

- 原因：修Bug2时把done发送给删了，只留下了流式回复的传输通道，record/correct/delete这些靠done发送的非流式回复只return字符串，不调用onDelta，所以服务器什么都不发，导致
  无回复
- 修法：把switch的三个分支分别包装成代码块，三个分支算完reply后onDelta.accept(reply)补上通道，最后用yield返回值。return是方法级的，直接结束方法，yield是switch级的，所
  以用yield

### 5. 早报：幂等懒生成 + 一天一份

- 当天早报生成后录入数据早报内容不变，这是设计而不是bug，因为早报只有一份，不断刷新也保持不变则是体现幂等原则
- 验证懒生成：数据库手动删除reports表当天行，然后刷新，触发懒生成功能

### 6. 前端交互设计（UI 改进）
- 原因：原本用的float只管靠右显示，不知道容器宽度也不能自动换行，所以会出现按钮上下错开
- 修法：把三个按钮打包成一个div控制，加上flex和flex-wrap实现整体换行
- 新增上下拖拽早报功能：使用Pointer Events来统一触发拖拽事件，用touch-action:none来防止触发浏览器页面滚动
- height = startH + (clientY - startY) 下拉clientY增大，因为(clientY - startY)是正数 上滑(clientY - startY)则是负数
- clamp高度限制在[0,早报高度]，防止过度拖拽
- 按钮改进方案采用rgba半透明融入背景+transform按压反馈，使得按钮看起来更自然，更有物理感

## 文件与方法链路
```text
用户问：9月8日有什么课
          ↓ ChatController.chatStream
    handleStreaming方法
          ↓
    classify(question)
          ↓
    resolveDate(计算并返回当天日期)
          ↓
    sectionFor(根据日期得出日期所在周的状态并查出当天课程并序列化成JSON拼进上下文)
          ↓
    handleStreaming方法内拼prompt给llm.chatStream()做参数
          ↓
    ChatController调用流式发送方法向前端发送回复信息
          ↓
    前端用reader.read()攒行处理信息
          ↓
      页面显示回复
```
```text
用户删除信息
     ↓
  handleStreaming方法
     ↓
  classify(delete)
     ↓
  recordsWithSeq生成快照
     ↓
  把删除提示词和快照传给llm
     ↓
  根据快照调用resolveIdBySeq()
     ↓
  Mybatis-plus执行deleteByid
     ↓
  onDelta流式返回回执
```

## 今天踩坑记录

- 1.代码改完没重启，控制台看不到打印，以后改完代码先重启再继续测试
- 2.m.get(type)把变量名当成键名，map里根本查不到这个键，条件恒不成立
- 3.拖拽高度计算逻辑有误，导致下拉行为却是收起现象
- 4.修bug2时把done删了，没意识到三条分支是靠它传给前端回复的，以后在修改代码前要确定修改面和涉及的代码
- 5.误判纠正未执行，其实是纠正只修改了内容字段，但是查看记录只显示标题不显示内容，以后下判断多思考，用控制台信息辅助判断
- 6.手机不显示还以为是bug，其实是手机token还是旧的

## 下一步

- 回 v4-pro 规划会话：汇报 Task 8 验收结果（对照阶段 3 验收清单逐项打勾）
- 讨论阶段 3.5（OCR 选型）与阶段 4 优先级
- 待规划层决定：记录面板显示 content 字段；纠正时"内容"字段语义（title vs content）
