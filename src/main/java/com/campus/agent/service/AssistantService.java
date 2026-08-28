package com.campus.agent.service;

import com.campus.agent.Prompts;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.model.ExtractedItem;
import com.campus.agent.model.ExtractedOverride;
import com.campus.agent.model.ExtractedSemesterSetting;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.store.CourseOccurrence;
import java.time.LocalDate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 核心编排：意图分类 → 记录/提问/纠正 三条分支。 */
public class AssistantService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> DELETABLE_TYPES =
            Set.of("assignment", "exam", "todo", "course", "event");

    private final LlmClient llm;
    private final ItemRepository repo;
    private final Database db;

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
            case "delete" -> delete(input);
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
                String weekdayLabel = "周" + "一二三四五六日".charAt(LocalDate.now().getDayOfWeek().getValue() - 1);
                String todaySection = todaySection();
                StringBuilder acc = new StringBuilder();
                llm.chatStream(Prompts.ANSWER,"【数据库内容】\n" + context
                        + "\n\n【今天(" + LocalDate.now() + " " + weekdayLabel + ")的课程，已应用临时变动】\n" + todaySection
                        + "\n\n【用户问题】\n" + input,delta -> {acc.append(delta);onDelta.accept(delta);});
                String reply = acc.toString();
                repo.insertMessage("assistant", reply);
                return reply;
            }
            else{
                return switch (intent){
                    case "correction" -> correct(input);
                    case "delete" -> delete(input);
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
                if ("course_override".equals(item.type())) {
                    ExtractedOverride ov = ExtractedOverride.fromJson(json);
                    lastErrors = ov.validate();
                    if (lastErrors.isEmpty()) {
                        long id = repo.insertOverrideByTitle(ov.courseTitle(), LocalDate.parse(ov.overrideDate()), ov.kind(),
                                ov.newStartTime(), ov.newEndTime(), ov.newLocation(), ov.note(), messageId);
                        String reply = "✅ 已记录变动：" + ov.courseTitle() + " " + ov.overrideDate() + " " + ("cancel".equals(ov.kind()) ? "停课" : "调课");
                        repo.insertMessage("assistant", reply);
                        return reply;
                    }
                } else if ("semester_setting".equals(item.type())) {
                    ExtractedSemesterSetting st = ExtractedSemesterSetting.fromJson(json);
                    lastErrors = st.validate();
                    if (lastErrors.isEmpty()) {
                        repo.setSemester(LocalDate.parse(st.startDate()), st.totalWeeks());
                        String reply = "✅ 已设置学期：" + st.startDate() + " 开始，共 " + st.totalWeeks() + " 周";
                        repo.insertMessage("assistant", reply);
                        return reply;
                    }
                } else {
                    lastErrors = item.validate();
                    if (lastErrors.isEmpty()) {
                        long id = repo.insert(item, messageId);
                        String reply = ack(item);
                        repo.insertMessage("assistant", reply);
                        return reply;
                    }
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
        for (StoredItem s : repo.allItems()) {
            rows.add(s.toMap());
        }
        String context;
        try {
            context = rows.isEmpty() ? "（数据库为空，没有任何记录）" : MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败: " + e.getMessage(), e);
        }
        String weekdayLabel = "周" + "一二三四五六日".charAt(LocalDate.now().getDayOfWeek().getValue() - 1);
        String todaySection = todaySection();
        String reply = llm.chat(Prompts.ANSWER, "【数据库内容】\n" + context
                + "\n\n【今天(" + LocalDate.now() + " " + weekdayLabel + ")的课程，已应用临时变动】\n" + todaySection
                + "\n\n【用户问题】\n" + input);
        try {
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
        }
        return reply;
    }

    private String correct(String input) {
        try {
            String recordsJson = MAPPER.writeValueAsString(
                    repo.allItems().stream().map(StoredItem::toMap).toList());
            String prompt = Prompts.CORRECT.replace("{records}", recordsJson);
            String json = llm.chatJson(prompt, input);
            JsonNode n = MAPPER.readTree(json);
            String type = n.path("type").asText("");
            long id = n.path("id").asLong(-1);
            if (id < 0 || !ExtractedItem.VALID_TYPES.contains(type)) {
                String reply = "没听懂要纠正哪条，请带上编号，如“把作业第3条的日期改成11月12日”。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            ExtractedItem corr = ExtractedItem.fromJson(json);
            Optional<StoredItem> curOpt = repo.getById(type, id);
            if (curOpt.isEmpty()) {
                String reply = "没找到编号为 " + id + " 的" + type + " 记录。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            StoredItem cur = curOpt.get();
            ExtractedItem merged = new ExtractedItem(type,
                    pick(corr.title(), cur.title()),
                    pick(corr.course(), cur.course()),
                    pick(corr.teacher(), cur.teacher()),
                    pick(corr.content(), cur.content()),
                    pick(corr.location(), cur.location()),
                    pick(corr.dueDate(), cur.dueDate()),
                    pick(corr.dueTime(), cur.dueTime()),
                    pick(corr.startTime(), cur.startTime()),
                    pick(corr.endTime(), cur.endTime()),
                    corr.weekday(), corr.weeks());
            List<String> errors = merged.validate();
            if (!errors.isEmpty()) {
                String reply = "纠正失败：" + String.join("；", errors);
                repo.insertMessage("assistant", reply);
                return reply;
            }
            repo.updateById(merged, id);
            String reply = "✅ 已更新：" + summarize(merged);
            repo.insertMessage("assistant", reply);
            return reply;
        } catch (Exception e) {
            throw new RuntimeException("纠正出错: " + e.getMessage(), e);
        }
    }

    private String delete(String input) {
        try {
            String recordsJson = MAPPER.writeValueAsString(
                    repo.allItems().stream().map(StoredItem::toMap).toList());
            String prompt = Prompts.DELETE.replace("{records}", recordsJson);
            String json = llm.chatJson(prompt, input);
            JsonNode n = MAPPER.readTree(json);
            String type = n.path("type").asText("");
            long id = n.path("id").asLong(-1);
            if (id < 0 || !DELETABLE_TYPES.contains(type)) {
                String reply = "没听懂要删除哪条，请带上类型和编号，如“删除作业第3条”。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            Optional<StoredItem> cur = repo.getById(type, id);
            if (cur.isEmpty()) {
                String reply = "没找到编号为 " + id + " 的" + type + " 记录。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            StoredItem s = cur.get();
            repo.deleteById(type, id);
            String reply = "🗑️ 已删除：" + typeLabel(type) + "《" + s.title() + "》。删除不可恢复，录错了可重新录入。";
            repo.insertMessage("assistant", reply);
            return reply;
        } catch (Exception e) {
            throw new RuntimeException("删除出错: " + e.getMessage(), e);
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

    private String todaySection() {
        try {
            String weekLine;
            var weekOpt = repo.weekOf(LocalDate.now());
            if (weekOpt.isEmpty() || weekOpt.get() == -1) {
                weekLine = "（未设置学期信息，无法按周次过滤课程；说“本学期从X月X日开始，共N周”即可设置）";
            } else {
                int w = weekOpt.get();
                var sem = repo.semester().orElseThrow();
                if (w == 0) {
                    weekLine = "（尚未开学，学期 " + sem.start() + " 开始）";
                } else if (w > sem.totalWeeks()) {
                    weekLine = "（假期中，本学期共 " + sem.totalWeeks() + " 周已结束）";
                } else {
                    weekLine = "今天是本学期第 " + w + " 周（共 " + sem.totalWeeks() + " 周）";
                }
            }
            List<CourseOccurrence> todayCourses = repo.coursesOn(LocalDate.now());
            String courses = todayCourses.isEmpty()
                    ? "（今天没有安排课程）"
                    : MAPPER.writeValueAsString(todayCourses.stream().map(CourseOccurrence::toMap).toList());
            return weekLine + "\n" + courses;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化今日课程失败: " + e.getMessage(), e);
        }
    }

}
