-- 前置条件：已部署 JD 解析 Schema 1.1，应用不再生成或读取证据/可靠性字段，并已完成 V2026092102。
-- 数据影响：仅从历史 JD 事实快照 JSON 顶层删除 evidence、confidence、reliability；结构化岗位事实、JD 原文和 AI 原始输出归档不变。
-- 执行前备份：如需保留历史解析 JSON 原值，先备份 demand_jd_fact_snapshot。
-- 恢复方式：从备份按主键回填 facts_json；新版应用不会重新生成已删除字段。
-- 可重入性：只更新仍包含任一废弃顶层字段的记录，支持重复执行和部分完成后续跑。

UPDATE demand_jd_fact_snapshot
SET facts_json = JSON_REMOVE(
  facts_json,
  '$.evidence',
  '$.confidence',
  '$.reliability'
)
WHERE JSON_CONTAINS_PATH(
  facts_json,
  'one',
  '$.evidence',
  '$.confidence',
  '$.reliability'
);
