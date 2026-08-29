package com.campus.agent.web;

import com.campus.agent.service.ExcelReader;
import com.campus.agent.service.ExtractionEngine;
import com.campus.agent.store.ItemRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImportController {

    private final ExtractionEngine engine;
    private final ItemRepository repo;

    public ImportController(ExtractionEngine engine, ItemRepository repo) {
        this.engine = engine;
        this.repo = repo;
    }

    @PostMapping("/import/excel")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("error", "文件为空");
        }
        List<String> rows;
        try (InputStream in = file.getInputStream()) {
            rows = ExcelReader.readRowsAsText(in);
        } catch (Exception e) {
            return Map.of("error", "读取 Excel 失败: " + e.getMessage());
        }
        long messageId;
        try {
            messageId = repo.insertMessage("user", "【Excel导入】" + file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("保存导入记录失败", e);
        }
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (String row : rows) {
            ExtractionEngine.Outcome o = engine.extractAndStore(row, messageId);
            if (o.ok()) ok++; else errors.add(row + " → " + o.error());
        }
        try {
            repo.insertMessage("assistant", "📥 导入完成：成功 " + ok + " 条，失败 " + errors.size() + " 条");
        } catch (Exception e) {
            throw new RuntimeException("保存导入回执失败", e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("ok", ok);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }
}
