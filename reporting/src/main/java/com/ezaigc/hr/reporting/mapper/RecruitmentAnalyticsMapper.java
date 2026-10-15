package com.ezaigc.hr.reporting.mapper;

import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsRowDTO;
import com.ezaigc.hr.reporting.enums.MetricSourceRecordStatus.Codes;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 由指标 Mapper 继承的只读查询；聚合前统一裁剪授权范围。 */
@Mapper
public interface RecruitmentAnalyticsMapper {
  String RESUME_FILTERS = """
      WHERE i.status='""" + ResumeIntakeStatus.Codes.COMPLETED + "' AND i.candidate_id IS NOT NULL\n" + """
        AND i.create_time &gt;= FROM_UNIXTIME(#{q.fromEpoch}) AND i.create_time &lt; FROM_UNIXTIME(#{q.toEpoch})
        AND i.create_time &lt;= FROM_UNIXTIME(#{q.asOfEpoch})
        <if test="!q.allScope">
          AND (i.owner_user_id=#{q.actorId}
          <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
            OR i.owner_user_id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
          </if>)
        </if>
        <if test="q.departmentId != null">AND j.owner_department_id=#{q.departmentId}</if>
        <if test="q.jdId != null">AND v.jd_id=#{q.jdId}</if>
        <if test="q.recruitmentType != null">AND j.recruitment_type=#{q.recruitmentType}</if>
      """;
  String RESUME_COHORT = """
      , resume_cohort AS (
        SELECT i.candidate_id,i.resume_version_id,i.source_channel_id,b.jd_version_id,
          ROW_NUMBER() OVER (PARTITION BY i.candidate_id ORDER BY i.create_time DESC,i.id DESC) AS resume_rank
        FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id
        LEFT JOIN demand_jd_version v ON v.id=b.jd_version_id LEFT JOIN demand_jd j ON j.id=v.jd_id
      """ + RESUME_FILTERS + """
      )
      """;

  @SpecialSql("已完成简历收件按候选人去重，在聚合前统一应用时间、归属和岗位筛选")
  @Select("<script>" + """
      SELECT COUNT(DISTINCT i.candidate_id)
      FROM talent_resume_intake i
      JOIN talent_intake_batch b ON b.id=i.batch_id
      LEFT JOIN demand_jd_version v ON v.id=b.jd_version_id
      LEFT JOIN demand_jd j ON j.id=v.jd_id
      """ + RESUME_FILTERS + "</script>")
  long analyticsResumeCount(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("当前有效公司黑名单按候选人当前归属裁剪，不受历史流程或岗位筛选影响")
  @Select("""
      <script>
      SELECT COUNT(*)
      FROM risk_company_blacklist_current b
      JOIN talent_candidate c ON c.id=b.candidate_id
      WHERE b.active=1 AND c.status='""" + CandidateStatus.Codes.ACTIVE + "'\n" + """
      <if test="!q.allScope">
        AND (c.owner_user_id=#{q.actorId}
        <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
          OR c.owner_user_id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
        </if>)
      </if>
      <if test="q.departmentId != null">AND c.owner_department_id=#{q.departmentId}</if>
      </script>
      """)
  long analyticsActiveBlacklistCount(@Param("q") RecruitmentAnalyticsQuery query);

  String OWNER_SCOPE = """
      <if test="!q.allScope">
        AND (a.owner_user_id=#{q.actorId}
        <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
          OR a.owner_user_id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
        </if>)
      </if>
      """;
  String FILTERS = """
      <if test="q.departmentId != null">AND j.owner_department_id=#{q.departmentId}</if>
      <if test="q.jdId != null">AND a.jd_id=#{q.jdId}</if>
      <if test="q.recruitmentType != null">AND j.recruitment_type=#{q.recruitmentType}</if>
      """;
  String COHORT = """
      WITH cohort AS (
        SELECT a.id,a.jd_id,a.jd_version_id,a.resume_version_id,a.owner_user_id,j.owner_department_id,UNIX_TIMESTAMP(a.create_time) AS created_epoch,a.terminal_at,a.outcome,a.current_stage
        FROM recruitment_application a JOIN demand_jd j ON j.id=a.jd_id
        WHERE a.create_time &gt;= FROM_UNIXTIME(#{q.fromEpoch}) AND a.create_time &lt; FROM_UNIXTIME(#{q.toEpoch})
          AND a.create_time &lt;= FROM_UNIXTIME(#{q.asOfEpoch})
      """ + OWNER_SCOPE + FILTERS + """
      ), facts AS (
        SELECT a.id,a.jd_id,a.jd_version_id,a.resume_version_id,a.owner_user_id,a.owner_department_id,a.created_epoch,a.terminal_at,a.outcome,a.current_stage,
          CASE WHEN EXISTS (SELECT 1 FROM recruitment_onboarding_process p WHERE p.application_id=a.id
            AND p.started_at &lt;= #{q.asOfShanghai}) THEN 1 ELSE 0 END AS offer_count,
          CASE WHEN EXISTS (SELECT 1 FROM recruitment_onboarding_event b WHERE b.application_id=a.id AND b.onboarded_at &lt;= #{q.asOfShanghai}) THEN 1 ELSE 0 END AS onboard_count,
          CASE WHEN EXISTS (SELECT 1 FROM interview_interview i WHERE i.application_id=a.id AND i.round_no=1 AND i.status='""" + Codes.COMPLETED + "') THEN 1 ELSE 0 END AS interview1,\n" + """
          CASE WHEN EXISTS (SELECT 1 FROM interview_interview i WHERE i.application_id=a.id AND i.round_no=2 AND i.status='""" + Codes.COMPLETED + "') THEN 1 ELSE 0 END AS interview2,\n" + """
          CASE WHEN EXISTS (SELECT 1 FROM interview_interview i WHERE i.application_id=a.id AND i.round_no &gt;= 3 AND i.status='""" + Codes.COMPLETED + "') THEN 1 ELSE 0 END AS interview3\n" + """
        FROM cohort a
      )
      """;

  @SpecialSql("流程创建队列按 Application 去重计算有效里程碑与自然耗时，不使用面评条数")
  @Select("<script>" + COHORT + """
      SELECT COUNT(*) AS applications,COALESCE(SUM(offer_count),0) AS offers,COALESCE(SUM(onboard_count),0) AS onboarded,
        COALESCE(SUM(interview1),0) AS interview1,COALESCE(SUM(interview2),0) AS interview2,COALESCE(SUM(interview3),0) AS interview3,
        COALESCE(SUM(CASE WHEN terminal_at IS NOT NULL THEN 1 ELSE 0 END),0) AS completed_count,
        AVG(CASE WHEN terminal_at IS NOT NULL THEN
          (TIMESTAMPDIFF(MICROSECOND,'1970-01-01 08:00:00',terminal_at)/1000000.0-created_epoch)/3600.0 END) AS average_total_hours,
        COALESCE(SUM(CASE WHEN terminal_at IS NULL THEN 1 ELSE 0 END),0) AS active_count,
        AVG(CASE WHEN terminal_at IS NULL THEN (#{q.asOfEpoch}-created_epoch)/3600.0 END) AS average_active_hours
      FROM facts
      </script>
      """)
  RecruitmentAnalyticsRowDTO analyticsSummary(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("按上海时区的流程创建日分组队列，避免混用跨期事件数为转化率")
  @Select("<script>" + COHORT + """
      SELECT CAST(DATE(TIMESTAMPADD(SECOND,FLOOR(created_epoch),'1970-01-01 08:00:00')) AS CHAR) AS date,COUNT(*) AS applications,
        COALESCE(SUM(offer_count),0) AS offers,COALESCE(SUM(onboard_count),0) AS onboarded
      FROM facts GROUP BY CAST(DATE(TIMESTAMPADD(SECOND,FLOOR(created_epoch),'1970-01-01 08:00:00')) AS CHAR) ORDER BY date
      </script>
      """)
  List<RecruitmentAnalyticsRowDTO> analyticsTrend(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("收件渠道按授权候选人去重，流程转化仍按授权流程；缺失渠道保留未知分组")
  @Select("<script>" + COHORT + RESUME_COHORT + """
      , source_rows AS (
        SELECT COALESCE(c.id,'UNKNOWN') AS id,COALESCE(c.name,'未知渠道') AS name,
          COUNT(*) AS resumes,0 AS applications,0 AS offers,0 AS onboarded
        FROM resume_cohort i LEFT JOIN talent_source_channel c ON c.id=i.source_channel_id
        WHERE i.resume_rank=1 GROUP BY c.id,c.name
        UNION ALL
        SELECT COALESCE(c.id,'UNKNOWN') AS id,COALESCE(c.name,'未知渠道') AS name,
          0 AS resumes,COUNT(*) AS applications,COALESCE(SUM(a.offer_count),0) AS offers,
          COALESCE(SUM(a.onboard_count),0) AS onboarded
        FROM facts a LEFT JOIN talent_resume_version v ON v.id=a.resume_version_id
        LEFT JOIN talent_resume_intake t ON t.id=v.intake_id LEFT JOIN talent_source_channel c ON c.id=t.source_channel_id
        GROUP BY c.id,c.name
      )
      SELECT id,name,SUM(resumes) AS resumes,SUM(applications) AS applications,
        SUM(offers) AS offers,SUM(onboarded) AS onboarded
      FROM source_rows GROUP BY id,name ORDER BY resumes DESC,id
      </script>
      """)
  List<RecruitmentAnalyticsRowDTO> analyticsChannels(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("授权名册补零，与授权收件和流程聚合关联；停用账号仅保留匹配事实，面试按流程去重")
  @Select("<script>" + COHORT + """
      , workload AS (
        SELECT a.owner_user_id,COUNT(*) AS applications,
          SUM(CASE WHEN EXISTS (SELECT 1 FROM interview_interview i WHERE i.application_id=a.id
            AND i.status='""" + Codes.COMPLETED + "') THEN 1 ELSE 0 END) AS interviews,\n" + """
          SUM(a.interview1) AS interview1,SUM(a.interview2) AS interview2,SUM(a.interview3) AS interview3,
          SUM(a.offer_count) AS offers,SUM(a.onboard_count) AS onboarded,
          SUM(CASE WHEN a.outcome='REJECT' THEN 1 ELSE 0 END) AS rejected
        FROM facts a GROUP BY a.owner_user_id
      )
      , resumes AS (
        SELECT i.owner_user_id,COUNT(DISTINCT i.candidate_id) AS resumes
        FROM talent_resume_intake i JOIN talent_intake_batch b ON b.id=i.batch_id
        LEFT JOIN demand_jd_version v ON v.id=b.jd_version_id LEFT JOIN demand_jd j ON j.id=v.jd_id
        WHERE i.status='""" + ResumeIntakeStatus.Codes.COMPLETED + "' AND i.candidate_id IS NOT NULL\n" + """
          AND i.create_time &gt;= FROM_UNIXTIME(#{q.fromEpoch}) AND i.create_time &lt; FROM_UNIXTIME(#{q.toEpoch})
          AND i.create_time &lt;= FROM_UNIXTIME(#{q.asOfEpoch})
          <if test="!q.allScope">
            AND (i.owner_user_id=#{q.actorId}
            <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
              OR i.owner_user_id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
            </if>)
          </if>
          <if test="q.departmentId != null">AND j.owner_department_id=#{q.departmentId}</if>
          <if test="q.jdId != null">AND v.jd_id=#{q.jdId}</if>
          <if test="q.recruitmentType != null">AND j.recruitment_type=#{q.recruitmentType}</if>
        GROUP BY i.owner_user_id
      ), roster AS (
        SELECT u.id FROM identity_user_account u WHERE u.status='""" + IdentityActivationStatus.Codes.ACTIVE + "'\n" + """
        <choose>
          <when test="q.allScope">
            AND (u.id=#{q.actorId} OR EXISTS (SELECT 1 FROM identity_user_role ur
              JOIN identity_role r ON r.id=ur.role_id AND r.status='""" + IdentityActivationStatus.Codes.ACTIVE + "'\n" + """
              WHERE ur.user_id=u.id AND r.code IN ('RECRUITER','RECRUITMENT_MANAGER','HR_DIRECTOR','SUPER_ADMIN')))
          </when>
          <otherwise>
            AND (u.id=#{q.actorId}
            <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
              OR u.id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
            </if>)
          </otherwise>
        </choose>
        UNION SELECT owner_user_id FROM workload
        UNION SELECT owner_user_id FROM resumes
      )
      SELECT roster.id,COALESCE(u.display_name,'账号名称不可用') AS name,
        CASE WHEN u.status='""" + IdentityActivationStatus.Codes.ACTIVE + "' THEN FALSE ELSE TRUE END AS inactive,\n" + """
        COALESCE(w.applications,0) AS applications,COALESCE(w.interviews,0) AS interviews,
        COALESCE(w.interview1,0) AS interview1,COALESCE(w.interview2,0) AS interview2,COALESCE(w.interview3,0) AS interview3,
        COALESCE(w.offers,0) AS offers,COALESCE(w.onboarded,0) AS onboarded,COALESCE(w.rejected,0) AS rejected,
        COALESCE(r.resumes,0) AS resumes
      FROM roster LEFT JOIN identity_user_account u ON u.id=roster.id
      LEFT JOIN workload w ON w.owner_user_id=roster.id LEFT JOIN resumes r ON r.owner_user_id=roster.id
      ORDER BY CASE WHEN roster.id=#{q.actorId} THEN 0 ELSE 1 END,name,roster.id
      </script>
      """)
  List<RecruitmentAnalyticsRowDTO> analyticsHrWorkload(@Param("q") RecruitmentAnalyticsQuery query);

  String JD_SCOPE = """
      <if test="!q.allScope">
        AND (j.owner_user_id=#{q.actorId}
        <if test="q.managedUserIds != null and !q.managedUserIds.isEmpty()">
          OR j.owner_user_id IN <foreach collection="q.managedUserIds" item="u" open="(" separator="," close=")">#{u}</foreach>
        </if>)
      </if>
      """;

  @SpecialSql("当前在招 JD 与已占用 HC 分别权限裁剪；未知编制不混入缺口，逐 JD 截断后求和")
  @Select("""
      <script>
      WITH jobs AS (SELECT j.id,j.headcount FROM demand_jd j WHERE j.deleted=FALSE AND j.status='""" + Codes.ACTIVE + "'\n" + JD_SCOPE + """
      <if test="q.departmentId != null">AND j.owner_department_id=#{q.departmentId}</if>
      <if test="q.jdId != null">AND j.id=#{q.jdId}</if>
      <if test="q.recruitmentType != null">AND j.recruitment_type=#{q.recruitmentType}</if>
      ), occupied AS (SELECT a.jd_id,COUNT(*) AS amount FROM recruitment_application a
        JOIN demand_jd j ON j.id=a.jd_id
        JOIN recruitment_offer_status_record o ON o.application_id=a.id
        WHERE o.status = '""" + Codes.ONBOARDED + "'\n" + """
        AND NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record n WHERE n.application_id=o.application_id
          AND (n.create_time &gt; o.create_time OR (n.create_time=o.create_time AND n.id &gt; o.id)))
      """ + OWNER_SCOPE + FILTERS + """
        GROUP BY a.jd_id)
      SELECT COUNT(*) AS active_jobs,COALESCE(SUM(j.headcount),0) AS headcount,
        COALESCE(SUM(CASE WHEN j.headcount IS NULL THEN 1 ELSE 0 END),0) AS unknown_headcount_jobs,
        COALESCE(SUM(COALESCE(o.amount,0)),0) AS occupied,
        COALESCE(SUM(CASE WHEN j.headcount IS NOT NULL THEN GREATEST(j.headcount-COALESCE(o.amount,0),0) ELSE 0 END),0) AS open_headcount
      FROM jobs j LEFT JOIN occupied o ON o.jd_id=j.id
      </script>
      """)
  RecruitmentAnalyticsRowDTO analyticsDemand(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("部门选项只来源于授权流程和 JD；不受当前下拉筛选影响，不枚举不可见组织")
  @Select("""
      <script>
      SELECT d.id,d.name FROM identity_department d JOIN (
        SELECT j.owner_department_id AS id FROM recruitment_application a JOIN demand_jd j ON j.id=a.jd_id WHERE 1=1
      """ + OWNER_SCOPE + """
        UNION SELECT j.owner_department_id AS id FROM demand_jd j WHERE j.deleted=FALSE
      """ + JD_SCOPE + """
      ) visible ON visible.id=d.id ORDER BY d.name,d.id
      </script>
      """)
  List<RecruitmentAnalyticsRowDTO> analyticsDepartments(@Param("q") RecruitmentAnalyticsQuery query);

  @SpecialSql("年龄学历增加全部授权收件候选人，流程结果及冻结 JD 工作区域仍按流程；未知独立且不输出个人信息")
  @Select("<script>" + COHORT + RESUME_COHORT + """
      , profile_source AS (
        SELECT a.id,COALESCE(a.outcome,'""" + Codes.PENDING + "') AS outcome,a.resume_version_id,a.jd_version_id FROM facts a\n" + """
        UNION ALL SELECT i.candidate_id,'RESUME',i.resume_version_id,i.jd_version_id FROM resume_cohort i WHERE i.resume_rank=1
      ), profile AS (
        SELECT a.id,a.outcome,
          CASE WHEN s.age IS NULL THEN '未知' WHEN s.age &lt; 25 THEN '25岁以下' WHEN s.age &lt; 30 THEN '25–29岁'
            WHEN s.age &lt; 35 THEN '30–34岁' WHEN s.age &lt; 40 THEN '35–39岁' ELSE '40岁及以上' END AS age_label,
          COALESCE((SELECT MAX(CASE WHEN e.degree_name LIKE '%博士%' OR LOWER(e.degree_name) LIKE '%phd%' THEN 6
            WHEN e.degree_name LIKE '%硕士%' OR LOWER(e.degree_name) LIKE '%master%' THEN 5
            WHEN e.degree_name LIKE '%本科%' OR LOWER(e.degree_name) LIKE '%bachelor%' THEN 4
            WHEN e.degree_name LIKE '%大专%' OR e.degree_name LIKE '%专科%' THEN 3
            WHEN e.degree_name LIKE '%高中%' OR e.degree_name LIKE '%中专%' THEN 2 ELSE 1 END)
            FROM talent_resume_education_fact e WHERE e.fact_snapshot_id=s.id AND NULLIF(TRIM(e.degree_name),'') IS NOT NULL),0) AS education_rank,
          COALESCE(r.name,'未知') AS location_label
        FROM profile_source a LEFT JOIN talent_resume_fact_snapshot s ON s.resume_version_id=a.resume_version_id AND s.current_marker='Y'
        LEFT JOIN demand_jd_version v ON v.id=a.jd_version_id
        LEFT JOIN demand_jd_code_option r ON r.kind='REGION' AND r.code=v.work_region_code
      ), labels AS (
        SELECT id,outcome,'AGE' AS dimension,age_label AS name FROM profile
        UNION ALL SELECT id,outcome,'EDUCATION',CASE education_rank WHEN 6 THEN '博士' WHEN 5 THEN '硕士' WHEN 4 THEN '本科'
          WHEN 3 THEN '专科' WHEN 2 THEN '高中/中专' WHEN 1 THEN '其他/未标准化' ELSE '未知' END FROM profile
        UNION ALL SELECT id,outcome,'INTENDED_LOCATION',location_label FROM profile
      )
      SELECT dimension,name,SUM(CASE WHEN outcome='RESUME' THEN 1 ELSE 0 END) AS resumes,
        SUM(CASE WHEN outcome='PASS' THEN 1 ELSE 0 END) AS passed,
        SUM(CASE WHEN outcome='REJECT' THEN 1 ELSE 0 END) AS rejected,
        SUM(CASE WHEN outcome='WITHDRAW' THEN 1 ELSE 0 END) AS withdrawn,
        SUM(CASE WHEN outcome='""" + Codes.PENDING + "' THEN 1 ELSE 0 END) AS pending\n" + """
      FROM labels GROUP BY dimension,name ORDER BY dimension,name
      </script>
      """)
  List<RecruitmentAnalyticsRowDTO> analyticsPortraits(@Param("q") RecruitmentAnalyticsQuery query);
}
