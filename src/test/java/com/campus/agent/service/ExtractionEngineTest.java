package com.campus.agent.service;

import com.campus.agent.model.ExtractedProfile;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.ProfileItem;
import com.campus.agent.store.StoredItem;
import com.campus.agent.store.StudyProgressItem;
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
class ExtractionEngineTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("extraction-engine-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired ItemRepository repo;
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

    private ExtractionEngine engine(FakeLlm llm) {
        return new ExtractionEngine(llm, repo);
    }

    @Test
    void validExtractionStoresAndOk() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"assignment","title":"习题5.2","course":"高数","teacher":"",
                 "content":"","location":"","dueDate":"2026-11-20","dueTime":"23:59"}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("高数作业", 1);
        assertTrue(o.ok());
        assertEquals(1, repo.allItems().size());
    }

    @Test
    void twoBadJsonGoesToRawInboxAndFail() {
        FakeLlm llm = new FakeLlm().json("不是JSON").json("不是JSON");
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("坏消息", 1);
        assertFalse(o.ok());
        assertTrue(repo.allItems().isEmpty());
    }

    @Test
    void semesterSettingBranchWritesSemester() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"semester_setting","startDate":"2026-09-01","totalWeeks":16,"note":""}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("设置学期", 1);
        assertTrue(o.ok());
        assertTrue(repo.semester().isPresent());
        assertEquals(16, repo.semester().orElseThrow().totalWeeks());
    }

    @Test
    void courseOverrideBranchWritesOverride() {
        FakeLlm llm = new FakeLlm().json("""
                {"type":"course_override","courseTitle":"高数","overrideDate":"2026-09-01","kind":"cancel",
                 "newStartTime":"","newEndTime":"","newLocation":"","note":""}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("高数停课", 1);
        assertTrue(o.ok());
    }

    @Test
    void internshipBranchWritesInternship(){
        FakeLlm llm = new FakeLlm().json("""
            {"type":"internship","title":"","course":"","teacher":"",
             "content":"","location":"","dueDate":"","dueTime":"",
             "company":"字节跳动","position":"Java后端实习生","city":"北京",
             "salary":"200-300/天","deadline":"2026-09-15",
             "jd":"负责XX系统开发","link":"https://example.com/jd/1"}
            """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("字节跳动招聘Java后端实习生",1);
        assertTrue(o.ok(), "应成功: " + o.error());
        assertEquals(1, repo.allInternships().size());
        assertEquals("字节跳动", repo.allInternships().get(0).company());
    }

    @Test
    void profileSettingBranchWritesSingleRow(){
        FakeLlm llm = new FakeLlm().json("""
            {"type":"profile_setting","title":"","course":"","teacher":"",
             "content":"","location":"","dueDate":"","dueTime":"",
             "skills":"Java、SQL","targetRole":"Java后端实习生","targetCity":"杭州",
             "grade":"大三","note":""}
            """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("我的技能是Java和SQL，目标Java后端实习生，想在杭州，大三", 1);
        assertTrue(o.ok(),"应成功: " + o.error());
        ProfileItem p = repo.profile().orElseThrow();
        assertEquals("Java后端实习生", p.targetRole());
        assertEquals("杭州", p.targetCity());
    }

    @Test
    void emptyProfileGoesToRawInbox(){
        FakeLlm llm = new FakeLlm().json("""
            {"type":"profile_setting","title":"","course":"","teacher":"",
             "content":"","location":"","dueDate":"","dueTime":"",
             "skills":"","targetRole":"","targetCity":"","grade":"","note":""}
            """).json("""
            {"type":"profile_setting","title":"","course":"","teacher":"",
             "content":"","location":"","dueDate":"","dueTime":"",
             "skills":"","targetRole":"","targetCity":"","grade":"","note":""}
            """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("改一下档案",1);
        assertFalse(o.ok(),"全空档案应失败");
        assertTrue(repo.profile().isEmpty(),"不应写入档案");
    }

    @Test
    void studyProgressWithExplicitDate(){
        FakeLlm llm = new FakeLlm().json("""
                {"type":"study_progress","title":"","course":"","teacher":"",
                 "context":"学了集合框架","location":"","dueDate":"","dueTime":"",
                 "studyDate":"2026-08-31"}
                """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("8月31日学了集合框架",1);
        assertTrue(o.ok(),"应成功: " + o.error());
        List<StudyProgressItem> list = repo.studyProgressBetween(LocalDate.of(2026,8,30),
                                                                 LocalDate.of(2026,8,31));
        assertEquals(1,list.size());
        assertEquals("学了集合框架", list.get(0).context());
    }

    @Test
    void studyProgressDefaultsToToday(){
        FakeLlm llm = new FakeLlm().json("""
                {"type":"study_progress","title":"","course":"","teacher":"",
                 "context":"学了MyBatis-Plus","location":"","dueDate":"","dueTime":"",
                 "studyDate":""}
                """);
        ExtractionEngine.Outcome o = engine((llm)).extractAndStore("今天学了MyBatis-Plus",1);
        assertTrue(o.ok(),"应成功: " + o.error());
        List<StudyProgressItem> list = repo.studyProgressBetween(LocalDate.now(),LocalDate.now());
        assertEquals(1,list.size());
        assertEquals("学了MyBatis-Plus", list.get(0).context());
    }

    @Test
    void assignmentContentStripsCoursePrefix(){
        FakeLlm llm = new FakeLlm().json("""
            {"type":"assignment","title":"计算机组成","course":"计算机组成","teacher":"",
             "content":"计算机组成，习题3、4、6、7、8","location":"","dueDate":"","dueTime":"",
             "startTime":"","endTime":"","weekday":null,"weeks":null}
            """);
        ExtractionEngine.Outcome o = engine(llm).extractAndStore("计算机组成习题3、4、6、7、8",1);
        assertTrue(o.ok(),"应成功"+o.error());
        StoredItem s = repo.allItems().get(0);
        assertEquals("习题3、4、6、7、8",s.content(),"应剥掉'计算机组成，'前缀");
    }
}
