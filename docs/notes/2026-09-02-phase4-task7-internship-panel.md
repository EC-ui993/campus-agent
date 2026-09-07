# 阶段 4 Task 7 总结：实习列表面板 + 端点（2026-09-02）

> 今日状态：Task 7 完成，全量 77 个测试绿，已提交

## 今日范围

- `ChatController.java`
- `index.html`
- `ChatControllerTest.java`
- `start.ps1`

## 今日知识点

### 1. REST 路径约定

- 完整路径 = 类注解路径@RequestMapping("/api")(社区惯例+项目约定) + 方法注解路径@GetMapping("/internships")(理论上自定义，但是原则是见名知意)
- 资源一般用复数名词，因为GET拿的是一个资源集合
- /api是父资源 后面的都是子资源

### 2. 端点复用 recordsWithSeq 再过滤

- internships端点内：service.recordsWithSeq()获得带seq的含实习段 -> filter逐个过滤只留type=internship -> toList转化成表 spring会把表转成JSON返回
- 为什么不直接repo.allInternships()：前端要显示的是seq编号，recordsWithSeq得到的就是seq编号而不是真实id
- fliter过滤的时候写"internship".equals(m.get("type"))而不是反过来：为了防止null.equals产生NPE(报空指针错误)

### 3. MockMvc 测试 JSON 断言（jsonPath）

- mvc.perform(get("/api/internships").header(token)) 模拟HTTP发送GET请求 路径开头加/表示绝对路径
- jsonPath("$[0].company")：美元符号 = JSON根，可能是单个JSON对象也可能是JSON对象数组;[0] = 数组第一项;.company = 取字段
  总的就是取第一项的company
- 测试先repo.insertInternship直接插数据(不走LLM)，再断言响应  

### 4. 早报"彻底收起"交互（UI 改进）

- 问题：minH=0但body padding撑着+border线，收到底还有残留
- 修法：applyHeight(h)：h<=8 → body.classList.add("hidden")(display:none 彻底消失，只留手柄)；
                       h>8 → 移除 hidden + 设高度；currentHeight()：hidden 时从 0 开始拉（下拉能重新展开）；
- 默认展开：applyHeight(maxH)

### 5. start.ps1 前台/后台取舍

- 现象：后台隐藏窗口启动springboot，开机自动启动但是自己无法控制开关
- 处理：开机任务计划程序触发，打开前台窗口，可见且随时开关

## 文件与方法链路

实习列表链路：
页面加载 -> loadInternships() -> fetch GET /api/internships(带token)    页面打开时加载，自动发送http请求
-> ChatController.internships() -> service.recordsWithSeq()(带seq的5类+实习段) -> filter   前端控制器根据请求头调用对应的controller方法，然后取出过滤返回列表
-> JSON数组 -> 前端渲染"#seq title | city | 截止 | 状态"   spring把得到的列表序列化成JSON返回给前端渲染
折叠：面板默认折叠（HTML 初始带 collapsed 类）→ 点标题 -> panel.classList.toggle("collapsed") → CSS(.collapsed #internshipList) 控制内容显示/隐藏，箭头旋转

## 今天答错的点（复习重点）

- 1.jsonPath("$[0]")是数组的第一个元素(值/对象，不是字符)
- 2.filter条件写"internship".equals(m.get("type"))而不是反过来，是为了防止NPE(报空指针错误)
- 3.早报收不干净：以为 minH=0 就够 → 实际 padding/border 也占空间，要 display:none 彻底隐藏
- 4.@GetMapping的路径是类注解路径+方法注解路径  GET拿的是集合，所以REST中资源名都用复数

## 排障记录

- 1.每个对象都要有单独的@Autowired，否则报空指针错误、get路径漏写前导斜杠、jsonPath键名req应该是seq

## 明日接续点
Task 8：阶段 4 端到端验收 + 文档归档(全链路走查 + 设计文档 + 笔记 + 交接文档)
