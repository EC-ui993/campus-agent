package com.campus.agent.service;

import com.campus.agent.Prompts;
import com.campus.agent.llm.LlmClient;
import com.campus.agent.model.*;
import com.campus.agent.store.ItemRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 抽取引擎：文本 → LLM 抽取（重试 1 次）→ 校验 → 按类型入库 → 失败进 raw_inbox。聊天与导入共用。 */
public class ExtractionEngine {

    private final LlmClient llm;
    private final ItemRepository repo;

    public ExtractionEngine(LlmClient llm, ItemRepository repo) {
        this.llm = llm;
        this.repo = repo;
    }

    public record Outcome(boolean ok, String summary, String error) {
    }

    public Outcome extractAndStore(String input, long sourceMessageId) {
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String json = llm.chatJson(Prompts.extract(), input);
                ExtractedItem item = ExtractedItem.fromJson(json);
                if ("course_override".equals(item.type()) || "semester_setting".equals(item.type()) || "internship".equals(item.type()) || "profile_setting".equals(item.type())) {
                    return store(item, json, sourceMessageId);
                }
                List<String> errs = item.validate();
                if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
                return store(item, json, sourceMessageId);
            } catch (Exception e) {
                lastErrors = List.of(e.getMessage());
            }
        }
        try {
            repo.insertRaw(input, String.join("；", lastErrors));
        } catch (SQLException e) {
            throw new RuntimeException("写 raw_inbox 失败: " + e.getMessage(), e);
        }
        return new Outcome(false, null, String.join("；", lastErrors));
    }

    private Outcome store(ExtractedItem item, String json, long sourceMessageId) throws SQLException {
        if ("course_override".equals(item.type())) {
            ExtractedOverride ov = ExtractedOverride.fromJson(json);
            List<String> errs = ov.validate();
            if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
            repo.insertOverrideByTitle(ov.courseTitle(), LocalDate.parse(ov.overrideDate()), ov.kind(),
                    ov.newStartTime(), ov.newEndTime(), ov.newLocation(), ov.note(), sourceMessageId);
            return new Outcome(true, "变动：" + ov.courseTitle() + " " + ov.overrideDate() + " " + ov.kind(), null);
        }
        if ("semester_setting".equals(item.type())) {
            ExtractedSemesterSetting st = ExtractedSemesterSetting.fromJson(json);
            List<String> errs = st.validate();
            if (!errs.isEmpty()) throw new RuntimeException(String.join("；", errs));
            repo.setSemester(LocalDate.parse(st.startDate()), st.totalWeeks());
            return new Outcome(true, "学期：" + st.startDate() + " 开始，共 " + st.totalWeeks() + " 周", null);
        }
        if ("internship".equals(item.type())){
            ExtractedInternship in = ExtractedInternship.fromJson(json);
            List<String> errs = in.validate();
            if(!errs.isEmpty()) throw new RuntimeException(String.join(";",errs));
            repo.insertInternship(in.company(),in.position(),in.city(),in.salary(),in.deadline(),
                    in.jd(),in.link(),sourceMessageId);
            return new Outcome(true,"实习信息录入成功",null);
        }
        if("profile_setting".equals(item.type())){
            ExtractedProfile ep = ExtractedProfile.fromJson(json);
            List<String> errs = ep.validate();
            if(!errs.isEmpty()) throw new RuntimeException(String.join(";",errs));
            repo.setProfile(ep);
            StringBuilder sb = new StringBuilder("求职档案已更新");
            if (ep.skills() != null) sb.append("，技能：").append(ep.skills());
            if (ep.targetRole() != null) sb.append("，目标岗位：").append(ep.targetRole());
            if (ep.targetCity() != null) sb.append("，目标城市：").append(ep.targetCity());
            if (ep.grade() != null) sb.append("，年级：").append(ep.grade());
            return new Outcome(true,sb.toString(),null);
        }
        repo.insert(item, sourceMessageId);
        return new Outcome(true, summarize(item), null);
    }

    private String summarize(ExtractedItem item) {
        StringBuilder sb = new StringBuilder(typeLabel(item.type()) + "《" + item.title() + "》");
        if (item.course() != null) sb.append("（课程：").append(item.course()).append("）");
        if (item.location() != null) sb.append("，地点：").append(item.location());
        if (item.dueDate() != null) {
            sb.append("，日期：").append(item.dueDate());
            if (item.dueTime() != null) sb.append(" ").append(item.dueTime());
        }
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
