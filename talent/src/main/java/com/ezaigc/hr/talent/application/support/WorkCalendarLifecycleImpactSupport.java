package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.talent.domain.dto.WorkCalendarImpactDTO;

/** 日历发布与周期重算的模块内协作；发布回调不得获取候选人或简历锁。 */
public interface WorkCalendarLifecycleImpactSupport {
  WorkCalendarImpactDTO preview(int year, String draftId);
  String published(int year, String revisionId, String previewFingerprint);
}
