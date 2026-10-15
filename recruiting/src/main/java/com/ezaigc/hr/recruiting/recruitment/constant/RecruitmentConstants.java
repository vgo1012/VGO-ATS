package com.ezaigc.hr.recruiting.recruitment.constant;

/** 应聘流程域稳定输入边界。 */
public final class RecruitmentConstants {

  /** 单个应聘流程允许的协作人数量上限。 */
  public static final int MAX_COLLABORATORS_PER_APPLICATION = 50;

  /** 单次候选人意向记录允许的地点数量上限。 */
  public static final int MAX_LOCATIONS_PER_INTENTION = 20;

  /** 单个意向地点允许的字符数上限。 */
  public static final int MAX_INTENTION_LOCATION_LENGTH = 100;

  /** 意向地点持久化字段的字符数上限。 */
  public static final int MAX_INTENTION_LOCATIONS_STORAGE_LENGTH = 500;

  public static final int MAX_APPLICATION_DECISION_LENGTH = 16;
  public static final int MAX_APPLICATION_STEP_RESULT_LENGTH = 16;
  public static final int MAX_COMMUNICATION_CHANNEL_LENGTH = 32;
  public static final int MAX_OFFER_STATUS_LENGTH = 20;
  public static final int MAX_OFFER_TERMINATION_REASON_LENGTH = 32;
  public static final int MAX_APPLICATION_OUTCOME_LENGTH = 16;

  private RecruitmentConstants() {}
}
