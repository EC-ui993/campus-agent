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
            - match_analysis：让我分析某条实习与我的匹配度（如"分析匹配度""这个岗位适合我吗"）
            - study_plan：让我根据岗位 JD 制定学习计划（如"根据这个JD制定学习计划"）
            - weekly_review：复盘/总结我的学习（如"复盘这周""本周总结"）
            只输出 JSON，不要输出其他任何文字。
            """;

    private static final String EXTRACT_TEMPLATE = """
            你是校园助手的信息抽取器。从用户消息中抽取结构化信息，只输出 JSON，不要输出其他内容。
            JSON 字段：
            - type: 必填，取值 assignment(作业) / exam(考试) / todo(待办) / course(课程信息) / course_override(课程临时变动：停课/调课) / semester_setting(学期设置) / event(日程活动) / 
              internship(实习/招聘信息：如岗位 JD) / profile_setting(设置求职档案：技能/目标岗位/城市/年级) / study_progress(学习打卡：如"今天学了集合框架一小时")。
              停课、调课、换教室这类“某门课某天的变动”归为 course_override；每周固定的课程安排归为 course；设置学期开始日期和总周数归为 semester_setting。
            - title: 必填，不超过 15 字的简短标题。作业(assignment)的 title 填**课程名**（如"计算机组成""高数"）；
             考试(exam)填考试名（如"英语期中"）；课程(course)填课程名；待办(todo)填事项名。
            - content: 完整内容/要求。作业的 content 填具体作业内容（如"习题3、4、6、7、8""实验报告，下周一交"），
            **不要重复课程名**。没有留空。
            - course: 课程名（与某门课相关才填，没有留空）
            - teacher: 老师姓名（没有留空）
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
            - company: 公司名（仅 internship 用，必填）
            - position: 岗位名（仅 internship 用，必填）
            - city: 工作城市（仅 internship 用，多个城市用顿号/逗号分隔）
            - salary: 薪资（仅 internship 用）
            - deadline: 投递截止日期 yyyy-MM-dd（仅 internship 用）
            - jd: 岗位要求原文（仅 internship 用，尽量完整保留）
            - link: 招聘链接（仅 internship 用）
            - skills: 技能列表（仅 profile_setting 用，如"Java、SQL、Spring Boot"）
            - targetRole: 目标岗位（仅 profile_setting 用）
            - targetCity: 目标城市（仅 profile_setting 用）
            - grade: 年级（仅 profile_setting 用）
            - studyDate: 学习日期 yyyy-MM-dd（仅 study_progress 用；"今天/昨天"换算成日期；没提日期留空，系统按今天记）
            - context: 学习内容（仅 study_progress 用，必填；只写学了什么，不要把"今天/昨天"等时间词写进来）
            抽取示例（严格照此风格）：
            输入：计算机组成习题3、4、6、7、8
            输出：{"type":"assignment","title":"计算机组成","course":"计算机组成","teacher":"","content":"习题3、4、6、7、8","location":"","dueDate":"","dueTime":"","startTime":"","endTime":"","weekday":null,"weeks":null}
            输入：高数作业：习题5.2，11月20日前交
            输出：{"type":"assignment","title":"高数","course":"高数","teacher":"","content":"习题5.2","location":"","dueDate":"2026-11-20","dueTime":"","startTime":"","endTime":"","weekday":null,"weeks":null}
            输入：下周二英语课考试，考1-5单元
            输出：{"type":"exam","title":"英语期中","course":"英语","teacher":"","content":"1-5单元","location":"","dueDate":"2026-09-08","dueTime":"","startTime":"","endTime":"","weekday":null,"weeks":null}
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

    /** 匹配度分析：档案 + 最新 JD → 评分/差距/建议。 */
    public static final String MATCH_ANALYSIS = """
        你是求职顾问。下面是用户的求职档案和一条实习 JD。请输出：
        1) 匹配度评分（0-100）与一句话结论
        2) 已满足的要求（列表）
        3) 差距清单（列表，每条附一句补课建议）
        4) 接下来 2 周最该做的 3 件事
        用中文，简洁。档案：{profile}。JD：{jd}
        """;

    /** 学习计划：档案 + JD → 4 周计划。 */
    public static final String STUDY_PLAN = """
        你是求职导师。根据 JD 要求与用户技能现状，制定一份 4 周学习计划：
        - 按周拆分（第1周/第2周/第3周/第4周），每周 3-5 个可执行任务
        - 任务要具体到"学什么、做什么练习"，不要空话
        - 优先补差距最大的技能
        - 每天预计投入 2 小时
        用中文。档案：{profile}。JD：{jd}
        """;

    /** 周复盘：最近 7 天打卡 → 总结/亮点/建议。 */
    public static final String WEEKLY_REVIEW = """
        你是学习教练。下面是用户本周的学习打卡记录。请输出：
        1) 本周总结（学了什么、总量感）
        2) 亮点与不足
        3) 下周建议（具体任务）
        用中文，简洁。打卡记录：{logs}
        """;
}


