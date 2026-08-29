package com.campus.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.agent.store.CourseOccurrence;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.store.entity.Report;
import com.campus.agent.store.mapper.ReportMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 每日早报：纯模板拼接（数据确定，不需要 LLM）。 */
@Service
public class DailyReportService {

    private final ItemRepository repo;
    private final ReportMapper reportMapper;

    public DailyReportService(ItemRepository repo, ReportMapper reportMapper) {
        this.repo = repo;
        this.reportMapper = reportMapper;
    }

    public String buildReport(LocalDate today) {
        String weekLabel = "周" + "一二三四五六日".charAt(today.getDayOfWeek().getValue() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(today).append(" ").append(weekLabel).append("\n");

        sb.append("\n【今日课程】\n");
        List<CourseOccurrence> courses = repo.coursesOn(today);
        if (courses.isEmpty()) sb.append("（无）\n");
        else for (CourseOccurrence c : courses) {
            sb.append("- ").append(nvl(c.startTime(), "?"))
              .append("-").append(nvl(c.endTime(), "?"))
              .append(" ").append(c.title())
              .append(c.location() != null ? " @ " + c.location() : "")
              .append(c.note() != null ? "（" + c.note() + "）" : "").append("\n");
        }

        appendDue(sb, "【今日到期的作业】", repo.dueBetween("assignment", today, today));
        appendDue(sb, "【3 天内到期的作业】", repo.dueBetween("assignment", today, today.plusDays(3)));
        appendDue(sb, "【7 天内的考试】", repo.dueBetween("exam", today, today.plusDays(7)));
        appendDue(sb, "【今日待办】", repo.dueBetween("todo", today, today));
        return sb.toString();
    }

    /** 取当天报告；不存在则现场生成并保存（幂等：一天一份）。 */
    public String todayReport() {
        LocalDate today = LocalDate.now();
        Report existing = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>().eq(Report::getReportDate, today.toString()));
        if (existing != null) return existing.getContent();
        String content = buildReport(today);
        Report r = new Report();
        r.setReportDate(today.toString());
        r.setContent(content);
        reportMapper.insert(r);
        return content;
    }

    @Scheduled(cron = "${app.report-cron:0 0 7 * * ?}")
    public void generateDaily() {
        todayReport();
    }


    private void appendDue(StringBuilder sb, String title, List<StoredItem> items) {
        sb.append("\n").append(title).append("\n");
        if (items.isEmpty()) sb.append("（无）\n");
        else for (StoredItem s : items) {
            sb.append("- ").append(s.title())
              .append(s.dueDate() != null ? "（截止 " + s.dueDate() : "")
              .append(s.dueTime() != null ? " " + s.dueTime() + "）" : s.dueDate() != null ? "）" : "")
              .append(s.course() != null ? "，" + s.course() : "").append("\n");
        }
    }

    private String nvl(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }
}
