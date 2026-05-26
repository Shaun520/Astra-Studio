-- V9: 知识库文档新增 content_type 字段（支持图片文档类型）

ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS content_type VARCHAR(16) DEFAULT 'text';

COMMENT ON COLUMN knowledge_documents.content_type IS '文档内容类型: text（文本）或 image（图片）';

CREATE INDEX IF NOT EXISTS idx_documents_content_type ON knowledge_documents(content_type);
