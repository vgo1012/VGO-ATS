package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

/** 简历入库与流程创建分析；耗时单位小时，画像为读取时最新事实。 */
@Value
public class RecruitmentAnalyticsResponse {
  Instant from;
  Instant to;
  Instant asOf;
  Demand demand;
  Risk risk;
  List<Milestone> funnel;
  List<Trend> trend;
  List<Channel> channels;
  List<Portrait> portraits;
  Efficiency efficiency;
  List<Department> departments;
  /** 招聘负责人、人力负责人及管理员可见；其他角色为 null，授权员工无记录时补零。 */
  List<HrWorkload> hrWorkload;
  List<String> notes;
  @Value public static class Demand { long activeJobs; long headcount; long unknownHeadcountJobs; long occupied; long openHeadcount; }
  /** 当前权限范围内的风险快照，不按历史统计区间回溯。 */
  @Value public static class Risk { long activeBlacklists; }
  @Value public static class Milestone { String key; String label; long count; }
  @Value public static class Trend { String date; long applications; long offers; long onboarded; }
  @Value public static class Channel { String id; String name; long resumes; long applications; long offers; long onboarded; }
  @Value public static class Portrait { String dimension; String label; long resumes; long passed; long rejected; long pending; long withdrawn; }
  @Value public static class Efficiency { long completedCount; Double averageTotalHours; long activeCount; Double averageActiveHours; }
  @Value public static class Department { String id; String name; }
  /** 简历按收件归属人去重；同期创建流程按当前负责人归属，分轮面试按流程去重。 */
  @Value public static class HrWorkload { String ownerUserId; String displayName; long applications; long interviews; long offers; long onboarded; long rejected; long resumes; long interview1; long interview2; long interview3Plus; boolean self; boolean inactive; }
}
