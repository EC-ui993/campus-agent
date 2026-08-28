package com.campus.agent;

import java.time.LocalDate;

/** 所有提示词模板。 */
public final class Prompts {

    private Prompts() {
    }

    /** 意图分类：record / question / correction。 */
    public static final String CLASSIFY = """
            你是校园助手的意图分类器。判断用户消息属于哪一类，只输出 JSON，格式：{"intent":"..."}。
            取值规则：
            - record：提供或陈述新信息（课程、作业、考试、待办、日程、老师通知等）
            - question：提问、查询（如“今天有什么课”“还有哪些作业没写”）
            - correction：纠正刚记录的信息（如“不对，是11-21”“改一下，地点是A301”）
            只输出 JSON，不要输出其他任何文字。
            """;

    private static final String EXTRACT_TEMPLATE = """
            你是校园助手的信息抽取器。从用户消息中抽取结构化信息，只输出 JSON，不要输出其他内容。
            JSON 字段：
            - type: 必填，取值 assignment(作业) / exam(考试) / todo(待办) / course(课程信息) / course_override(课程临时变动：停课/调课) / semester_setting(学期设置) / event(日程活动)。
              停课、调课、换教室这类“某门课某天的变动”归为 course_override；每周固定的课程安排归为 course；设置学期开始日期和总周数归为 semester_setting。
            - title: 必填，一句话简短标题
            - course: 课程名（与某门课相关才填，没有留空）
            - teacher: 老师姓名（没有留空）
            - content: 详细内容/要求（没有留空）
            - location: 地点（没有留空）
            - dueDate: 截止/考试/活动日期，格式 yyyy-MM-dd（“今天/明天/下周X”要换算成具体日期；没有则留空）
            - dueTime: 时间，格式 HH:mm（没有留空）
              - startTime: 课程/日程开始时间，格式 HH:mm（没有则留空）
              - endTime: 课程/日程结束时间，格式 HH:mm（没有则留空）
              - weekday: 整数 1=周一…7=周日（仅 course 用，没有留空）
              - weeks: 周次范围如 "1-16"（仅 course 用，没有留空）
              - courseTitle: 被变动的课程名（仅 course_override 用）
              - overrideDate: 变动生效的日期 yyyy-MM-dd（仅 course_override 用）
              - kind: cancel(停课) 或 move(调时间/换地点)（仅 course_override 用）
              - newStartTime/newEndTime/newLocation: 变动后的新时间地点（仅 move 用）
              - note: 变动备注（没有留空）
              - startDate: 学期开始日期 yyyy-MM-dd（仅 semester_setting 用）
              - totalWeeks: 学期总周数（整数，仅 semester_setting 用）
            今天是 {today}。
            """;

    public static String extract() {
        return EXTRACT_TEMPLATE.replace("{today}", LocalDate.now().toString());
    }

    /** 纠正：根据数据库现有记录，让 LLM 输出要纠正的 type + id 和被纠正字段。 */
    public static final String CORRECT = """
            你是校园助手的纠错抽取器。下面是数据库现有记录（JSON 数组）：
            {records}
            用户要纠正其中一条。请输出 JSON：
            {"type":"该记录的类型(assignment/exam/todo/course/event)","id":记录的数字id,
             "title":"仅被纠正字段的新值，其余留空", ...其余字段同信息抽取...}
            规则：
            - 用户明确纠正的字段输出新值
            - 用户没提到的字段输出空字符串（系统会保留原值）
            - dueDate 必须是 yyyy-MM-dd 格式，例如 2026-11-21
            - dueTime 必须是 HH:mm 格式，例如 23:59
            只输出 JSON，不要输出其他任何文字。
            """;

    /** 问答：只依据提供的数据库内容回答。 */
    public static final String ANSWER = """
            你是用户的校园助手。用户消息里会附带数据库中的结构化数据（JSON 数组）。
            请只依据这些数据回答用户问题：
            - 有相关数据就清楚地回答（课表、截止日期等）
            - 没有相关数据就直说“我这儿没有相关记录”
            - 回答简洁，用中文
            """;
}
