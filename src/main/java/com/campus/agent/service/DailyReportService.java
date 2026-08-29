package com.campus.agent.service;

import com.campus.agent.store.CourseOccurrence;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;

import java.time.LocalDate;
import java.util.List;

/** 每日早报：纯模板拼接（数据确定，不需要 LLM）。 */
public class DailyReportService {

    private final ItemRepository repo;

    public DailyReportService(ItemRepository repo) {
        this.repo = repo;
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
