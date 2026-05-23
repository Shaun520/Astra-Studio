-- V7 验证脚本：检查迁移是否成功执行
-- 在执行完 V7__add_conversation_metadata.sql 后运行此脚本进行验证

-- 1. 检查 conversations 表结构
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'conversations'
AND column_name IN ('title', 'last_message_preview', 'message_count', 'deleted_at', 'status', 'updated_at')
ORDER BY ordinal_position;

-- 2. 验证索引是否存在
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'conversations'
AND indexname IN (
    'idx_conversations_status_updated',
    'idx_conversations_title_search'
);

-- 3. 验证消息表索引
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'conversation_messages'
AND indexname = 'idx_messages_conv_seq';

-- 4. 检查数据完整性（无 NULL 值）
SELECT 
    COUNT(*) AS total_rows,
    COUNT(title) AS non_null_title,
    COUNT(last_message_preview) AS non_null_preview,
    COUNT(message_count) AS non_null_count,
    COUNT(CASE WHEN title IS NULL OR title = '' THEN 1 END) AS empty_title_count
FROM conversations;

-- 5. 性能测试查询（模拟会话列表加载）
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, memory_id, title, message_count, updated_at
FROM conversations
WHERE status != -1
ORDER BY updated_at DESC
LIMIT 20;

-- 6. 测试全文搜索索引
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, memory_id, title
FROM conversations
WHERE status != -1
AND to_tsvector('simple', title) @@ to_tsvector('simple', 'test')
LIMIT 10;

-- 输出验证结果摘要
DO $$
DECLARE
    v_columns_ok INT;
    v_indexes_ok INT;
BEGIN
    -- 检查列是否存在
    SELECT COUNT(*) INTO v_columns_ok
    FROM information_schema.columns
    WHERE table_name = 'conversations'
    AND column_name IN ('last_message_preview', 'deleted_at');

    -- 检查索引是否存在
    SELECT COUNT(*) INTO v_indexes_ok
    FROM pg_indexes
    WHERE tablename = 'conversations'
    AND indexname IN ('idx_conversations_status_updated', 'idx_conversations_title_search');

    IF v_columns_ok = 2 AND v_indexes_ok = 2 THEN
        RAISE NOTICE '✅ V7 migration validation PASSED';
        RAISE NOTICE '   - All required columns exist';
        RAISE NOTICE '   - All indexes created successfully';
    ELSE
        RAISE NOTICE '❌ V7 migration validation FAILED';
        RAISE NOTICE '   - Columns found: % (expected 2)', v_columns_ok;
        RAISE NOTICE '   - Indexes found: % (expected 2)', v_indexes_ok;
    END IF;
END $$;
