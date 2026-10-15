package com.ezaigc.hr.talent.application.manager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.enums.ResumeLifecycleRunStatus;
import com.ezaigc.hr.talent.enums.ResumeLifecyclePhase;
import com.ezaigc.hr.talent.enums.ResumeLifecycleAdvanceResult;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleTiming;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRun;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRunPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
/** 调度不包裹批量事务；租约协调扫描，每条业务更新独立且幂等。 */
@Service
public class ResumeLifecycleJobManager {
  private static final Logger LOG = LoggerFactory.getLogger(ResumeLifecycleJobManager.class);
  @Resource private ResumeLifecyclePersistenceService cycles;
  @Resource private ResumeLifecycleRuntimePersistenceService runtime;
  @Resource private ResumeLifecycleRunPersistenceService runs;
  @Resource private ResumeLifecycleApplicationManager manager;
  @Resource private ResumeLifecycleProperties properties;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  public void execute() {
    if (!properties.isEnabled()) return;
    var now=clock.instant();
    runtime.ensure(now);
    runtime.activate(now);
    String lease=ids.newId();
    if (runtime.claim(lease,now,now.plusSeconds(300))!=1) return;
    ResumeLifecycleRun run=null;
    try {
      var state=runtime.getById("RESUME_LIFECYCLE");
      LocalDate latest=ResumeLifecycleTiming.settlementDate(now);
      run=runs.getOne(Wrappers.<ResumeLifecycleRun>lambdaQuery().eq(ResumeLifecycleRun::getStatus,ResumeLifecycleRunStatus.RETRY_PENDING.getCode())
          .le(ResumeLifecycleRun::getSettlementDate,latest).orderByAsc(ResumeLifecycleRun::getSettlementDate).last("LIMIT 1"));
      boolean requestedRetry=run!=null;
      LocalDate date=requestedRetry ? run.getSettlementDate() : latest;
      // 重试不能借用新日历提前升级。已结算边界或跨日历代次的重试只纠正投影和降级，
      // 升级留给下一次正常 01:00 边界；原代次未完成的普通失败仍可按原规则补跑。
      boolean correctionOnly=date.equals(state.getLastSettlementDate())
          || (requestedRetry && run.getCalendarGeneration()!=state.getCalendarGeneration());
      if(!requestedRetry && correctionOnly && state.getCompletedGeneration()==state.getCalendarGeneration()) return;
      if (!requestedRetry) run=runs.getOne(Wrappers.<ResumeLifecycleRun>lambdaQuery().eq(ResumeLifecycleRun::getSettlementDate,date)
          .eq(ResumeLifecycleRun::getCalendarGeneration,state.getCalendarGeneration()));
      if(run==null) {
        run=new ResumeLifecycleRun(); run.setId(ids.newId()); run.setSettlementDate(date);
        run.setCalendarGeneration(state.getCalendarGeneration()); run.setCreateTime(now);
        run.setUpdateTime(now); run.setStatus(ResumeLifecycleRunStatus.RUNNING.getCode()); runs.save(run);
      }
      runs.update(Wrappers.<ResumeLifecycleRun>lambdaUpdate().eq(ResumeLifecycleRun::getId,run.getId())
          .set(ResumeLifecycleRun::getFinishedAt,null).set(ResumeLifecycleRun::getErrorCode,null));
      run.setFinishedAt(null);run.setErrorCode(null);
      run.setStatus(ResumeLifecycleRunStatus.RUNNING.getCode());run.setStartedAt(now);run.setScannedCount(0);run.setSucceededCount(0);
      run.setSharedCount(0);run.setBlockedCount(0);run.setFailedCount(0);run.setUpdateTime(now);
      runs.updateById(run);
      // 存量起点固定为首次启用时间，新入库使用实际就绪时间，不追溯上线前超时。
      String after="";
      while(true) {
        var ready=cycles.ready(after,properties.getBatchSize());
        if(ready.isEmpty()) break;
        for(var value:ready) {
          renew(lease);
          try {
            manager.initialize(value.getCandidateId(),value.getResumeVersionId(),
                value.getEnteredAt().isAfter(state.getActivatedAt()) ? value.getEnteredAt() : state.getActivatedAt());
          } catch(RuntimeException error) {
            run.setFailedCount(run.getFailedCount()+1);
            LOG.warn("resume-lifecycle-initialize-failed runId={} code={}",run.getId(),error.getClass().getSimpleName());
          }
        }
        after=ready.getLast().getResumeVersionId();
      }
      after="";
      while(true) {
        var batch=cycles.page(new Page<ResumeLifecycle>(1,properties.getBatchSize(),false),Wrappers.<ResumeLifecycle>lambdaQuery().eq(ResumeLifecycle::getActiveMarker,"Y")
            .in(ResumeLifecycle::getPhase,List.of(ResumeLifecyclePhase.PENDING_SCREENING.getCode(),ResumeLifecyclePhase.PENDING_DISPOSAL.getCode()))
            .gt(ResumeLifecycle::getId,after).orderByAsc(ResumeLifecycle::getId)).getRecords();
        if(batch.isEmpty()) break;
        for(var row:batch) {
          renew(lease);
          run.setScannedCount(run.getScannedCount()+1);
          try {
            String result=manager.advance(row.getId(),date,correctionOnly);
            if(ResumeLifecycleAdvanceResult.BLOCKED.matches(result)) run.setBlockedCount(run.getBlockedCount()+1);
            else {run.setSucceededCount(run.getSucceededCount()+1);
              if(ResumeLifecycleAdvanceResult.SHARED.matches(result)) run.setSharedCount(run.getSharedCount()+1);}
          } catch(RuntimeException error) {
            run.setFailedCount(run.getFailedCount()+1);
            LOG.warn("resume-lifecycle-advance-failed runId={} cycleId={} code={}",run.getId(),row.getId(),error.getClass().getSimpleName());
          }
        }
        after=batch.getLast().getId();
      }
      renew(lease);
      run.setStatus(run.getFailedCount()>0 || (run.getBlockedCount()>0 && run.getSucceededCount()>0) ? ResumeLifecycleRunStatus.PARTIAL.getCode() : run.getBlockedCount()>0 ? ResumeLifecycleRunStatus.BLOCKED.getCode() : ResumeLifecycleRunStatus.SUCCEEDED.getCode());
      run.setErrorCode(run.getFailedCount()>0 ? "ITEM_FAILED" : run.getBlockedCount()>0 ? "CALENDAR_MISSING" : null);
      run.setFinishedAt(clock.instant());run.setUpdateTime(clock.instant());runs.updateById(run);
      // 缺失日历有明确版本变更后再恢复；部分失败保留边界供补跑。
      if(run.getFailedCount()==0 && date.equals(latest)) runtime.complete(lease,state.getCalendarGeneration(),date,clock.instant());
    } catch (RuntimeException error) {
      // 仅仍持租约者写终态，防止过期线程覆盖接手实例的运行结果。
      var leaseState=runtime.getById("RESUME_LIFECYCLE");
      if(run!=null && leaseState!=null && lease.equals(leaseState.getLeaseOwner()) && leaseState.getLeaseUntil()!=null
          && leaseState.getLeaseUntil().isAfter(clock.instant())) {
        run.setStatus(ResumeLifecycleRunStatus.PARTIAL.getCode());run.setErrorCode("RUN_INTERRUPTED");run.setFinishedAt(clock.instant());
        run.setFailedCount(run.getFailedCount()+1);runs.updateById(run);
      }
      LOG.warn("resume-lifecycle-run-interrupted code={}",error.getClass().getSimpleName());
    } finally { runtime.release(lease,clock.instant()); }
  }
  private void renew(String lease) {
    var now=clock.instant();
    if(runtime.claim(lease,now,now.plusSeconds(300))!=1)
      throw new IllegalStateException("RESUME_LIFECYCLE_LEASE_LOST");
  }
}
