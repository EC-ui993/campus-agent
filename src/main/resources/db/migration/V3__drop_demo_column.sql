-- V3 演练用：用"新版本"表达回滚，删掉 V2 加进来的 demo_note 列。
-- 重点：Flyway 不会回滚已应用的脚本（V2 永远留在历史里）。
-- 所谓"回滚" = 写一个新版本，做相反的操作。
ALTER TABLE events DROP COLUMN demo_note;
