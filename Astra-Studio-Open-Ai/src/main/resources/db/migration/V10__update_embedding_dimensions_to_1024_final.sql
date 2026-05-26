-- V10: 将 embedding 向量维度从 768 更新为 1024（最终方案：API返回1152维，代码层截断到1024维）
--
-- 原因：
-- 1. tongyi-embedding-vision-plus-2026-03-06 不支持 dimensions 参数，始终输出 1152 维
-- 2. text-embedding-v3 最大支持 1024 维
-- 3. 解决方案：代码层将 1152 维向量截断为 1024 维（保留前 1024 维，信息损失约 11%）
--
-- 注意：由于 pgvector 不支持不同维度之间的直接类型转换，
-- 此迁移脚本会删除现有的 document_chunks 数据并重建表结构。

BEGIN;

-- Step 1: 删除 document_chunks 表中的所有现有数据（768维向量无法直接转为1024维）
DELETE FROM document_chunks;

-- Step 2: 修改 embedding 列的维度从 768 改为 1024
ALTER TABLE document_chunks 
DROP COLUMN embedding,
ADD COLUMN embedding vector(1024);

-- Step 3: 更新列注释
COMMENT ON COLUMN document_chunks.embedding IS '文档块向量（1024维），文本使用text-embedding-v3原生输出，图片使用多模态Embedding截断处理';

COMMIT;
