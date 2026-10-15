package com.ezaigc.hr.common.infrastructure.ai;

/** 外部模型额度耗尽；仅暴露可操作提示，不向业务响应透传供应商错误正文。 */
public final class AiQuotaExceededException extends RuntimeException {
  public static final String CODE = "MODEL_QUOTA_EXHAUSTED";
  public static final String USER_MESSAGE = "模型额度不足，请管理员在模型服务控制台补充额度或调整计费设置后重试";

  public AiQuotaExceededException(Throwable cause) {
    super(USER_MESSAGE, cause);
  }
}
