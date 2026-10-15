package com.ezaigc.hr.common.infrastructure.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/** HTTP 与持久任务共用的安全关联 ID；不接受任意请求内容进入日志上下文。 */
public final class TraceIds {
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{16,64}");

  private TraceIds() {}

  public static boolean isSafe(String value) {
    return value != null && SAFE.matcher(value).matches();
  }

  public static String acceptOrCreate(String value) {
    return isSafe(value) ? value : UUID.randomUUID().toString().replace("-", "");
  }
}
