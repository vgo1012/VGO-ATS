package com.ezaigc.hr.reporting.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class RecruitmentAnalyticsMapperTest {
  @Test void cumulativeQueriesKeepTheExclusiveMicrosecondUpperBoundAndCurrentSnapshotCutoff() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-10-01T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(Instant.EPOCH,now.plus(1,ChronoUnit.MICROS),now,
        null,null,null,false,"manager",List.of("active","disabled","indirect"));
    assertThat(query.getToEpoch()).isEqualByComparingTo(query.getAsOfEpoch().add(new BigDecimal("0.000001")));
    for (String method:List.of("analyticsResumeCount","analyticsSummary","analyticsChannels","analyticsPortraits","analyticsHrWorkload")) {
      var bound=configuration.getMappedStatement(MetricMapper.class.getName()+"."+method)
          .getBoundSql(Map.of("q",query));
      assertThat(bound.getSql().replaceAll("\\s+"," "))
          .contains("create_time < FROM_UNIXTIME(?)", "create_time <= FROM_UNIXTIME(?)", "owner_user_id=?", "owner_user_id IN");
      assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
          .contains("q.fromEpoch","q.toEpoch","q.asOfEpoch");
    }
  }

  @Test void trendGroupsByTheCompleteSelectedDateExpression() {
    var configuration = new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now = Instant.parse("2026-09-15T00:00:00Z");
    var query = new RecruitmentAnalyticsQuery(now.minusSeconds(3600), now, now,
        null, null, null, true, "actor", List.of());
    String sql = configuration.getMappedStatement(MetricMapper.class.getName() + ".analyticsTrend")
        .getBoundSql(Map.of("q", query)).getSql().replaceAll("\\s+", " ");
    // ONLY_FULL_GROUP_BY 不能依赖 MySQL 推导 CAST 与内层日期函数的依赖关系。
    String dateExpression = "CAST(DATE(TIMESTAMPADD(SECOND,FLOOR(created_epoch),'1970-01-01 08:00:00')) AS CHAR)";
    assertThat(sql).contains("SELECT " + dateExpression + " AS date")
        .contains("GROUP BY " + dateExpression + " ORDER BY date");
  }

  @Test void queryUsesAbsoluteEpochAndShanghaiDatetimeWithoutJvmTimezoneConversion() {
    var instant=Instant.parse("2026-09-01T15:59:59.999999Z");
    var query=new RecruitmentAnalyticsQuery(instant,instant.plusNanos(1000),instant,null,null,null,false,"actor",List.of());
    assertThat(query.getFromEpoch()).isEqualByComparingTo("1788278399.999999");
    assertThat(query.getToEpoch()).isEqualByComparingTo("1788278400");
    assertThat(query.getAsOfShanghai()).isEqualTo(LocalDateTime.of(2026,9,1,23,59,59,999999000));
  }

  @Test void onboardingFactsAndCompletedDurationUseShanghaiDatetimeClock() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,null,null,null,true,"actor",List.of());
    var bound=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsSummary")
        .getBoundSql(Map.of("q",query));

    assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
        .contains("q.asOfShanghai").doesNotContain("q.asOfUtc");
    assertThat(bound.getSql().replaceAll("\\s+"," "))
        .contains("TIMESTAMPDIFF(MICROSECOND,'1970-01-01 08:00:00',terminal_at)");
  }

  @Test void inheritedQueriesParseAndBindAllScopeFilters() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,"dept","jd","INTERNAL",false,"actor",List.of("managed"));
    for (String method:List.of("analyticsResumeCount","analyticsSummary","analyticsDemand","analyticsTrend","analyticsChannels","analyticsPortraits","analyticsDepartments","analyticsHrWorkload")) {
      var statement=configuration.getMappedStatement(MetricMapper.class.getName()+"."+method);
      var bound=statement.getBoundSql(Map.of("q",query));
      assertThat(bound.getSql()).contains("owner_user_id").doesNotContain("${");
      assertThat(bound.getParameterMappings()).anySatisfy(p->assertThat(p.getProperty()).isEqualTo("q.actorId"));
      if (!method.equals("analyticsDepartments")) {
        assertThat(bound.getParameterMappings()).anySatisfy(p->assertThat(p.getProperty()).isEqualTo("q.departmentId"));
        assertThat(bound.getParameterMappings()).anySatisfy(p->assertThat(p.getProperty()).isEqualTo("q.jdId"));
      }
    }
  }

  @Test void resumeCountUsesCompletedIntakesAndTheSameOwnerJobFilters() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,"dept","jd","INTERNAL",false,"actor",List.of("managed"));
    var bound=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsResumeCount").getBoundSql(Map.of("q",query));
    var sql=bound.getSql().replaceAll("\\s+"," ");
    assertThat(sql).contains("COUNT(DISTINCT i.candidate_id)", "i.status='COMPLETED'", "i.owner_user_id=?", "i.owner_user_id IN")
        .contains("j.owner_department_id=?", "v.jd_id=?", "j.recruitment_type=?");
    assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
        .contains("q.fromEpoch","q.toEpoch","q.asOfEpoch","q.actorId","q.departmentId","q.jdId","q.recruitmentType");
  }

  @Test void activeBlacklistCountUsesCurrentProjectionAndCandidateOwnerScope() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,"dept","jd","INTERNAL",false,"actor",List.of("managed"));
    var bound=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsActiveBlacklistCount").getBoundSql(Map.of("q",query));
    var sql=bound.getSql().replaceAll("\\s+"," ");
    assertThat(sql).contains("risk_company_blacklist_current", "b.active=1", "c.status='ACTIVE'", "c.owner_user_id=?", "c.owner_user_id IN", "c.owner_department_id=?")
        .doesNotContain("recruitment_application", "q.jdId", "q.recruitmentType", "${");
    assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
        .containsExactly("q.actorId", "__frch_u_0", "q.departmentId");
  }

  @Test void workloadBindsEveryFilterBeforeGroupingAndDoesNotJoinRepeatedInterviewRows() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,"dept","jd","EXTERNAL",false,"actor",List.of("managed"));
    var bound=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsHrWorkload").getBoundSql(Map.of("q",query));
    var sql=bound.getSql().replaceAll("\\s+"," ");
    assertThat(sql).contains("a.owner_user_id=?", "a.owner_user_id IN", "j.owner_department_id=?", "a.jd_id=?", "j.recruitment_type=?")
        .contains("SUM(CASE WHEN EXISTS (SELECT 1 FROM interview_interview", "GROUP BY a.owner_user_id")
        .contains("SUM(a.offer_count)", "SUM(a.onboard_count)", "a.outcome='REJECT'")
        .contains("COUNT(DISTINCT i.candidate_id)", "i.owner_user_id=?", "i.owner_user_id IN",
            "UNION SELECT owner_user_id FROM workload", "UNION SELECT owner_user_id FROM resumes",
            "u.status='ACTIVE'", "COALESCE(w.applications,0)", "COALESCE(r.resumes,0)")
        .doesNotContain("JOIN interview_interview");
    assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
        .contains("q.fromEpoch","q.toEpoch","q.asOfEpoch","q.actorId","q.departmentId","q.jdId","q.recruitmentType");
  }

  @Test void channelsAndPortraitsIncludeAllVisibleCompletedIntakesWithoutApplications() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,"dept","jd","INTERNAL",false,"actor",List.of("managed"));
    for (String method:List.of("analyticsChannels","analyticsPortraits")) {
      var bound=configuration.getMappedStatement(MetricMapper.class.getName()+"."+method)
          .getBoundSql(Map.of("q",query));
      var sql=bound.getSql().replaceAll("\\s+"," ");
      assertThat(sql)
          .contains("FROM talent_resume_intake i", "i.status='COMPLETED'", "i.candidate_id IS NOT NULL")
          .contains("PARTITION BY i.candidate_id ORDER BY i.create_time DESC,i.id DESC")
          .contains("i.owner_user_id=?", "i.owner_user_id IN", "j.owner_department_id=?", "v.jd_id=?", "j.recruitment_type=?")
          .contains("AS resumes");
      assertThat(sql.substring(sql.indexOf("resume_cohort AS ("),sql.indexOf("WHERE i.status='COMPLETED'")))
          .doesNotContain("recruitment_application");
      if (method.equals("analyticsChannels")) {
        assertThat(sql).contains("FROM resume_cohort i LEFT JOIN talent_source_channel c", "WHERE i.resume_rank=1 GROUP BY c.id,c.name");
      } else {
        assertThat(sql).contains("SELECT i.candidate_id,i.resume_version_id,i.source_channel_id,b.jd_version_id,")
            .contains("UNION ALL SELECT i.candidate_id,'RESUME',i.resume_version_id,i.jd_version_id FROM resume_cohort i WHERE i.resume_rank=1")
            .contains("SUM(CASE WHEN outcome='RESUME' THEN 1 ELSE 0 END) AS resumes")
            .contains("'INTENDED_LOCATION',location_label FROM profile")
            .doesNotContain("'INTENDED_LOCATION',location_label FROM profile WHERE outcome <> 'RESUME'");
      }
      assertThat(bound.getParameterMappings()).extracting(p->p.getProperty())
          .contains("q.fromEpoch","q.toEpoch","q.asOfEpoch","q.actorId","q.departmentId","q.jdId","q.recruitmentType");
    }
  }

  @Test void offersUseOnboardingProcessFactsAndOnlyOnboardedApplicationsOccupyHeadcount() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,null,null,null,true,"actor",List.of());

    var summarySql=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsSummary")
        .getBoundSql(Map.of("q",query)).getSql().replaceAll("\\s+"," ");
    var demandSql=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsDemand")
        .getBoundSql(Map.of("q",query)).getSql().replaceAll("\\s+"," ");

    assertThat(summarySql)
        .contains("recruitment_onboarding_process p", "p.application_id=a.id", "p.started_at <= ?")
        .doesNotContain("o.status='ISSUED'");
    assertThat(demandSql)
        .contains("o.status = 'ONBOARDED'", "NOT EXISTS (SELECT 1 FROM recruitment_offer_status_record n")
        .doesNotContain("o.status IN ('ISSUED','ACCEPTED','ONBOARDED')");
  }

  @Test void companyRosterIncludesActiveRecruitingRolesAndFilteredHistoricalOwners() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,null,null,null,true,"actor",List.of());
    var sql=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsHrWorkload")
        .getBoundSql(Map.of("q",query)).getSql().replaceAll("\\s+"," ");
    assertThat(sql).contains("r.code IN ('RECRUITER','RECRUITMENT_MANAGER','HR_DIRECTOR','SUPER_ADMIN')",
        "ur.user_id=u.id", "UNION SELECT owner_user_id FROM workload", "UNION SELECT owner_user_id FROM resumes")
        .doesNotContain("a.owner_user_id=?", "i.owner_user_id=?");
  }

  @Test void portraitLocationsComeFromTheFrozenJdVersionRegion() {
    var configuration=new Configuration();
    configuration.addMapper(MetricMapper.class);
    var now=Instant.parse("2026-09-15T00:00:00Z");
    var query=new RecruitmentAnalyticsQuery(now.minusSeconds(3600),now,now,null,null,null,true,"actor",List.of());
    var bound=configuration.getMappedStatement(MetricMapper.class.getName()+".analyticsPortraits")
        .getBoundSql(Map.of("q",query));
    var sql=bound.getSql().replaceAll("\\s+"," ");

    assertThat(sql)
        .contains("b.jd_version_id", "i.jd_version_id", "a.jd_version_id", "demand_jd_version v", "v.work_region_code", "demand_jd_code_option r")
        .contains("r.kind='REGION'", "COALESCE(r.name,'未知') AS location_label")
        .doesNotContain("recruitment_intention", "intention_locations", "s.city");
  }
}
