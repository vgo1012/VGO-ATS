package com.ezaigc.hr.reporting.outbox.constant;

/** 持久任务通知协议和独立发布任务的常量。 */
public final class OutboxConstants {
  public static final String PUBLISH_TASK_TYPE = "OUTBOX_PUBLISH";
  public static final String EVENT_TYPE = "TASK_AVAILABLE";
  public static final int EVENT_VERSION = 1;

  private OutboxConstants() {}
}
