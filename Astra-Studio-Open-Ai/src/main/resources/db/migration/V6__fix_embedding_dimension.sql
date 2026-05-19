-- V6: 修复 embedding 维度 (DashScope text-embedding-v3 仅支持 1024，不支持 1536)
ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(1024);
-- 清除旧的错误维度向量数据（需重新上传文档生成）
TRUNCATE TABLE document_chunks;
UPDATE knowledge_documents SET status = 'FAILED', error_message = 'embedding 维度已从 1536 调整为 1024，请重新上传文档', updated_at = CURRENT_TIMESTAMP WHERE status = 'READY';
