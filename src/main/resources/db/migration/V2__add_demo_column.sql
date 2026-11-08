-- V2 演练用：给 events 加一个无害列（不参与任何业务逻辑，验完会由 V3 删掉）
ALTER TABLE events ADD COLUMN demo_note TEXT;
