package com.campus.agent;

import com.campus.agent.service.AssistantService;
import com.campus.agent.store.Database;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.mapper.AssignmentMapper;
import com.campus.agent.store.mapper.CourseMapper;
import com.campus.agent.store.mapper.CourseOverrideMapper;
import com.campus.agent.store.mapper.EventMapper;
import com.campus.agent.store.mapper.ExamMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 10 条典型老师消息走完整流水线（用假 LLM 驱动），保证改代码后管道不退化。 */
@SpringBootTest
class SampleMessagesTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("agent-samples-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired
    ItemRepository repo;

    @Autowired
    Database db;

    // 每条 = {用户消息, 抽取结果JSON}

    @Autowired AssignmentMapper assignmentMapper;
    @Autowired ExamMapper examMapper;
    @Autowired TodoMapper todoMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EventMapper eventMapper;
    @Autowired CourseOverrideMapper courseOverrideMapper;

    @BeforeEach
    void cleanDb() {
        courseOverrideMapper.delete(null);
        assignmentMapper.delete(null);
        examMapper.delete(null);
        todoMapper.delete(null);
        courseMapper.delete(null);
        eventMapper.delete(null);
    }

    private static final String[][] SAMPLES = {
            {"老师通知：下周一（11月24日）下午两点在A201开会",
                    "{\"type\":\"event\",\"title\":\"老师会议\",\"course\":\"\",\"teacher\":\"\",\"content\":\"老师通知开会\",\"location\":\"A201\",\"dueDate\":\"2026-11-24\",\"dueTime\":\"14:00\"}"},
            {"高数作业：习题5.2，11月20日前交",
                    "{\"type\":\"assignment\",\"title\":\"习题5.2\",\"course\":\"高数\",\"teacher\":\"\",\"content\":\"\",\"location\":\"\",\"dueDate\":\"2026-11-20\",\"dueTime\":\"\"}"},
            {"下周二英语课考试，考1-5单元",
                    "{\"type\":\"exam\",\"title\":\"英语考试\",\"course\":\"英语\",\"teacher\":\"\",\"content\":\"1-5单元\",\"location\":\"\",\"dueDate\":\"2026-11-25\",\"dueTime\":\"\"}"},
            {"记住明天上午10点去辅导员办公室领材料",
                    "{\"type\":\"todo\",\"title\":\"领材料\",\"course\":\"\",\"teacher\":\"\",\"content\":\"辅导员办公室\",\"location\":\"\",\"dueDate\":\"2026-08-16\",\"dueTime\":\"10:00\"}"},
            {"数据库课在周三3-4节，B302，老师是张老师",
                    "{\"type\":\"course\",\"title\":\"数据库\",\"course\":\"\",\"teacher\":\"张老师\",\"content\":\"\",\"location\":\"B302\",\"dueDate\":\"\",\"dueTime\":\"\"}"},
            {"实验报告周五前交给学委",
                    "{\"type\":\"assignment\",\"title\":\"实验报告\",\"course\":\"\",\"teacher\":\"\",\"content\":\"交给学委\",\"location\":\"\",\"dueDate\":\"2026-08-21\",\"dueTime\":\"\"}"},
            {"12月1日晚上7点教学楼D105 英语四级模拟考",
                    "{\"type\":\"exam\",\"title\":\"英语四级模拟考\",\"course\":\"英语\",\"teacher\":\"\",\"content\":\"\",\"location\":\"D105\",\"dueDate\":\"2026-12-01\",\"dueTime\":\"19:00\"}"},
            {"记得给社团发活动总结",
                    "{\"type\":\"todo\",\"title\":\"发活动总结\",\"course\":\"\",\"teacher\":\"\",\"content\":\"给社团\",\"location\":\"\",\"dueDate\":\"\",\"dueTime\":\"\"}"},
            {"周五下午4点实验室组会",
                    "{\"type\":\"event\",\"title\":\"组会\",\"course\":\"\",\"teacher\":\"\",\"content\":\"实验室组会\",\"location\":\"实验室\",\"dueDate\":\"2026-08-21\",\"dueTime\":\"16:00\"}"},
            {"数据结构期末大作业12月10日截止，要求实现一个学生管理系统",
                    "{\"type\":\"assignment\",\"title\":\"学生管理系统大作业\",\"course\":\"数据结构\",\"teacher\":\"\",\"content\":\"实现一个学生管理系统\",\"location\":\"\",\"dueDate\":\"2026-12-10\",\"dueTime\":\"\"}"},
    };

    @Test
    void allTenSamplesFlowThroughPipeline() throws Exception {
        FakeLlm llm = new FakeLlm();
        for (String[] sample : SAMPLES) {
            llm.json("{\"intent\":\"record\"}").json(sample[1]);
        }
        AssistantService service = new AssistantService(llm, repo, db);
        for (String[] sample : SAMPLES) {
            String reply = service.handle(sample[0]);
            assertTrue(reply.startsWith("✅"), "样例应成功入库，实际回复: " + reply);
        }
        List<?> all = repo.allItems();
        assertEquals(10, all.size(), "10 条样例都应入库");
    }
}
