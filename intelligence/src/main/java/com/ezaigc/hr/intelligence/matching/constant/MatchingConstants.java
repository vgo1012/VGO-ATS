package com.ezaigc.hr.intelligence.matching.constant;

/**
 * 匹配模块常量定义。
 * <p>
 * Stable matching-module constants shared by HTTP and worker entry points.
 */
public final class MatchingConstants {

  /** 维度配置、技能分组和附加要求证据语义变更后隔离旧评分缓存。 */
  public static final String DETERMINISTIC_ALGORITHM_VERSION = "deterministic-v5-five-dimensions";

  /** 共享任务失租恢复使用的稳定错误编码。 */
  public static final String TASK_LEASE_EXPIRED = "TASK_LEASE_EXPIRED";
  public static final String MATCH_RUN_TASK_TYPE = "MATCH_RUN";
  public static final int DEFAULT_PAGE_LIMIT = 20;
  public static final int MAX_PAGE_LIMIT = 100;
  /** 单次正式匹配允许固化的候选简历数量上限。 */
  public static final int MAX_MATCH_RUN_RESUMES = 1000;
  /** 单个匹配配置允许的受控条件数量上限。 */
  public static final int MAX_SELECTED_CRITERIA = 5;
  /** 单个基础评价允许展示和持久化的亮点数量上限。 */
  public static final int MAX_MATCH_HIGHLIGHTS = 4;
  /** 单条匹配亮点长度上限，与 matching_highlight.highlight_text 列保持一致。 */
  public static final int MAX_MATCH_HIGHLIGHT_TEXT_LENGTH = 500;
  /** 匹配明细的 MyBatis/JDBC 单批写入大小。 */
  public static final int MATCH_DETAIL_WRITE_BATCH_SIZE = 200;
  public static final int MAX_SORT_MODE_LENGTH = 32;
  public static final int MAX_CRITERION_OPERATOR_LENGTH = 24;
  /** 单个期望值 JSON 的 HTTP 输入上限，防止解析超大条件对象。 */
  public static final int MAX_EXPECTED_VALUE_JSON_LENGTH = 10_000;
  public static final int MAX_CRITERION_REASON_LENGTH = 500;
  public static final int MAX_EVIDENCE_POLICY_LENGTH = 80;
  public static final int MAX_CRITERION_BONUS_POINTS = 20;

  private MatchingConstants() {}
}
