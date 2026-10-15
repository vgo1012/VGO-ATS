package com.ezaigc.hr.identity.api;

import java.util.function.Supplier;

/**
 * 在后台任务线程中，以已记录请求人的当前有效身份执行已授权工作。
 */
public interface BackgroundActorExecutor {

  /**
   * 重新加载用户的当前账号状态与权限，并仅在本次调用期间绑定其安全上下文。
   */
  <T> T execute(String userId, Supplier<T> action);
}
