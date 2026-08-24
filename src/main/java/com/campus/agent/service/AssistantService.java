package com.campus.agent.service;

import com.campus.agent.Prompts;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 核心编排：意图分类 → 记录/提问/纠正 三条分支。 */
public class AssistantService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llm;
    private final ItemRepository repo;
    private final Database db;
    private long lastInsertedId = -1;
    private String lastInsertedType = null;

    public AssistantService(LlmClient llm, ItemRepository repo, Database db) {
        this.llm = llm;
        this.repo = repo;
        this.db = db;
    }

    /** 供测试与 Main 查询用。 */
    public ItemRepository repository() {
        return repo;
    }

    /** 处理一条用户消息，返回助手的回复文本。 */
    public String handle(String input) {
        long messageId;
        try {
            messageId = repo.insertMessage("user", input);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        }
        String intent = classify(input);
        return switch (intent) {
            case "question" -> answer(input);
            case "correction" -> correct(input);
            default -> record(input, messageId);
        };
    }

    public String handleStreaming(String input,java.util.function.Consumer<String> onDelta) {
        long messageId;
        try {
            messageId = repo.insertMessage("user", input);
            String intent = classify(input);
            if (intent.equals("question")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (StoredItem s : repo.allItems()) {
                    rows.add(s.toMap());
                }
                String context = rows.isEmpty()?"（数据库为空，没有任何记录）":MAPPER.writeValueAsString(rows);
                StringBuilder acc = new StringBuilder();
                llm.chatStream(Prompts.ANSWER,"【数据库内容】\n" + context + "\n\n【用户问题】\n" + input,delta -> {acc.append(delta);onDelta.accept(delta);});
                String reply = acc.toString();
                repo.insertMessage("assistant", reply);
                return reply;
            }
            else{
                return switch (intent){
                    case "correction" -> correct(input);
                    default -> record(input, messageId);
                };
            }
        }catch(SQLException e){
                throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    private String classify(String input) {
        try {
            JsonNode n = MAPPER.readTree(llm.chatJson(Prompts.CLASSIFY, input));
            return n.path("intent").asText("record");
        } catch (Exception e) {
            return "record"; // 分类失败按记录处理，抽取环节还有兜底
        }
    }

    private String record(String input, long messageId) {
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String json = llm.chatJson(Prompts.extract(), input);
                ExtractedItem item = ExtractedItem.fromJson(json);
                lastErrors = item.validate();
                if (lastErrors.isEmpty()) {
                    long id = repo.insert(item, messageId);
                    lastInsertedId = id;
                    lastInsertedType = item.type();
                    String reply = ack(item);
                    repo.insertMessage("assistant", reply);
                    return reply;
                }
            } catch (Exception e) {
                lastErrors = List.of(e.getMessage());
            }
        }
        String reply = "⚠️ 这条我没能解析成结构化记录（原因：" + String.join("；", lastErrors)
                + "），原文已存档。你可以换个说法再说一次。";
        try {
            repo.insertRaw(input, String.join("；", lastErrors));
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("写 raw_inbox 失败: " + e.getMessage(), e);
        }
        return reply;
    }

    private String answer(String input) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (StoredItem s : repo.allItems()) {
                rows.add(s.toMap());
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        String context;
        try {
            context = rows.isEmpty() ? "（数据库为空，没有任何记录）" : MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败: " + e.getMessage(), e);
        }
        String reply = llm.chat(Prompts.ANSWER, "【数据库内容】\n" + context + "\n\n【用户问题】\n" + input);
        try {
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        }
        return reply;
    }

    private String correct(String input) {
        if (lastInsertedId < 0 || lastInsertedType == null) {
            return "我还没有刚记录的信息可纠正，请先录入一条信息。";
        }
        try {
            String json = llm.chatJson(Prompts.CORRECT, input);
            ExtractedItem corr = ExtractedItem.fromJson(json);
            Optional<StoredItem> currentOpt = repo.getById(lastInsertedType, lastInsertedId);
            if (currentOpt.isEmpty()) {
                String reply = "没找到要纠正的原记录，请先录入一条信息。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            StoredItem cur = currentOpt.get();
            ExtractedItem merged = new ExtractedItem(
                    lastInsertedType,
                    pick(corr.title(), cur.title()),
                    pick(corr.course(), cur.course()),
                    pick(corr.teacher(), cur.teacher()),
                    pick(corr.content(), cur.content()),
                    pick(corr.location(), cur.location()),
                    pick(corr.dueDate(), cur.dueDate()),
                    pick(corr.dueTime(), cur.dueTime()));
            List<String> errors = merged.validate();
            if (!errors.isEmpty()) {
                String reply = "纠正失败：" + String.join("；", errors);
                repo.insertMessage("assistant", reply);
                return reply;
            }
            repo.updateById(merged, lastInsertedId);
            String reply = "✅ 已更新：" + summarize(merged);
            repo.insertMessage("assistant", reply);
            return reply;
        } catch (SQLException e) {
            throw new RuntimeException("纠正时数据库出错: " + e.getMessage(), e);
        }
    }

    /** 纠正值非空则用之，否则保留原值。 */
    private String pick(String newValue, String oldValue) {
        return (newValue != null && !newValue.isBlank()) ? newValue : oldValue;
    }

    private String ack(ExtractedItem item) {
        return "✅ 已记录：" + summarize(item);
    }

    private String summarize(ExtractedItem item) {
        StringBuilder sb = new StringBuilder(typeLabel(item.type()) + "《" + item.title() + "》");
        if (item.course() != null) sb.append("（课程：").append(item.course()).append("）");
        if (item.teacher() != null) sb.append("，老师：").append(item.teacher());
        if (item.location() != null) sb.append("，地点：").append(item.location());
        if (item.dueDate() != null) {
            sb.append("，日期：").append(item.dueDate());
            if (item.dueTime() != null) sb.append(" ").append(item.dueTime());
        }
        if (item.content() != null) sb.append("，详情：").append(item.content());
        return sb.toString();
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "assignment" -> "作业";
            case "exam" -> "考试";
            case "todo" -> "待办";
            case "course" -> "课程";
            case "event" -> "日程";
            default -> "记录";
        };
    }
}
