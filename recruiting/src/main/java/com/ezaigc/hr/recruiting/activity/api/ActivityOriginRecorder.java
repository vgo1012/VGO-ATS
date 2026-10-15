package com.ezaigc.hr.recruiting.activity.api;

/** 仅由已授权业务事务关联实际落库事实；请求头只描述入口，不提供业务权限。 */
public interface ActivityOriginRecorder {
  void record(String applicationId, String sourceType, String sourceId);
}
