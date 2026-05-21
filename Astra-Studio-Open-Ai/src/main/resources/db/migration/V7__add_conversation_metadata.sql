-- V7: 添加会话元数据字段与性能优化索引
-- 用于支持会话列表管理、消息记录查询等功能

-- 1. 添加会话元数据字段（如果不存在）
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS last_message_preview TEXT DEFAULT '';
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 2. 创建复合索引优化会话列表查询（按状态过滤 + 更新时间倒序）
CREATE INDEX IF NOT EXISTS idx_conversations_status_updated ON conversations(status, updated_at DESC);

-- 3. 创建全文搜索索引用于标题模糊匹配（PostgreSQL GIN 索引）
CREATE INDEX IF NOT EXISTS idx_conversations_title_search ON conversations USING gin(to_tsvector('simple', title));

-- 4. 创建消息表复合索引优化分页查询（按会话ID + 序号排序）
CREATE INDEX IF NOT EXISTS idx_messages_conv_seq ON conversation_messages(conversation_id, sequence_num);

-- 5. 为已存在的数据填充默认值
UPDATE conversations SET title = COALESCE(title, '新对话'), message_count = COALESCE(message_count, 0) WHERE title IS NULL OR message_count IS NULL;
