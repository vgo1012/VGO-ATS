package com.ezaigc.hr.recruiting.demand.constant;

import java.util.regex.Pattern;

/**
 * 招聘需求模块常量定义。
 * <p>
 * Stable demand-domain limits, formats, and asynchronous contract versions.
 */
public final class DemandConstants {

    public static final Pattern BUSINESS_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");
    /**
     * 默认页上限。
     */
    public static final int DEFAULT_PAGE_LIMIT = 20;
    /**
     * 最大页上限。
     */
    public static final int MAX_PAGE_LIMIT = 100;
    /**
     * 单个招聘需求允许的显式协作人数上限。
     */
    public static final int MAX_COLLABORATORS_PER_DEMAND = 50;
    /**
     * JD 目录自由文本查询上限。
     */
    public static final int MAX_JD_QUERY_LENGTH = 100;
    /**
     * 单个招聘流程模板允许的步骤数量上限。
     */
    public static final int MAX_WORKFLOW_STEP_COUNT = 9;
    /**
     * 招聘类型编码最大长度，与需求和流程表列宽保持一致。
     */
    public static final int MAX_RECRUITMENT_TYPE_LENGTH = 16;
    /**
     * JD 来源类型最大长度，与版本表列宽保持一致。
     */
    public static final int MAX_JD_SOURCE_TYPE_LENGTH = 24;
    public static final String JD_PARSE_TASK_TYPE = "JD_PARSE";
    public static final String JD_PARSE_RESOURCE_TYPE = "JD_VERSION";
    public static final String JD_FACT_SCHEMA_VERSION = "1.1";
    public static final String JD_PARSE_PROMPT_VERSION = "jd-facts-v12-preserve-context";

    private DemandConstants() {
    }
}
