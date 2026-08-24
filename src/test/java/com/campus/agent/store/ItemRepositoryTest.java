package com.campus.agent.store;

import com.campus.agent.model.ExtractedItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemRepositoryTest {

    @Test
    void insertAndListRoundTrip(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("r.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long id = repo.insert(new ExtractedItem(
                    "assignment", "习题5.2", "高数", "王老师", "完成5.2全部", null, "2026-11-20", "23:59", null, null), 1);
            assertTrue(id > 0);

            List<StoredItem> all = repo.allItems();
            assertEquals(1, all.size());
            StoredItem s = all.get(0);
            assertEquals("assignment", s.type());
            assertEquals("习题5.2", s.title());
            assertEquals("2026-11-20", s.dueDate());
            assertEquals("pending", s.status());
        }
    }

    @Test
    void updateByIdMergesFields(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("u.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long id = repo.insert(new ExtractedItem(
                    "todo", "领材料", null, null, "辅导员办公室", null, "2026-11-21", "10:00", null, null), 1);

            boolean updated = repo.updateById(new ExtractedItem(
                    "todo", "领材料", null, null, "改去教务处", null, "2026-11-22", "10:00", null, null), id);
            assertTrue(updated);

            Optional<StoredItem> found = repo.getById("todo", id);
            assertTrue(found.isPresent());
            assertEquals("2026-11-22", found.get().dueDate());
        }
    }

    @Test
    void insertMessageAndRawInbox(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("m.db"))) {
            ItemRepository repo = new ItemRepository(db);
            long mid = repo.insertMessage("user", "老师通知原文");
            assertTrue(mid > 0);

            long rid = repo.insertRaw("解析失败的原文", "title 不能为空");
            assertTrue(rid > 0);

            assertEquals(0, repo.allItems().size()); // raw_inbox 不属于 allItems
        }
    }

    @Test
    void allItemsCoversEveryType(@TempDir Path tmp) throws Exception {
        try (Database db = new Database(tmp.resolve("t.db"))) {
            ItemRepository repo = new ItemRepository(db);
            repo.insert(new ExtractedItem("assignment", "作业1", null, null, null, null, null, null, null, null), 1);
            repo.insert(new ExtractedItem("exam", "考试1", null, null, null, "D105", "2026-12-01", "19:00", null, null), 1);
            repo.insert(new ExtractedItem("todo", "待办1", null, null, null, null, null, null, null, null), 1);
            repo.insert(new ExtractedItem("course", "数据结构", "张老师", "B302", null, null, null, null, "10:00", "11:40"), 1);
            repo.insert(new ExtractedItem("event", "组会", null, null, "实验室", null, "2026-11-27", "16:00", null, null), 1);
            assertEquals(5, repo.allItems().size());
        }
    }
}