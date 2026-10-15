package com.ezaigc.hr.identity.constant;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.IDENTIFIER_LENGTH;

/**
 * 身份模块常量定义。
 * <p>
 * Stable identity and access-management vocabulary.
 */
public final class IdentityConstants {

    public static final int MAX_WORK_EMAIL_LENGTH = 320;
    /** 用户展示名称最大长度，与账户表列宽保持一致。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;
    /** 来源职位名称最大长度，与账户表列宽保持一致。 */
    public static final int MAX_SOURCE_TITLE_LENGTH = 100;
    /** 部门编码最大长度，与部门表列宽保持一致。 */
    public static final int MAX_DEPARTMENT_CODE_LENGTH = 64;
    /** 部门名称最大长度，与部门表列宽保持一致。 */
    public static final int MAX_DEPARTMENT_NAME_LENGTH = 100;
    /** 状态编码最大长度。 */
    public static final int MAX_STATUS_CODE_LENGTH = 16;
    /** 审计和变更原因最大长度。 */
    public static final int MAX_REASON_LENGTH = 500;
    /**
     * 用户列表查询允许覆盖完整工作邮箱。
     */
    public static final int MAX_USER_QUERY_LENGTH = MAX_WORK_EMAIL_LENGTH;

    public static final int MAX_PASSWORD_LENGTH = 256;

    /**
     * 角色编码数据库列长度；HTTP 输入在规范化前也不得超过该边界。
     */
    public static final int MAX_ROLE_CODE_INPUT_LENGTH = 64;

    /** 单个用户允许关联的支持部门数量上限，防止批量关系写入失控。 */
    public static final int MAX_SUPPORT_DEPARTMENTS_PER_USER = 50;

    /** 主部门加支援部门关系的单次 JDBC 批量写入上限。 */
    public static final int USER_ORGANIZATION_WRITE_BATCH_SIZE =
            MAX_SUPPORT_DEPARTMENTS_PER_USER + 1;

    /** 普通可分配角色的封闭集合大小。 */
    public static final int MAX_ASSIGNABLE_ROLES_PER_USER = 3;

    /** 单次交接选择允许携带的资源键数量上限。 */
    public static final int MAX_HANDOVER_SELECTED_RESOURCES = 10_000;
    /** 交接选择模式最大长度。 */
    public static final int MAX_HANDOVER_SELECTION_MODE_LENGTH = 16;
    /** 交接资源键由最多 40 位资源类型、冒号和平台 ID 组成。 */
    public static final int MAX_HANDOVER_RESOURCE_KEY_LENGTH = 40 + 1 + IDENTIFIER_LENGTH;
    /** 交接资源键线路格式：受控资源类型加平台 ID。 */
    public static final String HANDOVER_RESOURCE_KEY_INPUT_PATTERN =
            "^[A-Za-z0-9_.-]{1,40}:[A-Za-z0-9]{26}$";

    /** 部门编码线路格式，兼容应用层的大小写和首尾空格规范化。 */
    public static final String DEPARTMENT_CODE_INPUT_PATTERN =
            "(?i)^ *[A-Z0-9][A-Z0-9_-]{1,63} *$";

    /**
     * 普通角色管理接口允许的封闭角色集合，兼容既有的大小写和首尾空白规范化。
     */
    public static final String ASSIGNABLE_ROLE_CODE_INPUT_PATTERN =
            "(?i)^\\s*(?:RECRUITER|RECRUITMENT_MANAGER|HR_DIRECTOR|INTERVIEWER)\\s*$";

    private IdentityConstants() {
    }
}
