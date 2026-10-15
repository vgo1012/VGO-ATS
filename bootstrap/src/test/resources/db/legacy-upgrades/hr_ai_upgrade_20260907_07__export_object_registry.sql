-- 版本：20260907_07；导出对象上传前登记及到期回收。
-- 前提：MySQL 8，已选择业务库且 reporting_export_job 已存在；先停止旧版应用及 Worker。
-- 数据影响：只新增一张登记表，不修改旧导出元数据或对象；旧文件继续通过原过期任务清理。
-- 历史上已丢失引用的孤儿对象无法由此升级自动找回，需在测试验证后按存储清单另行核对。
-- CREATE TABLE IF NOT EXISTS 由 MySQL 检查选定库元数据；单条原子 DDL 支持中断续跑与重复执行。
-- 回退应用时保留此表，不删除登记数据；恢复新版后继续清理。上传前登记依赖此表，应先完成升级再启动新版。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `reporting_export_object`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '与导出文件共用的唯一 ID',
    export_job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属导出任务 ID',
    object_key    VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上传前预留且不复用的对象键',
    cleanup_after TIMESTAMP(6) NOT NULL COMMENT '最早清理时间，删除失败后延后重试',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_export_object_key(object_key),
    INDEX ix_reporting_export_object_cleanup(cleanup_after,id),
    CONSTRAINT fk_reporting_export_object_job FOREIGN KEY (export_job_id) REFERENCES reporting_export_job (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出对象生命周期登记';
