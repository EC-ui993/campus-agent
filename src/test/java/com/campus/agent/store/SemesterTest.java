package com.campus.agent.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SemesterTest {

    private static Path dbPath;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception {
        dbPath = Files.createTempFile("semester-test", ".db");
        Files.deleteIfExists(dbPath);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbPath);
    }

    @Autowired
    ItemRepository repo;

    @BeforeEach
    void reset() {
        repo.clearSemester();
    }

    @Test
    void setAndReadSemesterRoundTrip() {
        repo.setSemester(LocalDate.of(2026, 9, 1), 16);
        Optional<SemesterInfo> s = repo.semester();
        assertTrue(s.isPresent());
        assertEquals(LocalDate.of(2026, 9, 1), s.get().start());
        assertEquals(16, s.get().totalWeeks());
    }

    @Test
    void setSemesterTwiceOverwrites() {
        repo.setSemester(LocalDate.of(2026, 9, 1), 16);
        repo.setSemester(LocalDate.of(2027, 3, 1), 18);
        assertEquals(18, repo.semester().orElseThrow().totalWeeks());
    }

    @Test
    void weekOfBoundaries() {
        repo.setSemester(LocalDate.of(2026, 9, 1), 16);
        assertEquals(0, repo.weekOf(LocalDate.of(2026, 8, 31)).orElseThrow(), "开学前一天为 0");
        assertEquals(1, repo.weekOf(LocalDate.of(2026, 9, 1)).orElseThrow(), "开学当天第 1 周");
        assertEquals(2, repo.weekOf(LocalDate.of(2026, 9, 8)).orElseThrow(), "第 8 天第 2 周");
        assertEquals(16, repo.weekOf(LocalDate.of(2026, 12, 15)).orElseThrow(), "最后一周");
        assertEquals(17, repo.weekOf(LocalDate.of(2026, 12, 22)).orElseThrow(), "超过总周数→假期");
    }

    @Test
    void weekOfUnsetSemesterReturnsMinusOne() {
        assertEquals(-1, repo.weekOf(LocalDate.now()).orElse(-2));
    }
}
