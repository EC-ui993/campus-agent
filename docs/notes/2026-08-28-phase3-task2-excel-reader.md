# Task 2 总结：ExcelReader（EasyExcel 读行转文本）（2026-08-28）

> 今日状态：Task 2 完成，全量 50 个测试绿，已提交

## 今日范围

- `pom.xml`：加 EasyExcel 依赖
- `service/ExcelReader.java`：Excel 每行转 “列名=值” 文本
- `service/ExcelReaderTest.java`：测试

## 今日知识点

### 1. EasyExcel 依赖

- `com.alibaba:easyexcel:4.0.3`
- 让项目能读写 Excel

### 2. ExcelReader.readRowsAsText

- 输入：`InputStream`
- 输出：`List<String>`
- 每行转成：
  ```text
  课程名=高数；星期=周一；开始时间=08:00；...
  ```
- 不假设列顺序/列名，交给 LLM 抽取

### 3. 回调式读取

- `invokeHeadMap`：表头 Map（列索引 → 列名），只触发一次
- `invoke`：每读到一行数据，把该行和表头拼成文本，加入 rows
- 不是一次性读进大集合

### 4. 为什么不用固定 Java 对象

- Excel 列名和列顺序不固定
- 直接转固定对象容易失败
- 转成文本后由 LLM 理解更灵活

### 5. 测试

- 先写一个 xlsx，再读回来验证
- 空表（只有表头）返回空列表

## 文件与方法链路

```text
Excel 文件
   ↓ Files.newInputStream
InputStream
   ↓ ExcelReader.readRowsAsText
EasyExcel 回调读取
   ↓ invokeHeadMap 拿表头
   ↓ invoke 每行拼接
List<String>（每行 “列名=值；列名=值”）
   ↓ 后续交给 LLM 抽取
```

## 下一步

- Task 3：ExtractionEngine 重构（抽取逻辑共用）。
