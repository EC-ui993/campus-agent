package com.campus.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 配置加载：环境变量 DEEPSEEK_API_KEY 优先，其次 config.properties。 */
public record AppConfig(String apiKey, String model, String baseUrl, String dbPath) {

    public static AppConfig load() {
        Properties p = new Properties();
        Path f = Path.of("config.properties");
        if (Files.exists(f)) {
            try (var reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("读取 config.properties 失败: " + e.getMessage(), e);
            }
        }
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            key = p.getProperty("api.key", "");
        }
        if (key.isBlank()) {
            throw new IllegalStateException(
                    "缺少 DeepSeek API Key：请设置环境变量 DEEPSEEK_API_KEY，"
                            + "或复制 config.properties.example 为 config.properties 并填写 api.key");
        }
        return new AppConfig(
                key.trim(),
                p.getProperty("model", "deepseek-chat"),
                p.getProperty("base.url", "https://api.deepseek.com"),
                p.getProperty("db.path", "data/agent.db"));
    }
}
