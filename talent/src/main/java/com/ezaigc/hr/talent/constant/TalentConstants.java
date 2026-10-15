package com.ezaigc.hr.talent.constant;

import java.util.regex.Pattern;

/**
 * 人才模块常量定义。
 * <p>
 * Stable talent-domain limits and vocabulary shared by application components.
 */
public final class TalentConstants {

    /** 平台 ULID 的固定长度。 */
    public static final int PLATFORM_ID_LENGTH = 26;
    /** 候选人邮箱的线路最大长度，与 RFC 邮箱字段和数据库列保持一致。 */
    public static final int CANDIDATE_EMAIL_MAX_LENGTH = 320;
    /** 可持久化邮箱的基础线路格式。 */
    public static final String CANDIDATE_EMAIL_INPUT_PATTERN =
            "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    /** 候选人列表查询允许覆盖完整邮箱，同时限制模糊查询成本。 */
    public static final int MAX_CANDIDATE_QUERY_LENGTH = CANDIDATE_EMAIL_MAX_LENGTH;
    /** 候选人电话原始输入的最大长度。 */
    public static final int CANDIDATE_PHONE_MAX_LENGTH = 40;
    /** 候选人电话只允许可规范化的数字、国际区号和常用分隔符。 */
    public static final String CANDIDATE_PHONE_INPUT_PATTERN = "[0-9+() .-]*";
    /** 来源渠道编码最大长度。 */
    public static final int SOURCE_CHANNEL_CODE_MAX_LENGTH = 64;
    /** 来源渠道编码线路格式。 */
    public static final String SOURCE_CHANNEL_CODE_INPUT_PATTERN =
            "(?i)^ *[A-Z0-9][A-Z0-9_-]{1,63} *$";
    /** 来源渠道类型最大长度。 */
    public static final int SOURCE_CHANNEL_TYPE_MAX_LENGTH = 30;
    /** 重复候选人冲突解决编码最大长度。 */
    public static final int DUPLICATE_RESOLUTION_MAX_LENGTH = 40;
    /** 学历风险相关枚举编码最大长度。 */
    public static final int EDUCATION_RISK_CODE_MAX_LENGTH = 32;
    /** 下载授权令牌由两个标准 UUID 串联组成。 */
    public static final int RESUME_DOWNLOAD_TOKEN_LENGTH = 72;
    /** 下载授权令牌的线路格式；令牌区分大小写且生成端固定输出小写十六进制。 */
    public static final String RESUME_DOWNLOAD_TOKEN_INPUT_PATTERN =
            "(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}){2}";
    /** 非 HTTP 调用使用的同源下载令牌校验器。 */
    public static final Pattern RESUME_DOWNLOAD_TOKEN_PATTERN =
            Pattern.compile(RESUME_DOWNLOAD_TOKEN_INPUT_PATTERN);
    public static final Pattern SOURCE_CHANNEL_CODE =
            Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");
    /** 单次批量装载的最大简历事实数量。 */
    public static final int MAX_RESUME_FACT_BATCH_SIZE = 1_000;
    /** 人工更正和模型输出共用的简历事实边界。 */
    public static final int MAX_RESUME_ITEM_ID_LENGTH = 64;
    public static final int MAX_RESUME_FULL_NAME_LENGTH = 160;
    public static final int MAX_RESUME_ROLE_LENGTH = 160;
    public static final int MAX_RESUME_CITY_LENGTH = 120;
    public static final int MAX_RESUME_JOB_INTENTION_LENGTH = 300;
    public static final int MAX_RESUME_ORGANIZATION_NAME_LENGTH = 200;
    public static final int MAX_RESUME_SHORT_LABEL_LENGTH = 120;
    public static final int MAX_RESUME_CODE_LENGTH = 80;
    public static final int MAX_RESUME_LEVEL_LENGTH = 100;
    public static final int MAX_RESUME_DESCRIPTION_LENGTH = 5_000;
    public static final int MAX_RESUME_LIST_TEXT_LENGTH = 500;
    public static final int MAX_RESUME_UNKNOWN_ITEM_LENGTH = 300;
    public static final int MAX_RESUME_RESPONSIBILITY_TAG_LENGTH = 200;
    public static final int MAX_RESUME_DATE_LENGTH = 10;
    public static final int MAX_RESUME_JSON_POINTER_LENGTH = 500;
    public static final int MAX_RESUME_WORK_EXPERIENCES = 100;
    public static final int MAX_RESUME_PROJECT_EXPERIENCES = 200;
    public static final int MAX_RESUME_EDUCATIONS = 30;
    public static final int MAX_RESUME_SKILLS = 300;
    public static final int MAX_RESUME_CREDENTIALS = 100;
    public static final int MAX_RESUME_LANGUAGES = 50;
    public static final int MAX_RESUME_INDUSTRY_TAGS = 100;
    public static final int MAX_RESUME_RESPONSIBILITY_TAGS = 200;
    public static final int MAX_RESUME_UNKNOWN_ITEMS = 200;
    /** 简历子事实 JDBC 批量写入大小，兼顾往返次数与单批内存占用。 */
    public static final int RESUME_FACT_WRITE_BATCH_SIZE = 200;
    /** 文档抽取适配器允许返回的最大分页数量，与集成模块配置上限保持一致。 */
    public static final int MAX_RESUME_EXTRACTION_PAGES = 1_000;
    /** 抽取页文本批量写入大小，避免长事务中逐页往返数据库。 */
    public static final int RESUME_EXTRACTION_PAGE_WRITE_BATCH_SIZE = 200;
    /** 单次人工纠错允许持久化的字段级变更数量上限。 */
    public static final int MAX_FACT_CORRECTION_CHANGES = 200;
    /** 证书、语言、行业、职责和未知项合并后的简单事实总上限。 */
    public static final int MAX_RESUME_SIMPLE_FACTS =
            MAX_RESUME_CREDENTIALS
                    + MAX_RESUME_LANGUAGES
                    + MAX_RESUME_INDUSTRY_TAGS
                    + MAX_RESUME_RESPONSIBILITY_TAGS
                    + MAX_RESUME_UNKNOWN_ITEMS;
    public static final int MAX_RESUME_ITEM_TEXTS = 100;
    public static final int MAX_RESUME_BOUNDING_BOX_COORDINATES = 4;
    public static final int MIN_RESUME_AGE = 16;
    public static final int MAX_RESUME_AGE = 100;
    public static final String MIN_RESUME_YEARS = "0.0";
    public static final String MAX_RESUME_YEARS = "80.0";
    public static final double MIN_RESUME_YEARS_VALUE = 0.0D;
    public static final double MAX_RESUME_YEARS_VALUE = 80.0D;
    public static final String MIN_NORMALIZED_COORDINATE = "0.0";
    public static final String MAX_NORMALIZED_COORDINATE = "1.0";
    public static final String RESUME_GENDER_INPUT_PATTERN = "(?i)(MALE|FEMALE|OTHER)?";
    public static final String RESUME_ITEM_ID_INPUT_PATTERN = "[A-Za-z0-9_-]{1,64}";
    public static final String RESUME_DATE_INPUT_PATTERN = "(?:\\d{4}(?:-\\d{2}(?:-\\d{2})?)?|PRESENT)?";
    /**
     * 简历解析任务类型。
     */
    public static final String RESUME_PARSE_TASK_TYPE = "RESUME_PARSE";
    /**
     * 简历事实结构版本。
     */
    public static final String RESUME_FACT_SCHEMA_VERSION = "1.2";
    /**
     * 简历解析提示词版本。
     */
    public static final String RESUME_PARSE_PROMPT_VERSION = "resume-facts-v27-explicit-age";

    private TalentConstants() {
    }
}
