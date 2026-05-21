-- V8: 补充缺失的乐观锁 version 列
-- 修复 V7 遗漏：ConversationEntity 新增 @Version 字段但未同步到数据库

ALTER TABLE conversations ADD COLUMN IF NOT EXISTS version INT DEFAULT 0;

UPDATE conversations SET version = COALESCE(version, 0) WHERE version IS NULL;
