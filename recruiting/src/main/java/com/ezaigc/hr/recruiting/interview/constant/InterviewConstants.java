package com.ezaigc.hr.recruiting.interview.constant;

/** 面试域稳定输入边界。 */
public final class InterviewConstants {

  /** 单场面试允许配置的内部参与人数量上限。 */
  public static final int MAX_PARTICIPANTS_PER_INTERVIEW = 50;

  /** 当前面试官任务页及对应面评状态批量查询上限。 */
  public static final int MAX_MY_INTERVIEW_TASKS = 200;

  /** 一次预读通知模板和参与人时允许处理的面试数量。 */
  public static final int MAX_NOTIFICATION_INTERVIEWS_PER_BATCH = 100;

  /** 面评列表每次按记录与当前版本精确配对读取的数量上限。 */
  public static final int MAX_FEEDBACK_VERSIONS_PER_BATCH = 200;

  /** 推荐撤回时，每次锁定和处理的面试条数。 */
  public static final int SUBMISSION_WITHDRAWAL_PAGE_SIZE = 100;

  /** 招聘活动终止时，每次锁定和处理的待进行面试条数。 */
  public static final int APPLICATION_TERMINATION_PAGE_SIZE = 100;

  /** 负责人、内部参与人与候选人合计的通知意图批量上限。 */
  public static final int MAX_NOTIFICATION_INTENTS_PER_INTERVIEW =
      MAX_PARTICIPANTS_PER_INTERVIEW + 2;

  private InterviewConstants() {}
}
