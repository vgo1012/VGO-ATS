package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsRowDTO;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 仅使用显式测试连接的新建随机数据库，绝不修改连接原有业务表。 */
@EnabledIfEnvironmentVariable(named="HR_EXTERNAL_MYSQL_IT",matches="true")
class RecruitmentAnalyticsExternalMySqlIT {
  private static final Instant FROM=Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant TO=Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant NOW=Instant.parse("2026-09-15T00:00:00Z");

  @Test void scopeCorrectionsReopensDuplicatesUnknownsAndDailyCohortsAreConsistent() throws Exception {
    String source=required("HR_DB_URL");
    URI endpoint=URI.create(source.substring("jdbc:".length()));
    assertThat(endpoint.getScheme()).isEqualTo("mysql");
    assertThat(endpoint.getRawUserInfo()).isNull();
    String schema="hr_analytics_it_"+UUID.randomUUID().toString().replace("-","");
    String isolatedUrl="jdbc:mysql://"+endpoint.getRawAuthority()+"/"+schema
        +"?"+(endpoint.getRawQuery()==null?"":endpoint.getRawQuery()+"&")+"connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    try (var admin=DriverManager.getConnection(source,required("HR_DB_USER"),required("HR_DB_PASSWORD"));var ddl=admin.createStatement()) {
      ddl.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
      try (Connection connection=DriverManager.getConnection(isolatedUrl,required("HR_DB_USER"),required("HR_DB_PASSWORD"))) {
        assertThat(connection.getCatalog()).isEqualTo(schema);
        var dataSource=new SingleConnectionDataSource(connection,true);
        var jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("SET time_zone = '+00:00'");
        // 即使外部测试服务器默认关闭严格分组，也必须覆盖生产默认模式。
        jdbc.execute("SET SESSION sql_mode = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION'");
        seed(jdbc);
        var configuration=new MybatisConfiguration();
        configuration.setEnvironment(new Environment("analytics-test",new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MetricMapper.class);
        configuration.addMapper(IdentityAccountMapper.class);
        try (var session=new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
          var mapper=session.getMapper(MetricMapper.class);
          var identity=session.getMapper(IdentityAccountMapper.class);
          var historicalScope=identity.findReportingManagedUserIds("owner");
          assertThat(historicalScope).containsExactly("disabled-empty","managed","zero");
          assertThat(identity.findManagedUserIds("owner")).isEmpty();
          var q=query(false,"owner",historicalScope,null,null);
          var workload=mapper.analyticsHrWorkload(query(false,"owner",List.of("managed","zero","disabled-empty"),null,null));
          assertThat(workload).extracting(RecruitmentAnalyticsRowDTO::getId).containsExactly("owner","managed","zero");
          assertThat(workload.getFirst()).satisfies(row->{
            assertThat(row.getApplications()).isEqualTo(4);
            assertThat(row.getInterviews()).isEqualTo(1);
            assertThat(row.getInterview1()).isEqualTo(1);
            assertThat(row.getInterview2()).isZero();
            assertThat(row.getInterview3()).isEqualTo(1);
            assertThat(row.getOffers()).isEqualTo(2);
            assertThat(row.getOnboarded()).isZero();
            assertThat(row.getResumes()).isEqualTo(1);
            assertThat(row.isInactive()).isFalse();
          });
          assertThat(workload.get(1)).satisfies(row->{
            assertThat(row.isInactive()).isTrue();
            assertThat(row.getOffers()).isEqualTo(1);
            assertThat(row.getOnboarded()).isEqualTo(1);
          });
          assertThat(workload.get(2).getApplications()).isZero();
          assertThat(workload.get(2).getResumes()).isZero();
          var company=mapper.analyticsHrWorkload(query(true,"owner",List.of(),null,null));
          assertThat(company).extracting(RecruitmentAnalyticsRowDTO::getId)
              .containsExactlyInAnyOrder("owner","managed","zero","outsider","intake-only")
              .doesNotContain("disabled-empty","non-recruiting");
          assertThat(company).filteredOn(row->row.getId().equals("intake-only")).singleElement().satisfies(row->{
            assertThat(row.getApplications()).isZero();
            assertThat(row.getResumes()).isEqualTo(1);
          });
          var filteredWorkload=mapper.analyticsHrWorkload(query(false,"owner",List.of("managed","zero"),"d2","j3"));
          assertThat(filteredWorkload).filteredOn(row->row.getId().equals("owner")).singleElement().satisfies(row->{
            assertThat(row.getApplications()).isZero();
            assertThat(row.getResumes()).isZero();
          });
          assertThat(filteredWorkload).filteredOn(row->row.getId().equals("managed")).singleElement()
              .satisfies(row->assertThat(row.getApplications()).isEqualTo(1));
          var internal=new RecruitmentAnalyticsQuery(FROM,TO,NOW,null,null,"INTERNAL",false,"owner",List.of("managed","zero"));
          assertThat(mapper.analyticsHrWorkload(internal)).extracting(RecruitmentAnalyticsRowDTO::getId)
              .containsExactly("owner","zero");
          var summary=mapper.analyticsSummary(q);
          assertThat(summary.getApplications()).isEqualTo(5);
          assertThat(summary.getOffers()).isEqualTo(3);
          assertThat(summary.getOnboarded()).isEqualTo(1);
          assertThat(summary.getInterview1()).isEqualTo(1);
          assertThat(summary.getInterview3()).isEqualTo(1);
          assertThat(summary.getCompletedCount()).isEqualTo(2);
          assertThat(summary.getAverageTotalHours()).isEqualTo(24.0);
          assertThat(summary.getActiveCount()).isEqualTo(3);
          var demand=mapper.analyticsDemand(q);
          assertThat(demand.getActiveJobs()).isEqualTo(4);
          assertThat(demand.getHeadcount()).isEqualTo(8);
          assertThat(demand.getUnknownHeadcountJobs()).isEqualTo(1);
          assertThat(demand.getOccupied()).isEqualTo(1);
          assertThat(demand.getOpenHeadcount()).isEqualTo(7);
          var days=mapper.analyticsTrend(q);
          assertThat(days).extracting(RecruitmentAnalyticsRowDTO::getDate).containsExactly("2026-09-02");
          assertThat(days.getFirst().getApplications()).isEqualTo(5);
          assertThat(days.getFirst().getOffers()).isEqualTo(3);
          var channels=mapper.analyticsChannels(q);
          assertThat(channels).hasSize(2);
          assertThat(channels.stream().mapToLong(RecruitmentAnalyticsRowDTO::getApplications).sum()).isEqualTo(5);
          assertThat(channels.stream().filter(r->r.getId().equals("UNKNOWN")).findFirst()).hasValueSatisfying(r->assertThat(r.getApplications()).isEqualTo(4));
          var portraits=mapper.analyticsPortraits(q);
          assertThat(portraits).filteredOn(r->r.getDimension().equals("AGE")&&r.getName().equals("未知")).singleElement().satisfies(r->{
            assertThat(r.getPassed()).isEqualTo(2);
            assertThat(r.getRejected()).isEqualTo(1);
            assertThat(r.getPending()).isEqualTo(1);
          });
          assertThat(portraits).filteredOn(r->r.getDimension().equals("EDUCATION")&&r.getName().equals("硕士")).singleElement().satisfies(r->assertThat(r.getPassed()).isEqualTo(1));
          assertThat(portraits).filteredOn(r->r.getDimension().equals("INTENDED_LOCATION")&&r.getName().equals("北京")).singleElement().satisfies(r->assertThat(r.getPassed()).isEqualTo(2));
          assertThat(portraits).filteredOn(r->r.getDimension().equals("INTENDED_LOCATION")&&r.getName().equals("上海")).singleElement().satisfies(r->assertThat(r.getPending()).isEqualTo(1));
          var narrowed=query(false,"owner",List.of("managed"),"d2",null);
          assertThat(mapper.analyticsSummary(narrowed).getApplications()).isEqualTo(1);
          assertThat(mapper.analyticsChannels(narrowed)).singleElement();
          assertThat(mapper.analyticsDepartments(narrowed)).extracting(RecruitmentAnalyticsRowDTO::getId).containsExactly("d1","d2");
          assertThat(mapper.analyticsSummary(query(false,"owner",List.of(),null,"j2")).getOffers()).isZero();
          assertThat(mapper.analyticsSummary(query(true,"owner",List.of(),null,null)).getApplications()).isEqualTo(6);
          var forbidden=query(false,"nobody",List.of(),"d1",null);
          assertThat(mapper.analyticsSummary(forbidden).getApplications()).isZero();
          assertThat(mapper.analyticsSummary(forbidden).getAverageTotalHours()).isNull();
          assertThat(mapper.analyticsDemand(forbidden).getActiveJobs()).isZero();
          assertThat(mapper.analyticsTrend(forbidden)).isEmpty();
          assertThat(mapper.analyticsChannels(forbidden)).isEmpty();
          assertThat(mapper.analyticsPortraits(forbidden)).isEmpty();
          assertThat(mapper.analyticsDepartments(forbidden)).isEmpty();

          // 月初没有新增记录不代表历史统计为空；累计仍受当前管理链与同一统计时点约束。
          jdbc.update("INSERT INTO talent_resume_intake(id,source_channel_id,batch_id,candidate_id,owner_user_id,status,create_time)"
              + " VALUES ('snapshot',NULL,'batch','snapshot-candidate','managed','COMPLETED','2026-09-15 00:00:00.000000'),"
              + "('future',NULL,'batch','future-candidate','managed','COMPLETED','2026-09-15 00:00:00.000001')");
          session.clearCache();
          var cumulative=new RecruitmentAnalyticsQuery(Instant.EPOCH,NOW.plus(1,ChronoUnit.MICROS),NOW,
              null,null,null,false,"owner",historicalScope);
          assertThat(mapper.analyticsResumeCount(cumulative)).isEqualTo(3);
          assertThat(mapper.analyticsChannels(cumulative).stream().mapToLong(RecruitmentAnalyticsRowDTO::getResumes).sum())
              .isEqualTo(3);
          assertThat(mapper.analyticsPortraits(cumulative).stream().filter(row->row.getDimension().equals("AGE"))
              .mapToLong(RecruitmentAnalyticsRowDTO::getResumes).sum()).isEqualTo(3);
          assertThat(mapper.analyticsHrWorkload(cumulative).stream().filter(row->row.getId().equals("managed")))
              .singleElement().satisfies(row->{
                assertThat(row.isInactive()).isTrue();
                assertThat(row.getResumes()).isEqualTo(1);
              });
          var ownCumulative=new RecruitmentAnalyticsQuery(Instant.EPOCH,NOW.plus(1,ChronoUnit.MICROS),NOW,
              null,null,null,false,"owner",List.of());
          assertThat(mapper.analyticsResumeCount(ownCumulative)).isEqualTo(2);
          var companyCumulative=new RecruitmentAnalyticsQuery(Instant.EPOCH,NOW.plus(1,ChronoUnit.MICROS),NOW,
              null,null,null,true,"owner",List.of());
          assertThat(mapper.analyticsResumeCount(companyCumulative)).isEqualTo(4);
          var emptyMonth=new RecruitmentAnalyticsQuery(TO,Instant.parse("2026-11-01T00:00:00Z"),NOW,
              null,null,null,true,"owner",List.of());
          assertThat(mapper.analyticsResumeCount(emptyMonth)).isZero();

          // 上海午夜前一微秒必须落在前一天；使用真实 TIMESTAMP 类型重放不同会话时区。
          jdbc.update("INSERT INTO recruitment_application(id,jd_id,jd_version_id,resume_version_id,owner_department_id,owner_user_id,recruitment_type,create_time,terminal_at,outcome,current_stage) "
              + "VALUES ('boundary','j1','v1','missing','d1','owner','INTERNAL','2026-09-01 15:59:59.999999',NULL,NULL,'DECISION')");
          session.clearCache();
          var expectedSummary=mapper.analyticsSummary(q);
          var expectedTrend=mapper.analyticsTrend(q);
          var expectedChannels=mapper.analyticsChannels(q);
          var expectedPortraits=mapper.analyticsPortraits(q);
          var boundary=new RecruitmentAnalyticsQuery(Instant.parse("2026-09-01T15:59:59.999999Z"),
              Instant.parse("2026-09-01T16:00:00Z"),NOW,null,null,null,false,"owner",List.of("managed"));
          for (String timezone:List.of("+00:00","+08:00")) {
            jdbc.execute("SET time_zone = '"+timezone+"'");
            // 清掉 MyBatis 一级缓存，确保两个会话设置均真正执行 SQL。
            session.clearCache();
            assertThat(mapper.analyticsSummary(q)).isEqualTo(expectedSummary);
            assertThat(mapper.analyticsTrend(q)).isEqualTo(expectedTrend);
            assertThat(mapper.analyticsChannels(q)).isEqualTo(expectedChannels);
            assertThat(mapper.analyticsPortraits(q)).isEqualTo(expectedPortraits);
            assertThat(mapper.analyticsSummary(boundary).getApplications()).isEqualTo(1);
            assertThat(mapper.analyticsTrend(boundary)).singleElement().satisfies(r->{
              assertThat(r.getDate()).isEqualTo("2026-09-01");
              assertThat(r.getApplications()).isEqualTo(1);
            });
          }
        }
      } finally {
        // 只能删除本测试生成并验证过的随机命名数据库。
        assertThat(schema).matches("hr_analytics_it_[a-f0-9]{32}");
        ddl.execute("DROP DATABASE `"+schema+"`");
      }
    }
  }

  private static RecruitmentAnalyticsQuery query(boolean all,String actor,List<String> managed,String department,String jd) {
    return new RecruitmentAnalyticsQuery(FROM,TO,NOW,department,jd,null,all,actor,managed);
  }

  private static void seed(JdbcTemplate jdbc) {
    jdbc.execute("CREATE TABLE recruitment_application(id VARCHAR(26) PRIMARY KEY,jd_id VARCHAR(26),jd_version_id VARCHAR(26),resume_version_id VARCHAR(26),owner_department_id VARCHAR(26),owner_user_id VARCHAR(26),recruitment_type VARCHAR(16),create_time TIMESTAMP(6),terminal_at DATETIME(6),outcome VARCHAR(16),current_stage VARCHAR(32))");
    jdbc.execute("CREATE TABLE recruitment_offer_status_record(id VARCHAR(26) PRIMARY KEY,application_id VARCHAR(26),status VARCHAR(20),record_type VARCHAR(16),corrects_record_id VARCHAR(26),occurred_at DATETIME(6),create_time TIMESTAMP(6))");
    jdbc.execute("CREATE TABLE recruitment_onboarding_process(application_id VARCHAR(26) PRIMARY KEY,status VARCHAR(20),started_at DATETIME(6))");
    jdbc.execute("CREATE TABLE recruitment_onboarding_event(application_id VARCHAR(26),onboarded_at DATETIME)");
    jdbc.execute("CREATE TABLE interview_interview(application_id VARCHAR(26),round_no INT,status VARCHAR(20))");
    jdbc.execute("CREATE TABLE demand_jd(id VARCHAR(26),headcount INT,owner_department_id VARCHAR(26),owner_user_id VARCHAR(26),recruitment_type VARCHAR(16),work_region_code CHAR(3),deleted BOOLEAN,status VARCHAR(16))");
    jdbc.execute("CREATE TABLE demand_jd_version(id VARCHAR(26),jd_id VARCHAR(26),work_region_code CHAR(3))");
    jdbc.execute("CREATE TABLE demand_jd_code_option(id VARCHAR(26),kind VARCHAR(16),code CHAR(3),name VARCHAR(80))");
    jdbc.execute("CREATE TABLE identity_department(id VARCHAR(26),name VARCHAR(100))");
    jdbc.execute("CREATE TABLE talent_resume_version(id VARCHAR(26),intake_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE talent_resume_intake(id VARCHAR(26),source_channel_id VARCHAR(26),batch_id VARCHAR(26),candidate_id VARCHAR(26),owner_user_id VARCHAR(26),status VARCHAR(20),create_time TIMESTAMP(6),resume_version_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE talent_intake_batch(id VARCHAR(26),jd_version_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE identity_user_account(id VARCHAR(26),display_name VARCHAR(100),status VARCHAR(16),direct_leader_user_id VARCHAR(26))");
    jdbc.execute("CREATE TABLE identity_role(id VARCHAR(26),code VARCHAR(64),status VARCHAR(16))");
    jdbc.execute("CREATE TABLE identity_user_role(user_id VARCHAR(26),role_id VARCHAR(26))");
    jdbc.update("INSERT INTO identity_user_account(id,display_name,status) VALUES ('owner','Owner','ACTIVE'),('managed','A','DISABLED'),('zero','Z','ACTIVE'),('disabled-empty','Disabled','DISABLED'),('outsider','Outsider','ACTIVE'),('intake-only','Intake','ACTIVE'),('non-recruiting','Other','ACTIVE')");
    jdbc.update("UPDATE identity_user_account SET direct_leader_user_id='owner' WHERE id IN ('managed','disabled-empty')");
    jdbc.update("UPDATE identity_user_account SET direct_leader_user_id='managed' WHERE id='zero'");
    jdbc.update("INSERT INTO identity_role VALUES ('recruiter','RECRUITER','ACTIVE')");
    jdbc.update("INSERT INTO identity_user_role VALUES ('zero','recruiter'),('zero','recruiter'),('disabled-empty','recruiter')");
    jdbc.update("INSERT INTO talent_intake_batch VALUES ('batch','v1')");
    jdbc.execute("CREATE TABLE talent_source_channel(id VARCHAR(26),name VARCHAR(100))");
    jdbc.execute("CREATE TABLE talent_resume_fact_snapshot(id VARCHAR(26),resume_version_id VARCHAR(26),current_marker CHAR(1),age INT)");
    jdbc.execute("CREATE TABLE talent_resume_education_fact(fact_snapshot_id VARCHAR(26),degree_name VARCHAR(120))");
    jdbc.update("INSERT INTO identity_department VALUES ('d1','A'),('d2','B'),('hidden','Hidden')");
    jdbc.update("INSERT INTO demand_jd VALUES ('j1',1,'d1','owner','INTERNAL','001',FALSE,'ACTIVE'),('j2',5,'d1','owner','INTERNAL','002',FALSE,'ACTIVE'),('j3',2,'d2','managed','EXTERNAL','003',FALSE,'ACTIVE'),('j4',NULL,'d1','owner','INTERNAL','001',FALSE,'ACTIVE'),('hidden',10,'hidden','outsider','INTERNAL','001',FALSE,'ACTIVE')");
    jdbc.update("INSERT INTO demand_jd_code_option VALUES ('r1','REGION','001','北京'),('r2','REGION','002','上海'),('r3','REGION','003','杭州')");
    jdbc.update("INSERT INTO demand_jd_version VALUES ('v1','j1','001'),('v2','j2','002'),('v3','j3','003')");
    for (String id:List.of("a1","a2","a3","a4","a5","a7")) {
      String owner=id.equals("a4")?"managed":id.equals("a5")?"outsider":"owner";
      String jd=id.equals("a3")?"j2":id.equals("a4")?"j3":"j1";
      String outcome=id.equals("a2")?"REJECT":id.equals("a3")?null:"PASS";
      // 活动部门故意全部写 d1，a4 必须仍按绑定 JD 的 d2 归组。
      String version=jd.equals("j2")?"v2":jd.equals("j3")?"v3":"v1";
      jdbc.update("INSERT INTO recruitment_application VALUES (?,?,?,?,?,?,?,'2026-09-01 16:00:00',?,?,?)",id,jd,version,"resume-"+id,"d1",owner,id.equals("a4")?"EXTERNAL":"INTERNAL",
          id.equals("a2")||id.equals("a4")?"2026-09-03 00:00:00":null,outcome,id.equals("a4")?"ONBOARDED":id.equals("a2")?"TERMINATED":"OFFER");
    }
    jdbc.update("INSERT INTO recruitment_offer_status_record VALUES ('o1','a1','ISSUED','STATUS',NULL,'2026-09-02','2026-09-02'),('o1b','a1','ISSUED','CORRECTION','o1','2026-09-03','2026-09-03'),('o2','a2','ISSUED','STATUS',NULL,'2026-09-02','2026-09-02'),('o2c','a2','IN_PROGRESS','CORRECTION','o2','2026-09-03','2026-09-03'),('o3','a3','ISSUED','STATUS',NULL,'2026-09-02','2026-09-02'),('o3r','a3','NOT_STARTED','REOPEN',NULL,'2026-09-03','2026-09-03'),('o4','a4','ONBOARDED','STATUS',NULL,'2026-09-02','2026-09-02'),('o5','a5','ISSUED','STATUS',NULL,'2026-09-02','2026-09-02'),('o7','a7','ISSUED','STATUS',NULL,'2026-09-02','2026-09-02')");
    // NOW 是上海时间 08:00；前一秒必须计入，后一秒仍不能计入。
    jdbc.update("INSERT INTO recruitment_onboarding_event VALUES ('a4','2026-09-15 07:59:59'),('a1','2026-09-15 08:00:01')");
    jdbc.update("INSERT INTO recruitment_offer_status_record VALUES ('o4i','a4','ISSUED','STATUS',NULL,'2026-09-01 20:00:00','2026-09-01 20:00:00')");
    jdbc.update("INSERT INTO recruitment_onboarding_process VALUES ('a1','IN_PROGRESS','2026-09-15 07:59:59'),('a4','ONBOARDED','2026-09-02'),('a7','RETURNED','2026-09-04')");
    jdbc.update("INSERT INTO interview_interview VALUES ('a1',1,'COMPLETED'),('a1',1,'COMPLETED'),('a1',3,'COMPLETED'),('a1',4,'COMPLETED'),('a2',1,'CANCELLED')");
    jdbc.update("INSERT INTO talent_resume_version VALUES ('resume-a1','intake1')");
    jdbc.update("INSERT INTO talent_resume_intake(id,source_channel_id,batch_id,candidate_id,owner_user_id,status,create_time) VALUES ('intake1','channel1','batch','candidate1','owner','COMPLETED','2026-09-02'),('duplicate',NULL,'batch','candidate1','owner','COMPLETED','2026-09-03'),('only',NULL,'batch','candidate2','intake-only','COMPLETED','2026-09-02'),('failed',NULL,'batch','candidate3','owner','FAILED','2026-09-02'),('outside',NULL,'batch','candidate4','owner','COMPLETED','2026-08-01')");
    jdbc.update("INSERT INTO talent_source_channel VALUES ('channel1','Referral')");
    jdbc.update("INSERT INTO talent_resume_fact_snapshot VALUES ('s1','resume-a1','Y',24)");
    jdbc.update("INSERT INTO talent_resume_education_fact VALUES ('s1','本科'),('s1','硕士')");
  }

  private static String required(String name) {
    String value=System.getenv(name);
    assertThat(value).as("必须显式配置测试变量 %s",name).isNotBlank();
    return value;
  }
}
