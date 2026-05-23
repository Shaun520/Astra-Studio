-- V7 回滚脚本：撤销 V7 迁移的更改
-- ⚠️ 警告：执行前请确保已备份数据库！

-- 1. 删除新增的索引
DROP INDEX IF EXISTS idx_conversations_status_updated;
DROP INDEX IF EXISTS idx_conversations_title_search;
DROP INDEX IF EXISTS idx_messages_conv_seq;

-- 2. 删除新增的列
ALTER TABLE conversations DROP COLUMN IF EXISTS last_message_preview;
ALTER TABLE conversations DROP COLUMN IF EXISTS deleted_at;

-- 验证回滚是否成功
DO $$
DECLARE
    v_columns_remaining INT;
    v_indexes_remaining INT;
BEGIN
    -- 检查列是否已删除
    SELECT COUNT(*) INTO v_columns_remaining
    FROM information_schema.columns
    WHERE table_name = 'conversations'
    AND column_name IN ('last_message_preview', 'deleted_at');

    -- 检查索引是否已删除
    SELECT COUNT(*) INTO v_indexes_remaining
    FROM pg_indexes
    WHERE indexname IN (
        'idx_conversations_status_updated',
        'idx_conversations_title_search',
        'idx_messages_conv_seq'
    );

    IF v_columns_remaining = 0 AND v_indexes_remaining = 0 THEN
        RAISE NOTICE '✅ V7 rollback SUCCESS';
        RAISE NOTICE '   - All new columns removed';
        RAISE NOTICE '   - All indexes dropped';
    ELSE
        RAISE NOTICE '❌ V7 rollback FAILED';
        RAISE NOTICE '   - Columns remaining: %', v_columns_remaining;
        RAISE NOTICE '   - Indexes remaining: %', v_indexes_remaining;
    END IF;
END $$;
