package com.ezaigc.hr.talent.application.manager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.talent.application.support.WorkCalendarLifecycleImpactSupport;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarImpactDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleTiming;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
/** 预览不锁候选人；发布仅标记新一轮重算，避免日历锁与路由锁反序。 */
@Service
public class ResumeLifecycleCalendarImpactManager implements WorkCalendarLifecycleImpactSupport {
  @Resource private ResumeLifecyclePersistenceService cycles;
  @Resource private ResumeLifecycleRuntimePersistenceService runtime;
  @Resource private WorkCalendarCalculationManager calendar;
  @Resource private ResumeLifecycleProperties properties;
  @Resource private Clock clock;
  public WorkCalendarImpactDTO preview(int year,String draftId) {
    if (!properties.isEnabled()) return new WorkCalendarImpactDTO(
        Sha256Utils.hashUtf8(year+":"+draftId+":disabled"),0,0,0,List.of());
    long upgrades=0,downgrades=0,shares=0;
    var missing=new TreeSet<Integer>();
    StringBuilder fingerprint=new StringBuilder(year+":"+draftId);
    String after="";
    while(true) {
      var rows=cycles.list(Wrappers.<ResumeLifecycle>lambdaQuery().eq(ResumeLifecycle::getActiveMarker,"Y")
          .in(ResumeLifecycle::getPhase,"PENDING_SCREENING","PENDING_DISPOSAL")
          .gt(ResumeLifecycle::getId,after).orderByAsc(ResumeLifecycle::getId).last("LIMIT 200"));
      if(rows.isEmpty()) break;
      for(var row:rows) {
        var result=calendar.calculateWithOverride(row.getEnteredAt().atZone(ResumeLifecycleTiming.ZONE).toLocalDate(),
            ResumeLifecycleTiming.settlementDate(clock.instant()),false,year,draftId);
        missing.addAll(result.getMissingYears());
        fingerprint.append(':').append(row.getId()).append(':').append(row.getVersion())
            .append(':').append(result.getRevisionVector());
        if(!result.isSettlementComplete()) continue;
        int target=Math.min(4,result.getElapsedWorkdays());
        int current=ResumeLifecycleApplicationManager.rank(row.getPhase(),row.getWarningLevel());
        if(target>current) upgrades++;
        if(target<current) downgrades++;
        if(target>=4) shares++;
      }
      after=rows.getLast().getId();
    }
    return new WorkCalendarImpactDTO(Sha256Utils.hashUtf8(fingerprint.toString()),upgrades,downgrades,shares,new ArrayList<>(missing));
  }
  public String published(int year,String revisionId,String previewFingerprint) {
    if (properties.isEnabled()) {
      runtime.ensure(null);
      runtime.calendarChanged();
    }
    return "calendar:"+year+":"+revisionId;
  }
}
