-- ============================================================================
-- V7 ROLLBACK: 移除会话元数据字段与性能优化索引
-- ============================================================================
-- 执行条件: V7__add_conversation_metadata.sql 部署后需要回退时
-- 执行顺序: 必须在应用停机维护期间执行，确保无活跃事务

-- 1. 删除性能优化索引（按创建倒序）
DROP INDEX IF EXISTS idx_messages_conv_seq;

-- 2. 删除全文搜索索引
DROP INDEX IF EXISTS idx_conversations_title_search;

-- 3. 删除复合索引
DROP INDEX IF EXISTS idx_conversations_status_updated;

-- 4. 删除新增的元数据字段
ALTER TABLE conversations DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE conversations DROP COLUMN IF EXISTS last_message_preview;

-- 注意: 此回滚脚本不会恢复数据，仅移除V7添加的结构
-- 建议: 回滚前先备份数据库
