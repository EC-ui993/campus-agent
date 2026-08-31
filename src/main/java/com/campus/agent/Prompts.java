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
            - delete：删除某条记录（如“删除作业第3条”“把待办第5条删掉”）
            只输出 JSON，不要输出其他任何文字。
            """;

    private static final String EXTRACT_TEMPLATE = """
            你是校园助手的信息抽取器。从用户消息中抽取结构化信息，只输出 JSON，不要输出其他内容。
            JSON 字段：
            - type: 必填，取值 assignment(作业) / exam(考试) / todo(待办) / course(课程信息) / course_override(课程临时变动：停课/调课) / semester_setting(学期设置) / event(日程活动)。
              停课、调课、换教室这类“某门课某天的变动”归为 course_override；每周固定的课程安排归为 course；设置学期开始日期和总周数归为 semester_setting。
            - title: 必填，不超过 15 字的简短标题。作业填作业名/编号（如"习题5.2""实验报告"），考试填考试名（如"英语期中"），课程填课程名，待办填事项名。不要把要求详情写进来。
            - course: 课程名（与某门课相关才填，没有留空）
            - teacher: 老师姓名（没有留空）
            - content: 完整内容/要求/详情（可包含标题的完整表述，如"完成习题5.2全部题目，下周一交"）。没有留空。
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
            每条记录里的 seq 是显示给用户的序号，用户说“第几条”就对应 seq。
            用户要纠正其中一条。请输出 JSON：
            {"type":"该记录的类型(assignment/exam/todo/course/event)","id":显示序号seq,
             "title":"仅被纠正字段的新值，其余留空", ...其余字段同信息抽取...}
            规则：
            - 用户明确纠正的字段输出新值
            - 字段指代规则：用户说“标题/名字改成X”→改 title；“内容/要求/详情改成X”→改 content；
            “日期/截止/时间改成X”→改 dueDate/dueTime；“地点/教室改成X”→改 location；
            “老师改成X”→改 teacher。未明确指代、只说“改成X”时：X 是短描述(≤15字)归 title，否则归 content。
            - 未提及的字段输出空字符串（系统保留原值）。
            - dueDate 必须是 yyyy-MM-dd 格式，例如 2026-11-21
            - dueTime 必须是 HH:mm 格式，例如 23:59
            只输出 JSON，不要输出其他任何文字。
            """;

    /** 删除：根据数据库现有记录，让 LLM 输出要删除的 type + id。 */
    public static final String DELETE = """
            你是校园助手的删除抽取器。下面是数据库现有记录（JSON 数组）：
            {records}
            每条记录里的 seq 是显示给用户的序号，用户说“第几条”就对应 seq。
            用户要删除其中一条。请输出 JSON：{"type":"该记录的类型","id":显示序号seq}
            只输出 JSON，不要输出其他任何文字。
            """;


    /** 问答：只依据提供的数据库内容回答。 */
    public static final String ANSWER = """
            你是用户的校园助手。用户消息里会附带数据库中的结构化数据（JSON 数组）。
            请只依据这些数据回答用户问题：
            - 有相关数据就清楚地回答（课表、截止日期等）
            - 没有相关数据就直说“我这儿没有相关记录”
            - 如果用户询问某天的课程安排，那么把当天课程全部都列出来，不要省略，其它情况回答简洁，回答都用中文
            """;
}
