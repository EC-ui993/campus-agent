package com.campus.agent.service;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.model.ExtractedProfile;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.store.mapper.*;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssistantServiceTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("agent-service-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired
    ItemRepository repo;

    @Autowired
    Database db;

    @Autowired AssignmentMapper assignmentMapper;
    @Autowired ExamMapper examMapper;
    @Autowired TodoMapper todoMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EventMapper eventMapper;
    @Autowired CourseOverrideMapper courseOverrideMapper;
    @Autowired SemesterMapper semesterMapper;
    @Autowired InternshipMapper internshipMapper;
    @Autowired ProfileMapper profileMapper;
    @Autowired StudyProgressMapper studyProgressMapper;

    @BeforeEach
    void cleanDb() {
        semesterMapper.delete(null);
        courseOverrideMapper.delete(null);
        assignmentMapper.delete(null);
        examMapper.delete(null);
        todoMapper.delete(null);
        courseMapper.delete(null);
        eventMapper.delete(null);
        internshipMapper.delete(null);
        profileMapper.delete(null);
        studyProgressMapper.delete(null);
    }

    private AssistantService newService(FakeLlm llm) {
        return new AssistantService(llm, repo, new ExtractionEngine(llm, repo), db);
    }

    private static final String VALID_ASSIGNMENT = """
            {"type":"assignment","title":"习题5.2","course":"高数","teacher":"王老师",
             "content":"完成5.2全部习题","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
            """;

    @Test
    void recordFlowStoresAndAcks() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm);
        String reply = service.handle("高数作业习题5.2，11月20日前交");
        assertTrue(reply.startsWith("✅"), "回执应以 ✅ 开头，实际: " + reply);
        assertTrue(reply.contains("习题5.2"));
        assertTrue(reply.contains("2026-11-20"));

        List<StoredItem> all = service.repository().allItems();
        assertEquals(1, all.size());
        assertEquals("assignment", all.get(0).type());
    }

    @Test
    void failedExtractionGoesToRawInbox() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json("不是JSON")
                .json("{\"type\":\"nonsense\",\"title\":\"\"}");
        AssistantService service = newService(llm);
        String reply = service.handle("某条奇怪的消息");
        assertTrue(reply.contains("没能解析"), "应提示解析失败，实际: " + reply);
        assertTrue(service.repository().allItems().isEmpty(), "不应产生业务记录");
    }

    @Test
    void questionFlowReturnsLlmAnswer() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .chat("根据数据库，今天没有课。");
        AssistantService service = newService(llm);
        assertEquals("根据数据库，今天没有课。", service.handle("今天上什么课？"));
    }

    @Test
    void correctionFlowUpdatesById() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm);
        service.handle("高数作业习题5.2，11月20日前交");
        long id = service.repository().allItems().get(0).id();

        llm.json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","id":1,"title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"2026-11-21","dueTime":""}
                        """);

        String reply = service.handle("不对，截止是11月21日");
        assertTrue(reply.startsWith("✅ 已更新"), "应为更新回执，实际: " + reply);

        StoredItem updated = service.repository().getById("assignment", id).orElseThrow();
        assertEquals("2026-11-21", updated.dueDate());
        assertEquals("习题5.2", updated.title(), "未纠正的字段应保留原值");
    }

    @Test
    void correctionIdNotFound() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT)
                .json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","id":999,"title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"2026-11-21","dueTime":""}
                        """);
        AssistantService service = newService(llm);
        service.handle("高数作业习题5.2，11月20日前交");

        String reply = service.handle("把第999条改成11月21日");
        assertTrue(reply.contains("没找到"), "应提示没找到记录，实际: " + reply);
    }

    @Test
    void unknownIntentFallsBackToRecord() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"something-else\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm);
        assertTrue(service.handle("随便说点什么").startsWith("✅"));
    }

    @Test
    void questionStreamEmitsDeltas() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .streamChunks("今天", "没", "有课。");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("今天有什么课？", received::append);
        assertEquals("今天没有课。", full);
        assertEquals("今天没有课。", received.toString());
    }

    @Test
    void todayCourseIncludedInQuestionPrompt() throws Exception {
        int today = LocalDate.now().getDayOfWeek().getValue();
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .chat("有");
        repo.insert(new ExtractedItem("course", "高数", null, "王老师", null, "A201", null, null,
                "08:00", "09:40", today, null), 1);
        AssistantService service = newService(llm);
        service.handle("今天有什么课？");
        assertTrue(llm.lastUserPrompt.contains("的课程"), "应包含课程段: " + llm.lastUserPrompt);
        assertTrue(llm.lastUserPrompt.contains("高数"), "应包含课程名: " + llm.lastUserPrompt);
    }

    @Test
    void semesterSettingViaChat() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json("""
                        {"type":"semester_setting","title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"","dueTime":"",
                         "startDate":"2026-09-01","totalWeeks":16,"note":""}
                        """);
        AssistantService service = newService(llm);
        String reply = service.handle("本学期9月1日开始，共16周");
        assertTrue(reply.contains("已记录：学期"), "应提示已记录学期，实际: " + reply);
        assertTrue(repo.semester().isPresent(), "学期应已写入数据库");
        assertEquals(16, repo.semester().orElseThrow().totalWeeks());
    }

    @Test
    void deleteFlowRemovesRecord() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT);
        AssistantService service = newService(llm);
        service.handle("高数作业习题5.2，11月20日前交");
        long id = service.repository().allItems().get(0).id();

        llm.json("{\"intent\":\"delete\"}")
                .json("{\"type\":\"assignment\",\"id\":1}");
        String reply = service.handle("删除作业第1条");
        assertTrue(reply.startsWith("🗑️"), "删除回执应以 🗑️ 开头，实际: " + reply);
        assertTrue(reply.contains("习题5.2"), "回执应带上被删内容: " + reply);
        assertTrue(service.repository().getById("assignment", id).isEmpty(), "记录应已被删除");
    }

    @Test
    void deleteMissingIdGivesHint() throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"delete\"}")
                .json("{\"type\":\"assignment\",\"id\":99}");
        AssistantService service = newService(llm);
        String reply = service.handle("删除作业第99条");
        assertTrue(reply.contains("没找到"), "应提示没找到，实际: " + reply);
    }

    @Test
    void correctionAckShowsFieldDiff() throws Exception{
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT)
                .json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","id":1,"title":"习题6.1","course":"","teacher":"",
                         "content":"","location":"","dueDate":"2026-11-21","dueTime":""}
                        """);;
        AssistantService service = newService(llm);
        service.handle("高数作业习题5.2，11月20日前交");

        String reply = service.handle("把作业第1条改成习题6.1，截止11月21日");
        assertTrue(reply.startsWith("✅ 已更新"),"应该以✅ 已更新开头，实际：" + reply);
        assertTrue(reply.contains("标题"),"应该包含标题，实际：" + reply);
        assertTrue(reply.contains("习题5.2"),"应该包含习题5.2，实际：" + reply);
        assertTrue(reply.contains("习题6.1"),"应该包含习题6.1，实际：" + reply);
        assertTrue(reply.contains("2026-11-20"),"应该包含2026-11-20，实际：" + reply);
        assertTrue(reply.contains("2026-11-21"),"应该包含2026-11-21，实际：" + reply);
    }

    @Test
    void noOpCorrectionIsHonest() throws Exception{
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT)
                .json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","id":1,"title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"","dueTime":""}
                        """);
        AssistantService service = newService(llm);
        service.handle("高数作业习题5.2，11月20日前交");
        String reply = service.handle("改一下");

        assertTrue(reply.contains("没有需要修改"),"实际: " + reply);
    }

    @Test
    void internshipFlowStoresAndDeletes() throws Exception{
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json("""                               
                {"type":"internship","title":"","course":"","teacher":"",
                 "content":"","location":"","dueDate":"","dueTime":"",
                 "company":"字节跳动","position":"Java后端实习生","city":"北京",
                 "salary":"200-300/天","deadline":"2026-09-15",
                 "jd":"负责XX系统开发…","link":"https://example.com/jd/1"}
                """)
                .json("{\"intent\":\"delete\"}")
                .json("{\"type\":\"internship\",\"id\":\"1\"}");
        AssistantService service = newService(llm);
        String reply = service.handle("字节跳动招聘Java后端实习生…");
        assertTrue(reply.contains("实习"),"应该包含实习，实际：" + reply);
        assertEquals(1, service.repository().allInternships().size());
        String delReply = service.handle("删除第一条实习");
        assertTrue(delReply.contains("字节跳动"),"应该包含实习，实际：" + delReply);
        assertTrue(service.repository().allInternships().isEmpty());
    }

    @Test
    void matchAnalysisStreamsWithProfileAndJd(){
        repo.setProfile(new ExtractedProfile("Java、SQL", "Java后端实习生", "杭州", "大三", null));
        repo.insertInternship("字节跳动", "Java后端实习生", "北京", "200-300/天",
                "2026-09-15", "要求熟悉Spring Boot", "https://example.com/jd/1", 1);
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"match_analysis\"}")
                .streamChunks("匹配度", "80分", "差距是Redis");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("分析匹配度", received::append);
        assertEquals("匹配度80分差距是Redis", full);
        assertEquals("匹配度80分差距是Redis", received.toString());
        assertTrue(llm.lastSystemPrompt.contains("字节跳动"), "prompt 内容: " + llm.lastSystemPrompt);
        assertTrue(llm.lastSystemPrompt.contains("Java、SQL"), "prompt 应含技能: " + llm.lastSystemPrompt);
    }

    @Test
    void weeklyReviewEmptyLogsGivesHint(){
        FakeLlm llm = new FakeLlm().json("{\"intent\":\"weekly_review\"}");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("复盘这周",received::append);
        assertEquals("近七天未录入学习进度信息", full);
        assertEquals("近七天未录入学习进度信息", received.toString());
    }

    @Test
    void studyPlanNoInternshipGivesHint(){
        FakeLlm llm = new FakeLlm().json("{\"intent\":\"study_plan\"}");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("根据这个JD制定学习计划",received::append);
        assertEquals("还没有实习记录，先粘贴一条JD吧", full);
        assertEquals("还没有实习记录，先粘贴一条JD吧", received.toString());
    }

    @Test
    void weeklyReviewWithLogsStreams(){
        repo.insertStudyProgress(LocalDate.now(),"MyBatis-Plus",1);
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"weekly_review\"}")
                .streamChunks("本周学了", "MyBatis-Plus");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        String full = service.handleStreaming("复盘这周",received::append);
        assertEquals("本周学了MyBatis-Plus",full);
        assertTrue(llm.lastSystemPrompt.contains("MyBatis-Plus"));
    }

    @Test
    void matchAnalysisCanTargetInternshipBySeq(){
        repo.setProfile(new ExtractedProfile("Java、SQL", "Java后端实习生", "广州", "大二", null));
        repo.insertInternship("字节跳动", "Java后端实习生", "北京", "200-300/天",
                "2026-09-15", "要求熟悉Spring Boot", "https://example.com/jd/1", 1);
        repo.insertInternship("腾讯", "前端实习生", "深圳", "300/天",
                "2026-09-20", "要求熟悉React", "https://example.com/jd/2", 2);
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"match_analysis\"}")
                .streamChunks("匹配度", "80分", "差距是Redis");
        AssistantService service = newService(llm);
        StringBuilder received = new StringBuilder();
        service.handleStreaming("分析实习第1条的匹配度", received::append);
        assertTrue(llm.lastSystemPrompt.contains("字节跳动"),"应分析第一条，实际：" + llm.lastSystemPrompt);
        assertFalse(llm.lastSystemPrompt.contains("腾讯"),"不应含第二条，实际：" + llm.lastSystemPrompt);
    }
}
