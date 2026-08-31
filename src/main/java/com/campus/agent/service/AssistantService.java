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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 核心编排：意图分类 → 记录/提问/纠正 三条分支。 */
public class AssistantService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> DELETABLE_TYPES =
            Set.of("assignment", "exam", "todo", "course", "event");

    private final LlmClient llm;
    private final ItemRepository repo;
    private final ExtractionEngine engine;
    private final Database db;

    public AssistantService(LlmClient llm, ItemRepository repo, ExtractionEngine engine, Database db) {
        this.llm = llm;
        this.repo = repo;
        this.engine = engine;
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
                LocalDate target = resolveDate(input);
                String weekdayLabel = "周" + "一二三四五六日".charAt(target.getDayOfWeek().getValue() - 1);
                String courseSection = sectionFor(target);
                StringBuilder acc = new StringBuilder();
                llm.chatStream(Prompts.ANSWER,"【数据库内容】\n" + context
                        + "\n\n【" + target + " " + weekdayLabel + "的课程，已应用临时变动】\n" + courseSection
                        + "\n\n【用户问题】\n" + input,delta -> {acc.append(delta);onDelta.accept(delta);});
                String reply = acc.toString();
                repo.insertMessage("assistant", reply);
                return reply;
            }
            else{
                return switch (intent){
                    case "correction" -> {
                        String reply = correct(input);
                        onDelta.accept(reply);
                        yield reply;
                    }
                    case "delete" -> {
                        String reply = delete(input);
                        onDelta.accept(reply);
                        yield reply;
                    }
                    default -> {
                        String reply = record(input, messageId);
                        onDelta.accept(reply);
                        yield reply;
                    }
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
        ExtractionEngine.Outcome o = engine.extractAndStore(input, messageId);
        String reply = o.ok()
                ? "✅ 已记录：" + o.summary()
                : "⚠️ 这条我没能解析成结构化记录（原因：" + o.error() + "），原文已存档。你可以换个说法再说一次。";
        try {
            repo.insertMessage("assistant", reply);
        } catch (SQLException e) {
            throw new RuntimeException("保存聊天记录失败: " + e.getMessage(), e);
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
        LocalDate target = resolveDate(input);
        String weekdayLabel = "周" + "一二三四五六日".charAt(target.getDayOfWeek().getValue() - 1);
        String courseSection = sectionFor(target);
        String reply = llm.chat(Prompts.ANSWER, "【数据库内容】\n" + context
                + "\n\n【" + target + " " + weekdayLabel + "的课程，已应用临时变动】\n" + courseSection
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
            List<Map<String,Object>> snapshot = recordsWithSeq();
            String recordsJson = MAPPER.writeValueAsString(snapshot);
            String prompt = Prompts.CORRECT.replace("{records}", recordsJson);
            String json = llm.chatJson(prompt, input);
            JsonNode n = MAPPER.readTree(json);
            String type = n.path("type").asText("");
            long seq = n.path("id").asLong(-1);
            if (seq < 0 || !ExtractedItem.VALID_TYPES.contains(type)) {
                String reply = "没听懂要纠正哪条，请带上编号，如“把作业第3条的日期改成11月12日”。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            Optional<Long> realIdOpt = resolveIdBySeq(snapshot,type, seq);
            if (realIdOpt.isEmpty()) {
                String reply = "没找到编号为 " + seq + " 的" + type + " 记录。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            long id = realIdOpt.get();
            ExtractedItem corr = ExtractedItem.fromJson(json);
            Optional<StoredItem> curOpt = repo.getById(type, id);
            if (curOpt.isEmpty()) {
                String reply = "没找到编号为 " + seq + " 的" + type + " 记录。";
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
            List<String> changes = diffLines(cur, merged);
            if (changes.isEmpty()) {
                String reply = "没有需要修改的内容：这条记录目前是 " + summarize(cur);
                repo.insertMessage("assistant", reply);
                return reply;
            }
            repo.updateById(merged, id);
            String reply = "✅ 已更新：" + typeLabel(merged.type()) + "《" + merged.title() + "》\n" + String.join("\n", changes);
            repo.insertMessage("assistant", reply);
            return reply;
        } catch (Exception e) {
            throw new RuntimeException("纠正出错: " + e.getMessage(), e);
        }
    }

    private String delete(String input) {
        try {
            List<Map<String,Object>> snapshot = recordsWithSeq();
            String recordsJson = MAPPER.writeValueAsString(snapshot);
            String prompt = Prompts.DELETE.replace("{records}", recordsJson);
            String json = llm.chatJson(prompt, input);
            JsonNode n = MAPPER.readTree(json);
            String type = n.path("type").asText("");
            long seq = n.path("id").asLong(-1);
            if (seq < 0 || !DELETABLE_TYPES.contains(type)) {
                String reply = "没听懂要删除哪条，请带上类型和编号，如“删除作业第3条”。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            Optional<Long> realIdOpt = resolveIdBySeq(snapshot, type, seq);
            if (realIdOpt.isEmpty()) {
                String reply = "没找到编号为 " + seq + " 的" + type + " 记录。";
                repo.insertMessage("assistant", reply);
                return reply;
            }
            long id = realIdOpt.get();
            Optional<StoredItem> cur = repo.getById(type, id);
            if (cur.isEmpty()) {
                String reply = "没找到编号为 " + seq + " 的" + type + " 记录。";
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

    /** 给所有记录加上每类内的连续显示序号 seq。 */
    public List<Map<String, Object>> recordsWithSeq() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> counters = new java.util.HashMap<>();
        for (StoredItem s : repo.allItems()) {
            Map<String, Object> m = s.toMap();
            int seq = counters.merge(s.type(), 1, Integer::sum);
            m.put("seq", seq);
            result.add(m);
        }
        return result;
    }

    /** 根据类型 + 显示序号，解析出真实数据库 id。 */
    private Optional<Long> resolveIdBySeq(List<Map<String, Object>> snapshot, String type, long seq) {
        long index = 0;
        for (Map<String,Object> m: snapshot) {
            if (type.equals(m.get("type"))) {
                index++;
                if (index == seq) return Optional.of((Long) m.get("id"));
            }
        }
        return Optional.empty();
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

    private String summarize(StoredItem item) {
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

    private LocalDate resolveDate(String input) {
        LocalDate today = LocalDate.now();
        if (input.contains("后天")) return today.plusDays(2);
        if (input.contains("明天")) return today.plusDays(1);
        if (input.contains("昨天")) return today.minusDays(1);
        Matcher m = Pattern.compile("(\\d{4})[-年](\\d{1,2})[-月](\\d{1,2})日?").matcher(input);
        if (m.find()) {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        Matcher md = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]").matcher(input);
        if (md.find()) {
            return LocalDate.of(today.getYear(), Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)));
        }

        String[] weekNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            if (input.contains("周" + weekNames[i]) || input.contains("星期" + weekNames[i])) {
                int target = i + 1;
                LocalDate d = today;
                for (int j = 0; j < 7; j++) {
                    if (d.getDayOfWeek().getValue() == target) return d;
                    d = d.plusDays(1);
                }
            }
        }
        return today;
    }

    private String sectionFor(LocalDate date) {
        try {
            String weekLine;
            var weekOpt = repo.weekOf(date);
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
                    weekLine = "该日是第 " + w + " 周（共 " + sem.totalWeeks() + " 周）";
                }
            }
            List<CourseOccurrence> courses = repo.coursesOn(date);
            String courseText = courses.isEmpty()
                    ? "（当天没有安排课程）"
                    : MAPPER.writeValueAsString(courses.stream().map(CourseOccurrence::toMap).toList());
            return weekLine + "\n" + courseText;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化课程失败: " + e.getMessage(), e);
        }
    }

    private List<String> diffLines(StoredItem before, ExtractedItem after){
        List<String> lines = new ArrayList<>();
        diff(lines, "标题", before.title(), after.title());
        diff(lines, "课程", before.course(), after.course());
        diff(lines, "老师", before.teacher(), after.teacher());
        diff(lines, "内容", before.content(), after.content());
        diff(lines, "地点", before.location(), after.location());
        diff(lines, "日期", before.dueDate(), after.dueDate());
        diff(lines, "时间", before.dueTime(), after.dueTime());
        return lines;
    }

    private void diff(List<String> lines,String label,String oldVal,String newVal){
        if(java.util.Objects.equals(oldVal,newVal)) return;;
        lines.add(label + ": " + nvl(oldVal,"（空）") + " -> " + nvl(newVal,"（空）"));
    }

    private String nvl(String v,String dflt){
        return (v == null || v.isBlank())?dflt:v;
    }
}
