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
            - type: 必填，取值 assignment(作业) / exam(考试) / todo(待办) / course(课程信息) / event(日程活动)。
              不属于前四类的零散信息一律归为 todo。
            - title: 必填，一句话简短标题
            - course: 课程名（与某门课相关才填，没有留空）
            - teacher: 老师姓名（没有留空）
            - content: 详细内容/要求（没有留空）
            - location: 地点（没有留空）
            - dueDate: 截止/考试/活动日期，格式 yyyy-MM-dd（“今天/明天/下周X”要换算成具体日期；没有则留空）
            - dueTime: 时间，格式 HH:mm（没有留空）
            今天是 {today}。
            """;

    public static String extract() {
        return EXTRACT_TEMPLATE.replace("{today}", LocalDate.now().toString());
    }

    /** 纠正：只输出被纠正的字段新值，未提及的字段输出空字符串。 */
    public static final String CORRECT = """
            你是校园助手的纠错抽取器。用户正在纠正刚记录的一条信息。
            请输出纠正后的完整记录 JSON（字段与信息抽取一致：type/title/course/teacher/content/location/dueDate/dueTime）。
            规则：
            - type 保持原记录的类型不变
            - 用户明确纠正的字段输出新值
            - 用户没提到的字段输出空字符串（系统会保留原值）
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
