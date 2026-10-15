-- 前置条件：已部署 2026-09-21 基线，且 talent_candidate、talent_resume_version、
-- demand_jd、demand_jd_version、matching_match_run 均存在。
-- 数据影响：仅新增不可变评分绑定表；不改写历史收件、评分、招聘、面试或面评数据。
-- 支持重复执行：CREATE TABLE IF NOT EXISTS 会在目标表已存在时保持现状。

CREATE TABLE IF NOT EXISTS `talent_resume_jd_score_binding`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    operation_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '一次批量重评操作 ID',
    candidate_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    resume_version_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '精确简历版本 ID',
    jd_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '逻辑 JD ID',
    jd_version_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评分使用的精确 JD 版本 ID',
    default_match_run_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公司默认评分运行 ID',
    selected_by_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '选择 JD 并发起评分的用户 ID',
    owner_department_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起时归属部门 ID',
    selected_at           TIMESTAMP(6) NOT NULL COMMENT '选择并发起评分时间',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_talent_resume_jd_score_run (default_match_run_id),
    INDEX ix_talent_resume_jd_score_operation (operation_id, id),
    INDEX ix_talent_resume_jd_score_candidate (candidate_id, resume_version_id, selected_at, id),
    INDEX ix_talent_resume_jd_score_identity (resume_version_id, jd_version_id, selected_at, id),
    CONSTRAINT fk_talent_resume_jd_score_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate(id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_jd_score_resume FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_jd_score_jd FOREIGN KEY (jd_id) REFERENCES demand_jd(id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_jd_score_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_jd_score_run FOREIGN KEY (default_match_run_id) REFERENCES matching_match_run(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享简历重新选择 JD 后的不可变默认评分绑定';
