package com.campus.agent.service;

import com.campus.agent.model.ExtractedItem;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import com.campus.agent.testing.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssistantServiceTest {

    private static final String VALID_ASSIGNMENT = """
            {"type":"assignment","title":"习题5.2","course":"高数","teacher":"王老师",
             "content":"完成5.2全部习题","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
            """;

    private AssistantService newService(FakeLlm llm, Database db) {
        return new AssistantService(llm, new ItemRepository(db), db);
    }

    @Test
    void recordFlowStoresAndAcks(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT);
        try (Database db = new Database(tmp.resolve("s.db"))) {
            AssistantService service = newService(llm, db);
            String reply = service.handle("高数作业习题5.2，11月20日前交");
            assertTrue(reply.startsWith("✅"), "回执应以 ✅ 开头，实际: " + reply);
            assertTrue(reply.contains("习题5.2"));
            assertTrue(reply.contains("2026-11-20"));

            List<StoredItem> all = service.repository().allItems();
            assertEquals(1, all.size());
            assertEquals("assignment", all.get(0).type());
        }
    }

    @Test
    void failedExtractionGoesToRawInbox(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json("不是JSON")
                .json("{\"type\":\"nonsense\",\"title\":\"\"}");
        try (Database db = new Database(tmp.resolve("s.db"))) {
            AssistantService service = newService(llm, db);
            String reply = service.handle("某条奇怪的消息");
            assertTrue(reply.contains("没能解析"), "应提示解析失败，实际: " + reply);
            assertTrue(service.repository().allItems().isEmpty(), "不应产生业务记录");
        }
    }

    @Test
    void questionFlowReturnsLlmAnswer(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .chat("根据数据库，今天没有课。");
        try (Database db = new Database(tmp.resolve("s.db"))) {
            AssistantService service = newService(llm, db);
            assertEquals("根据数据库，今天没有课。", service.handle("今天上什么课？"));
        }
    }

    @Test
    void correctionFlowUpdatesLastRecord(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"record\"}")
                .json(VALID_ASSIGNMENT)
                .json("{\"intent\":\"correction\"}")
                .json("""
                        {"type":"assignment","title":"","course":"","teacher":"",
                         "content":"","location":"","dueDate":"2026-11-21","dueTime":""}
                        """);
        try (Database db = new Database(tmp.resolve("s.db"))) {
            AssistantService service = newService(llm, db);
            service.handle("高数作业习题5.2，11月20日前交");
            long id = service.repository().allItems().get(0).id();

            String reply = service.handle("不对，截止是11月21日");
            assertTrue(reply.startsWith("✅ 已更新"), "应为更新回执，实际: " + reply);

            StoredItem updated = service.repository().getById("assignment", id).orElseThrow();
            assertEquals("2026-11-21", updated.dueDate());
            assertEquals("习题5.2", updated.title(), "未纠正的字段应保留原值");
        }
    }

    @Test
    void unknownIntentFallsBackToRecord(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"something-else\"}")
                .json(VALID_ASSIGNMENT);
        try (Database db = new Database(tmp.resolve("s.db"))) {
            AssistantService service = newService(llm, db);
            assertTrue(service.handle("随便说点什么").startsWith("✅"));
        }
    }

    @Test
    void questionStreamEmitsDeltas(@TempDir Path tmp) throws Exception {
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .streamChunks("今天", "没", "有课。");
        try (Database db = new Database(tmp.resolve("stream.db"))) {
            AssistantService service = new AssistantService(llm, new ItemRepository(db), db);
            StringBuilder received = new StringBuilder();
            String full = service.handleStreaming("今天有什么课？", received::append);
            assertEquals("今天没有课。", full);
            assertEquals("今天没有课。", received.toString());
        }
    }

    @Test
    void todayCourseIncludedInQuestionPrompt(@TempDir Path tmp) throws Exception {
        int today = LocalDate.now().getDayOfWeek().getValue();
        FakeLlm llm = new FakeLlm()
                .json("{\"intent\":\"question\"}")
                .chat("有");
        try (Database db = new Database(tmp.resolve("today.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(new ExtractedItem("course", "高数", null, "王老师", null, "A201", null, null,
                    "08:00", "09:40", today, null), 1);
            AssistantService service = new AssistantService(llm, repo, db);
            service.handle("今天有什么课？");
            assertTrue(llm.lastUserPrompt.contains("今天("), "应包含今天课程段: " + llm.lastUserPrompt);
            assertTrue(llm.lastUserPrompt.contains("高数"), "应包含课程名: " + llm.lastUserPrompt);
        }
    }

}
