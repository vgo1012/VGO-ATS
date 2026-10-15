package com.ezaigc.hr.reporting.application.manager;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Channel;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Demand;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Department;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Efficiency;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.HrWorkload;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Milestone;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Portrait;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Risk;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Trend;
import com.ezaigc.hr.reporting.service.MetricService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 独立招聘分析，直接读取授权事实，不依赖异步指标采集。 */
@Component
public class RecruitmentAnalyticsManager {
  @Resource private AuthorizationService authorization;
  @Resource private MetricService service;
  @Resource private Clock clock;

  @Transactional(readOnly = true)
  public RecruitmentAnalyticsResponse report(Instant from, Instant to, String departmentId, String jdId, String recruitmentType) {
    authorization.requirePermission("reporting.read-own");
    boolean cumulative = from == null && to == null;
    if (!cumulative && (from == null || to == null || !from.isBefore(to) || Duration.between(from,to).compareTo(Duration.ofDays(366)) > 0)) {
      throw new PlatformException(HttpStatus.BAD_REQUEST,"REPORT_TIME_RANGE_INVALID","请选择开始早于结束且不超过366天的统计区间",true);
    }
    String type = CommonUtils.stripToNull(recruitmentType);
    if (type != null && !List.of("INTERNAL","EXTERNAL").contains(type)) {
      throw new PlatformException(HttpStatus.BAD_REQUEST,"REPORT_RECRUITMENT_TYPE_INVALID","招聘类型只能为 INTERNAL 或 EXTERNAL",true);
    }
    var actor=authorization.currentActor();
    Instant asOf=clock.instant();
    if (cumulative) {
      from=Instant.EPOCH;
      // MySQL TIMESTAMP 最多微秒精度；独占上界向后一微秒，另由 asOf 排除未来事实。
      to=asOf.plus(1,ChronoUnit.MICROS);
    }
    var managedUserIds=!actor.hasCompanyWideScope() && actor.getRoles().contains("RECRUITMENT_MANAGER")
        ? List.copyOf(authorization.recruitmentReportManagedUserIds()) : List.<String>of();
    var query=new RecruitmentAnalyticsQuery(from,to,asOf,CommonUtils.stripToNull(departmentId),CommonUtils.stripToNull(jdId),type,
        actor.hasCompanyWideScope(),actor.getUserId(),managedUserIds);
    long resumes=service.analyticsResumeCount(query);
    var summary=service.analyticsSummary(query);
    var demand=service.analyticsDemand(query);
    return new RecruitmentAnalyticsResponse(from,to,query.getAsOf(),
        new Demand(demand.getActiveJobs(),demand.getHeadcount(),demand.getUnknownHeadcountJobs(),demand.getOccupied(),demand.getOpenHeadcount()),
        new Risk(service.analyticsActiveBlacklistCount(query)),
        List.of(new Milestone("RESUME","全部简历",resumes),new Milestone("APPLICATION","进入流程",summary.getApplications()),
            new Milestone("INTERVIEW_1","一面已完成",summary.getInterview1()),new Milestone("INTERVIEW_2","二面已完成",summary.getInterview2()),
            new Milestone("INTERVIEW_3_PLUS","三面及以上已完成",summary.getInterview3()),new Milestone("OFFER","进入入职流程",summary.getOffers()),
            new Milestone("ONBOARDED","已入职",summary.getOnboarded())),
        service.analyticsTrend(query).stream().map(r->new Trend(r.getDate(),r.getApplications(),r.getOffers(),r.getOnboarded())).toList(),
        service.analyticsChannels(query).stream().map(r->new Channel(r.getId(),r.getName(),r.getResumes(),r.getApplications(),r.getOffers(),r.getOnboarded())).toList(),
        service.analyticsPortraits(query).stream().map(r->new Portrait(r.getDimension(),r.getName(),r.getResumes(),r.getPassed(),r.getRejected(),r.getPending(),r.getWithdrawn())).toList(),
        new Efficiency(summary.getCompletedCount(),summary.getAverageTotalHours(),summary.getActiveCount(),summary.getAverageActiveHours()),
        service.analyticsDepartments(query).stream().map(r->new Department(r.getId(),r.getName())).toList(),
        (actor.isSuperAdmin() || actor.getRoles().stream().anyMatch(List.of("RECRUITMENT_MANAGER","HR_DIRECTOR","SUPER_ADMIN")::contains))
            ? service.analyticsHrWorkload(query).stream().map(r->new HrWorkload(r.getId(),r.getName(),r.getApplications(),
                r.getInterviews(),r.getOffers(),r.getOnboarded(),r.getRejected(),r.getResumes(),r.getInterview1(),r.getInterview2(),
                r.getInterview3(),actor.getUserId().equals(r.getId()),r.isInactive())).toList() : null,
        List.of((cumulative ? "累计模式包含截至本次统计时点的全部历史收件和流程，部门、岗位、招聘类型及人员权限仍生效。" : "期间模式仅包含所选时间区间内的收件和流程。")
                + "全部简历、渠道简历量及画像全部人数按区间内已完成收件的候选人去重，同一候选人取区间内最新收件；进入流程及渠道流程量以区间内创建的应聘流程为队列，每个流程计一次。里程碑追踪至当前读取时间。日期按上海时区，结束时间不含在内。",
            "趋势按创建流程的日期分组；各日期 Offer 和入职表示该日队列截至当前的结果，不是当日事件量。",
            "在招岗位与 HC 是当前快照，不按所选期间回溯。只有最新状态为已入职的可见流程占用 HC；缺口逐岗位计算，未知 HC 不计为零。",
            "当前有效黑名单按候选人当前负责人和部门权限统计，不按所选时间、岗位或内外部招聘类型回溯；标签生效期间候选人不会进入个人待筛选或共享简历库。",
            "部门按流程绑定 JD 的归属部门统计；权限仍独立限制本人及管理范围，部门筛选不扩权。岗位容量与占用均限可见数据，不代表公司完整库存。",
            "面试按已完成的面试记录和实际轮次去重；三面及以上合并。各里程碑百分比以同期创建的流程数为分母，不是相邻阶段通过率；入库简历按收件时间统计，与流程队列不同，不计算二者转化率。允许跳轮，里程碑数量可能不单调。",
            "Offer 以进入入职流程为准，同一招聘流程只计一次；进入普通 Offer 处理阶段但尚未确认录用的不计。",
            "总耗时为流程最初创建至最终终止的自然小时，重开不扣减；在途等待为创建至当前，均时无样本显示空值。",
            "年龄与学历的全部人数依据本期收件简历版本的当前明确事实，通过与不通过依据流程绑定版本及当前结果；学历取最高可识别学历。意向地点按流程绑定的 JD 版本统计，缺失归未知。",
            "尚无完整岗位级初筛起止事实，不展示虚构的“筛选通过”阶段，也不提供简历初筛通过率、处理时长或渠道有效简历率。渠道简历量与渠道流程量分别展示，口径不同。",
            "员工招聘统计仅向招聘负责人、人力负责人和管理员返回，沿用相同日期、岗位、部门、内外部筛选及数据权限；在用员工无数据时保留零值，停用员工仅保留筛选范围内有数据者；简历按收件负责人下的候选人去重，流程按当前负责人归属，交接会改变归属。面试数为至少完成一场面试的流程数，Offer 按已进入入职流程、入职按真实入职事实分别去重，不通过为当前 REJECT 决定。"));
  }
}
