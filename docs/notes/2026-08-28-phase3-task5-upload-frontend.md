# Task 5 总结：前端上传按钮 + 结果提示（2026-08-28）

> 今日状态：Task 5 完成，全量 55 个测试绿，已提交

## 今日范围

- `static/index.html`：上传按钮、隐藏文件框、结果提示条、上传 JS

## 今日知识点

### 1. FormData

- 浏览器上传文件时不能用 JSON 直接塞文件。
- 用 `FormData` 拼 multipart 请求体：
  ```js
  const fd = new FormData();
  fd.append("file", file);
  ```
- 这样 Spring 才能用 `@RequestParam("file") MultipartFile file` 收到。

### 2. 隐藏文件框触发

- `<input type="file" style="display:none">` 不可见。
- 点击按钮时用 JS 触发：
  ```js
  importBtn.onclick = () => importFile.click();
  ```

### 3. fetch 上传

- `method: "POST"`
- `headers: { "X-Access-Token": token }`
- `body: fd`
- 不手动设置 Content-Type，浏览器会自动加 multipart 边界。

### 4. 结果提示条

- 平时隐藏。
- 导入中：显示“正在导入 …”。
- 完成：显示成功/失败数量。
- 失败：显示错误信息。

## 文件与方法链路

```text
用户点击“导入课表”
   ↓ importFile.click()
选择 .xlsx
   ↓ onchange
FormData.append("file", file)
   ↓ fetch /api/import/excel
ImportController
   ↓
返回 {total, ok, failed, errors}
   ↓
importResult 显示结果
```

## 下一步

- Task 6：reports 表 + DailyReportService（早报生成）。
