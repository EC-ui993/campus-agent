package com.campus.agent.service;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
import com.campus.agent.store.mapper.SemesterMapper;
import com.campus.agent.store.mapper.TodoMapper;
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

    @BeforeEach
    void cleanDb() {
        semesterMapper.delete(null);
        courseOverrideMapper.delete(null);
        assignmentMapper.delete(null);
        examMapper.delete(null);
        todoMapper.delete(null);
        courseMapper.delete(null);
        eventMapper.delete(null);
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
}
