-- 前置条件：已部署不再生成简历证据/可靠性字段的应用版本，并已完成 V2026092101。
-- 数据影响：仅从历史成功解析 JSON 顶层删除 evidence、confidence、reliability；候选人事实、原始简历和 AI 原始输出归档不变。
-- 执行前备份：如需保留历史解析 JSON 原值，先备份 talent_resume_intake_processing。
-- 恢复方式：从备份按主键回填 parsed_facts_json；新版应用不会重新生成已删除字段。
-- 可重入性：只更新 JSON 有效且仍包含任一废弃顶层字段的记录，支持重复执行。

UPDATE talent_resume_intake_processing
SET parsed_facts_json = JSON_REMOVE(
  CAST(parsed_facts_json AS JSON),
  '$.evidence',
  '$.confidence',
  '$.reliability'
)
WHERE parsed_facts_json IS NOT NULL
  AND JSON_VALID(parsed_facts_json)
  AND JSON_CONTAINS_PATH(
    CAST(parsed_facts_json AS JSON),
    'one',
    '$.evidence',
    '$.confidence',
    '$.reliability'
  );
