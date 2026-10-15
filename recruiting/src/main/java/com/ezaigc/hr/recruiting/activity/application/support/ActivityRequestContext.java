package com.ezaigc.hr.recruiting.activity.application.support;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 老入口和后台任务没有按钮上下文，不新增入口元数据。 */
public final class ActivityRequestContext {
  private ActivityRequestContext() {}
  public static boolean isPresent() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return false;
    return attributes.getRequest().getHeader("X-Recruitment-Action") != null
        || attributes.getRequest().getHeader("X-Recruitment-Operation") != null;
  }
}
