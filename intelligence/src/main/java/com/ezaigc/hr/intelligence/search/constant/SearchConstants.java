package com.ezaigc.hr.intelligence.search.constant;

import java.time.Instant;

/** 搜索模块常量定义。 */
public final class SearchConstants {
  /** 重建任务类型。 */
  public static final String REBUILD_TASK_TYPE = "SEARCH_INDEX_REBUILD";
  /** 简历事实列表投影。 */
  public static final String RESUME_FACTS_PROJECTION = "RESUME_FACTS";
  /** 独立批准行；version 是批准代际，其余游标字段为固定占位，不能用于投影进度。 */
  public static final String RESUME_FACTS_APPROVED_INDEX = "RESUME_FACTS_APPROVED_INDEX";
  /** 搜索索引及别名的统一名称规则。 */
  public static final String INDEX_NAME_PATTERN = "[a-z0-9][a-z0-9._-]{0,118}";
  public static final Instant PROJECTION_EPOCH = Instant.parse("2000-01-01T00:00:00Z");
  /** 最大搜索页大小。 */
  public static final int MAX_SEARCH_PAGE_SIZE = 100;
  /** 单类结构化筛选值上限，防止单次请求生成超大查询。 */
  public static final int MAX_FILTER_VALUES = 50;
  /** 单次搜索的结构化词条总数上限。 */
  public static final int MAX_TOTAL_FILTER_VALUES = 100;
  /** 单个结构化筛选值最大字符数。 */
  public static final int MAX_FILTER_VALUE_LENGTH = 200;
  /** 关键词查询最大字符数。 */
  public static final int MAX_QUERY_LENGTH = 500;
  /** 自然语言筛选最大字符数。 */
  public static final int MAX_NATURAL_CONDITION_LENGTH = 1000;
  /** URL 游标最大字符数。 */
  public static final int MAX_CURSOR_LENGTH = 4096;
  /** 候选人可检索的最大年龄。 */
  public static final int MAX_CANDIDATE_AGE = 100;
  /** 候选人可检索的最小年龄。 */
  public static final int MIN_CANDIDATE_AGE = 16;
  /** 可检索的最大工作年限。 */
  public static final int MAX_WORK_YEARS = 80;
  /** Bean Validation 使用的最小工作年限文本。 */
  public static final String MIN_WORK_YEARS_DECIMAL = "0";
  /** Bean Validation 使用的最大工作年限文本。 */
  public static final String MAX_WORK_YEARS_DECIMAL = "80";
  /** 仅允许搜索简历中有明确证据的性别枚举。 */
  public static final String CANDIDATE_GENDER_PATTERN = "(?i)MALE|FEMALE|OTHER";
  /** 最大投影页大小。 */
  public static final int MAX_PROJECTION_PAGE_SIZE = 500;
  private SearchConstants() {}
}
