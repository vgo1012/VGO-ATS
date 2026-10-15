package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeParsingManager;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeCompletionManager;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.talent.application.support.ResumeParseWorker;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelUnavailableException;
import org.slf4j.MDC;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchProjectionApplicationManager;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.constant.DemandConstants;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.constant.TalentConstants;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.ezaigc.hr.reporting.application.support.AuditCaptureWorker;
import com.ezaigc.hr.reporting.application.support.MetricCaptureWorker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(
    properties = {
      "spring.datasource.url=${HR_DB_URL}",
      "spring.datasource.username=${HR_DB_USER}",
      "spring.datasource.password=${HR_DB_PASSWORD}",
      "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
      "spring.datasource.hikari.maximum-pool-size=4",
      "spring.datasource.hikari.connection-timeout=5000",
      "hr.object-storage.mode=local",
      "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
      "hr.search.mode=memory",
      "hr.malware-scan.mode=development",
      "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
      "hr.bootstrap-admin.enabled=true",
      "hr.bootstrap-admin.department-code=ROOT",
      "hr.bootstrap-admin.department-name=总部",
      "hr.bootstrap-admin.display-name=测试管理员",
      "hr.bootstrap-admin.work-email=admin-it@example.com",
      "hr.bootstrap-admin.password=Test-Only-Admin-123!",
      "hr.resume-parsing.worker-enabled=false",
      "hr.resume-intake.worker-enabled=false",
      "hr.jd-parsing.worker-enabled=false",
      "hr.matching.worker-enabled=false",
      "hr.search.worker-enabled=false",
      // 仅诊断验收显式开启汇总；由测试触发，避免依赖后台轮询时序。
      "hr.reporting.audit-capture.enabled=true",
      "hr.reporting.audit-capture.initial-delay=1h",
      "hr.reporting.audit-capture.reconciliation-initial-delay=1h"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(PlatformExternalMySqlIT.AiTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class PlatformExternalMySqlIT {

  private final JdbcTemplate jdbcTemplate;
  private final Flyway flyway;
  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final AsyncTaskPort asyncTasks;
  private final AsyncTaskBatchRunner taskBatchRunner;
  private final ResumeParsingApplicationManager resumeParsing;
  private final ResumeIntakeParsingManager intakeParsing;
  private final ResumeIntakeCompletionManager intakeCompletion;
  private final ResumeIntakeProcessingPersistenceService intakeProcessing;
  private final BackgroundActorExecutor backgroundActors;
  private final JdParsingApplicationManager jdParsing;
  private final MatchRunApplicationManager matchRuns;
  private final SearchProjectionApplicationManager searchProjection;
  private final AuditCaptureWorker auditCaptureWorker;
  private final MetricCaptureWorker metricCaptureWorker;
  private final ControlledNotificationDelivery notificationDelivery;
  private final PlatformIdGenerator ids;
  @MockitoSpyBean private MatchRunPersistenceService matchRunPersistence;
  private final MatchRunAttemptPersistenceService matchAttemptPersistence;
  private final TransactionTemplate transactions;
  @MockitoSpyBean private AiMatchingEvaluationManager matchingEngine;
  @MockitoSpyBean private AsyncTaskService taskPersistence;
  @MockitoSpyBean private RawAiArchivePort rawArchive;
  @MockitoSpyBean private ResumeDocumentExtractionPort documentExtraction;
  @MockitoSpyBean private JdParseModelPort jdParseModel;
  @MockitoSpyBean private ResumeParseModelPort resumeParseModel;

  @Test
  void migratesIdentitySchemaAndCompletesARealAdminRegistrationFlow() throws Exception {
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    int appliedMigrationsBefore = flyway.info().applied().length;
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_user_account WHERE normalized_email ="
                    + " 'admin-it@example.com'",
                Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND"
                    + " table_name = 'SPRING_SESSION'",
                Integer.class))
        .isOne();

    MvcResult anonymousCsrf =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    JsonNode anonymousToken = body(anonymousCsrf);
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .cookie(anonymousCsrf.getResponse().getCookies())
                    .header(
                        anonymousToken.get("headerName").asString(),
                        anonymousToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"workEmail":"admin-it@example.com","password":"Test-Only-Admin-123!"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.superAdmin").value(true))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn();

    Cookie[] authenticatedSession = login.getResponse().getCookies();
    MvcResult authenticatedCsrf =
        mockMvc
            .perform(get("/api/v1/auth/csrf").cookie(authenticatedSession))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode token = body(authenticatedCsrf);
    mockMvc
        .perform(
            post("/api/v1/me/password")
                .cookie(authenticatedSession)
                .header(token.get("headerName").asString(), token.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Test-Only-Admin-123!","newPassword":"Changed-Admin-456"}
                    """))
        .andExpect(status().isOk());

    MvcResult secondCsrf =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    JsonNode secondToken = body(secondCsrf);
    MvcResult secondLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .cookie(secondCsrf.getResponse().getCookies())
                    .header(
                        secondToken.get("headerName").asString(), secondToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"workEmail":"admin-it@example.com","password":"Changed-Admin-456"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andReturn();
    String adminUserId =
        body(secondLogin)
            .get("userId")
            .asString();

    Cookie[] activeSession = secondLogin.getResponse().getCookies();
    MvcResult adminCsrf =
        mockMvc
            .perform(get("/api/v1/auth/csrf").cookie(activeSession))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode adminToken = body(adminCsrf);
    MvcResult department =
        mockMvc
            .perform(
                post("/api/v1/admin/departments")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"RECRUITING","name":"招聘部"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String departmentId = id(department);

    MvcResult supportDepartment =
        mockMvc
            .perform(
                post("/api/v1/admin/departments")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"PRODUCT","name":"产品部"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String supportDepartmentId = id(supportDepartment);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"displayName":"招聘专员","workEmail":"recruiter-it@example.com",
                         "homeDepartmentId":"%s","supportDepartmentIds":["%s"],
                         "roles":["RECRUITER"],"reason":"集成测试直接注册"}
                        """
                            .formatted(departmentId, supportDepartmentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andExpect(jsonPath("$.oneTimeCredential").isString())
            .andReturn();

    JsonNode createdUser = body(created);
    String userId = createdUser.get("userId").asString();
    String oneTimeCredential = createdUser.get("oneTimeCredential").asString();
    assertRandomCredential(oneTimeCredential);
    assertThat(oneTimeCredential.equals("recruiter-it@example.com")).isFalse();
    String storedHash =
        jdbcTemplate.queryForObject(
            "SELECT credential_hash FROM identity_user_account WHERE normalized_email ="
                + " 'recruiter-it@example.com'",
            String.class);
    assertThat(storedHash).startsWith("{bcrypt}");
    assertThat(storedHash.equals(oneTimeCredential)).isFalse();
    assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(oneTimeCredential, storedHash)).isTrue();
    assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("recruiter-it@example.com", storedHash)).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_membership WHERE membership_type = 'HOME'",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_membership WHERE user_id = ? AND membership_type ="
                    + " 'SUPPORT'",
                Integer.class,
                userId))
        .isOne();

    mockMvc
        .perform(
            patch("/api/v1/admin/users/{userId}", userId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"displayName":"招聘专员","workEmail":"recruiter-it@example.com",
                     "sourceTitle":"高级招聘专员","homeDepartmentId":"%s",
                     "supportDepartmentIds":[],"version":"0","reason":"调整支持范围"}
                    """
                        .formatted(departmentId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceTitle").value("高级招聘专员"));
    mockMvc
        .perform(
            get("/api/v1/admin/users/{userId}/organization-assignments", userId)
                .cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportDepartmentIds").isEmpty())
        .andExpect(jsonPath("$.roles[0]").value("RECRUITER"));

    mockMvc
        .perform(
            patch("/api/v1/admin/departments/{departmentId}", departmentId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"招聘部","assignmentQueueOwnerUserId":"%s",
                     "status":"ACTIVE","version":"0"}
                    """
                        .formatted(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignmentQueueOwnerUserId").value(userId));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_department_assignment_queue WHERE owner_user_id = ?",
                Integer.class,
                userId))
        .isOne();

    mockMvc
        .perform(
            post("/api/v1/admin/users/{userId}/disable", userId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":"1","reason":"验证队列门禁","retainHistoricalUnresolved":false}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSIGNMENT_QUEUE_REASSIGNMENT_REQUIRED"));
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity_audit_event", Integer.class))
        .isGreaterThanOrEqualTo(4);

    MvcResult client =
        mockMvc
            .perform(
                post("/api/v1/clients")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-client-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"CLIENT_IT","name":"集成测试客户"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    String clientId = id(client);

    mockMvc
        .perform(
            post("/api/v1/clients")
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .header("Idempotency-Key", "mysql-it-client-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"CLIENT_IT","name":"集成测试客户"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(clientId));

    MvcResult project =
        mockMvc
            .perform(
                post("/api/v1/clients/{clientId}/projects", clientId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-project-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"PROJECT_IT","name":"交付项目"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String projectId = id(project);

    MvcResult position =
        mockMvc
            .perform(
                post("/api/v1/client-positions")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-position-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId":"%s","projectId":"%s","code":"POSITION_IT","name":"售后工程师"}
                        """
                            .formatted(clientId, projectId)))
            .andExpect(status().isCreated())
            .andReturn();
    String positionId = id(position);

    MvcResult demand =
        mockMvc
            .perform(
                post("/api/v1/demands")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-demand-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"DEMAND_IT","title":"售后工程师招聘","clientId":"%s",
                         "projectId":"%s","positionId":"%s","recruitmentType":"EXTERNAL",
                         "headcount":2,"description":"真实 MySQL 纵向验收"}
                        """
                            .formatted(clientId, projectId, positionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();
    String demandId = id(demand);

    MvcResult jdVersion =
        mockMvc
            .perform(
                post("/api/v1/demands/{demandId}/jd-versions", demandId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-jd-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"售后工程师","rawText":"本科要求，工作地点客户现场，负责客户支持与故障定位","sourceType":"MANUAL","headcount":2,"recruitmentCycleDays":30}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.versionNo").value(1))
            .andReturn();
    String jdVersionId = id(jdVersion);

    MvcResult jdParse =
        mockMvc
            .perform(
                post("/api/v1/jd-versions/{id}/parse", jdVersionId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-jd-parse-1"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn();
    String jdTaskId =
        body(jdParse).get("taskId").asString();
    asyncTasks.claim(DemandConstants.JD_PARSE_TASK_TYPE, 5).forEach(jdParsing::process);
    mockMvc
        .perform(get("/api/v1/tasks/{id}", jdTaskId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    mockMvc
        .perform(get("/api/v1/jd-versions/{id}/facts", jdVersionId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.facts.professional.coreSkills[0].name").value("客户支持"))
        .andExpect(jsonPath("$.facts.controlledConditions.educations[0]").value("本科"))
        .andExpect(jsonPath("$.facts.fulfillmentConditions.locations[0]").value("客户现场"));

    MvcResult workflow =
        mockMvc
            .perform(
                post("/api/v1/jd-versions/{jdVersionId}/workflow-template-versions", jdVersionId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-workflow-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"recruitmentType":"EXTERNAL","steps":[
                          {"code":"CANDIDATE_COMMUNICATION"},
                          {"code":"PRE_SUBMISSION_INTERNAL_INTERVIEW"},
                          {"code":"CLIENT_SUBMISSION"},
                          {"code":"CLIENT_AI_INTERVIEW"},
                          {"code":"INTERVIEW_ROUND_1"},
                          {"code":"DECISION"},
                          {"code":"OFFER"}
                        ],"version":0}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.steps[0].defaultFollowUpChannel").value("PHONE"))
            .andReturn();
    String workflowId =
        body(workflow)
            .get("template")
            .get("id")
            .asString();

    mockMvc
        .perform(
            post("/api/v1/workflow-template-versions/{workflowId}/activate", workflowId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":0,"reason":"集成测试激活"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template.status").value("ACTIVE"));

    // 公司固定默认规则已由部署初始化；历史自定义方案仍验证创建/发布，但不再替换新活动的默认绑定。
    MvcResult activeDefaultScoring = mockMvc
        .perform(
            get("/api/v1/demands/{demandId}/active-scoring-config", demandId)
                .cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowTemplateActive").value(true))
        .andExpect(jsonPath("$.scoringConfigurationActive").value(true))
        .andExpect(jsonPath("$.readyForApplication").value(true)).andReturn();
    String activeScoringId = body(activeDefaultScoring).get("scoringConfiguration").get("version").get("id").asString();

    MvcResult scoring =
        mockMvc
            .perform(
                post("/api/v1/admin/jd-scoring-configs")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-scoring-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"jdVersionId":"%s","name":"正式方案 V1",
                         "professionalDimensions":{"skills":true,"roleExperience":true,
                           "relatedYears":true,"responsibilityMatch":true,"industryAndProjects":true},
                         "criteria":[
                           {"code":"AGE","level":"MUST","operator":"LTE",
                            "expectedValueJson":"{\\\"max\\\":45}","bonusPoints":0,
                            "reason":"客户明确年龄要求","evidencePolicy":"EXPLICIT_FACT_ONLY","selectableByHr":false},
                           {"code":"LANGUAGE","level":"PREFER","operator":"IN",
                            "expectedValueJson":"{\\\"values\\\":[\\\"EN\\\"]}","bonusPoints":10,
                            "reason":"英语可用于客户支持","evidencePolicy":"EXPLICIT_FACT_ONLY","selectableByHr":true}
                         ]}
                        """
                            .formatted(jdVersionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.criterionPlan.planMaxBonusPoints").value(10))
            .andReturn();
    String scoringId =
        body(scoring)
            .get("version")
            .get("id")
            .asString();

    mockMvc
        .perform(
            post("/api/v1/admin/jd-scoring-configs/{scoringId}/activate", scoringId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":0,"reason":"集成测试激活"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version.status").value("ACTIVE"));

    mockMvc
        .perform(
            get("/api/v1/demands/{demandId}/active-scoring-config", demandId)
                .cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyForApplication").value(true));

    MvcResult weightProfile =
        mockMvc
            .perform(
                post("/api/v1/weight-profiles")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-weight-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"个人默认权重","weights":{"skills":20,"roleExperience":20,
                         "relatedYears":20,"responsibilityMatch":20,"industryAndProjects":20}}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currentVersion.skillsWeight").value(20))
            .andReturn();
    String weightProfileVersionId =
        body(weightProfile)
            .get("currentVersion")
            .get("id")
            .asString();

    Cookie[] recruiterSession = loginAndChangePassword(
        "recruiter-it@example.com", oneTimeCredential, "Display-Hr-Test-789!");
    mockMvc.perform(get("/api/v1/admin/users").cookie(recruiterSession))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/directory/users/resolve").cookie(recruiterSession)
            .param("ids", adminUserId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].displayName").value("测试管理员"))
        .andExpect(jsonPath("$[0].workEmail").doesNotExist())
        .andExpect(jsonPath("$[0].credentialHash").doesNotExist())
        .andExpect(jsonPath("$[0].roles").doesNotExist());
    String directoryFixtureId = ids.newId();
    jdbcTemplate.update("""
        INSERT INTO identity_user_account(
          id,display_name,work_email,normalized_email,credential_hash,home_department_id,status,
          must_change_password,registered_at,version,remark)
        VALUES(?,?,?,?,?,?,'ACTIVE',FALSE,?,0,'external MySQL directory pagination fixture')
        """, directoryFixtureId, "匿名目录用户", "directory-fixture@example.invalid",
        "directory-fixture@example.invalid", "!no-login-credential!", departmentId, Instant.now());
    try {
      var directoryPage = body(mockMvc.perform(get("/api/v1/directory/users").cookie(recruiterSession)
              .param("limit", "1")).andExpect(status().isOk())
          .andExpect(jsonPath("$.hasMore").value(true)).andReturn());
      var directorySecondPage = body(mockMvc.perform(get("/api/v1/directory/users").cookie(recruiterSession)
              .param("limit", "1").param("cursor", directoryPage.get("nextCursor").asString()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(false)).andReturn());
      List<String> directoryIds = List.of(directoryPage.at("/items/0/id").asString(),
          directorySecondPage.at("/items/0/id").asString());
      assertThat(directoryIds).containsExactlyInAnyOrder(userId, directoryFixtureId)
          .doesNotContain(adminUserId);
    } finally {
      assertThat(jdbcTemplate.update("DELETE FROM identity_user_account WHERE id=? AND remark=?",
          directoryFixtureId, "external MySQL directory pagination fixture")).isOne();
    }
    mockMvc.perform(get("/api/v1/directory/users").cookie(recruiterSession)
            .param("query", "招聘专员"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(userId));
    int sourceChannelsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM talent_source_channel", Integer.class);
    MvcResult source =
        mockMvc
            .perform(
                post("/api/v1/source-channels")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-source-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"人工上传","channelType":"MANUAL"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    String sourceId = id(source);
    String sourceCode = body(source).get("code").asString();
    assertThat(sourceCode).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/source-channels")
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .header("Idempotency-Key", "mysql-it-source-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"人工上传","channelType":"MANUAL"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(sourceId))
        .andExpect(jsonPath("$.code").value(sourceCode));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_source_channel", Integer.class))
        .isEqualTo(sourceChannelsBefore + 1);

    mockMvc
        .perform(
            post("/api/v1/source-channels")
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .header("Idempotency-Key", "mysql-it-source-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"复用幂等键","channelType":"MANUAL"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

    MvcResult batch =
        mockMvc
            .perform(
                post("/api/v1/intake-batches")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-intake-batch-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"sourceChannelId":"%s","jdVersionId":"%s"}
                        """
                            .formatted(sourceId, jdVersionId)))
            .andExpect(status().isCreated())
            .andReturn();
    String batchId = id(batch);
    byte[] resumeBytes = resumePdf(
        "Name Synthetic Candidate email candidate@example.com Skills Java Experience Project Education");
    MockMultipartFile resumeFile =
        new MockMultipartFile("file", "candidate.pdf", "application/pdf", resumeBytes);
    MvcResult intake =
        mockMvc
            .perform(
                multipart("/api/v1/intake-batches/{batchId}/files", batchId)
                    .file(resumeFile)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-resume-upload-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_IMPORT"))
            .andExpect(jsonPath("$.importStatus").value("PENDING"))
            .andExpect(jsonPath("$.scanStatus").value("CLEAN"))
            .andExpect(jsonPath("$.defaultOwnerUserId").isNotEmpty())
            .andExpect(jsonPath("$.handledByUserId").doesNotExist())
            .andReturn();
    JsonNode intakeJson = body(intake);
    String intakeId = intakeJson.get("id").asString();

    MockMultipartFile repeatedResume =
        new MockMultipartFile("file", "candidate.pdf", "application/pdf", resumeBytes);
    mockMvc
        .perform(
            multipart("/api/v1/intake-batches/{batchId}/files", batchId)
                .file(repeatedResume)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .header("Idempotency-Key", "mysql-it-resume-upload-1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(intakeId));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT total_count FROM talent_intake_batch WHERE id=?", Integer.class, batchId))
        .isOne();

    MvcResult ignoredIntake =
        mockMvc
            .perform(
                multipart("/api/v1/intake-batches/{batchId}/files", batchId)
                    .file(
                        new MockMultipartFile(
                            "file",
                            "ignored-only.pdf",
                            "application/pdf",
                            resumePdf("Ignored resume for intake list verification")))
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-resume-upload-ignored"))
            .andExpect(status().isCreated())
            .andReturn();
    String ignoredIntakeId = id(ignoredIntake);
    mockMvc
        .perform(
            post("/api/v1/intakes/{intakeId}/ignore", ignoredIntakeId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reason\":\"集成测试验证忽略后不再展示\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IGNORED"));

    mockMvc
        .perform(get("/api/v1/intakes").cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].id", Matchers.not(Matchers.hasItem(ignoredIntakeId))));
    mockMvc
        .perform(
            get("/api/v1/intakes")
                .cookie(activeSession)
                .param("query", "ignored-only.pdf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mockMvc
        .perform(
            get("/api/v1/intakes")
                .cookie(activeSession)
                .param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(intakeId))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(
            get("/api/v1/intakes")
                .cookie(activeSession)
                .param("status", "IGNORED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());

    mockMvc.perform(get("/api/v1/intakes/count").cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
    mockMvc.perform(get("/api/v1/intakes/count").cookie(activeSession)
            .param("query", "ignored-only.pdf"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
    mockMvc.perform(get("/api/v1/intakes/count").cookie(activeSession)
            .param("status", "IGNORED"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));

    MvcResult importOnly =
        mockMvc
            .perform(
                multipart("/api/v1/intake-batches/{batchId}/files", batchId)
                    .file(
                        new MockMultipartFile(
                            "file",
                            "import-only.pdf",
                            "application/pdf",
                            resumePdf("Skills Java import only")))
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-resume-import-only"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_IMPORT"))
            .andReturn();
    String importOnlyId = id(importOnly);
    mockMvc
        .perform(
            post("/api/v1/intakes/{intakeId}/import", importOnlyId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"0\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PARSING"))
        .andExpect(jsonPath("$.importStatus").value("PROCESSING"));
    mockMvc
        .perform(
            post("/api/v1/intakes/{intakeId}/import", importOnlyId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"0\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PARSING"));
    mockMvc
        .perform(
            get("/api/v1/intakes")
                .cookie(activeSession)
                .param("importStatus", "PROCESSING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(importOnlyId))
        .andExpect(jsonPath("$.hasMore").value(false));

    mockMvc.perform(get("/api/v1/intakes/count").cookie(activeSession)
            .param("importStatus", "PROCESSING"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    mockMvc.perform(get("/api/v1/intakes").cookie(activeSession).param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.hasMore").value(true));
    mockMvc.perform(get("/api/v1/intakes/count").cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));

    // 真实点击入库并完成共享解析，正式简历仍由生产完成链路创建。
    var firstImport = body(mockMvc.perform(post("/api/v1/intakes/{id}/import", intakeId)
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PARSING")).andReturn());
    String firstProcessingId = firstImport.get("processingId").asString();
    var pendingProcessing = intakeProcessing.findProcessing(firstProcessingId);
    String processingLease = ids.newId();
    Instant processingNow = Instant.now();
    assertThat(intakeProcessing.claim(firstProcessingId, pendingProcessing.getVersion(), processingLease,
        processingNow.plusSeconds(600), processingNow)).isOne();
    var parsedIntake = intakeParsing.parse(intakeProcessing.findProcessing(firstProcessingId));
    var intakeArchive = parsedIntake.archive();
    assertThat(intakeProcessing.succeed(firstProcessingId, processingLease, parsedIntake.pagesJson(),
        parsedIntake.factsJson(), parsedIntake.model().provider(), parsedIntake.model().modelVersion(),
        intakeArchive.objectKey(), intakeArchive.sha256(), intakeArchive.sizeBytes(),
        intakeArchive.encryptionVersion(), Instant.now())).isOne();
    intakeCompletion.completeAvailable();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_resume_intake"
                    + " WHERE id=? AND status='IGNORED' AND object_key IS NOT NULL",
                Integer.class,
                ignoredIntakeId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_process_event"
                    + " WHERE event_type='RESUME_INTAKE_IGNORED'"
                    + " AND target_type='RESUME_INTAKE' AND target_id=? AND reason IS NOT NULL",
                Integer.class,
                ignoredIntakeId))
        .isOne();

    mockMvc
        .perform(
            post("/api/v1/intake-batches/{batchId}/complete", batchId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    MvcResult confirmed = mockMvc.perform(get("/api/v1/intakes").param("status", "CONFIRMED").cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(intakeId)).andReturn();
    JsonNode confirmedJson = body(confirmed).get("items").get(0);
    String candidateId = confirmedJson.get("candidateId").asString();
    String resumeVersionId = confirmedJson.get("resumeVersionId").asString();
    String intakeCandidateName = jdbcTemplate.queryForObject(
        "SELECT full_name FROM talent_candidate WHERE id=?", String.class, candidateId);
    Instant intakeCreatedAt = Instant.parse(confirmedJson.get("createTime").asString());
    for (String path : List.of("/api/v1/intakes", "/api/v1/intakes/count")) {
      String resultSize = path.endsWith("/count") ? "$.count" : "$.items.length()";
      mockMvc.perform(get(path).cookie(activeSession)
              .param("status", "CONFIRMED").param("importStatus", "PROCESSING")
              .param("query", "candidate.pdf").param("name", intakeCandidateName)
              .param("from", intakeCreatedAt.minusSeconds(1).toString())
              .param("to", intakeCreatedAt.plusSeconds(1).toString()))
          .andExpect(status().isOk()).andExpect(jsonPath(resultSize).value(1));
      mockMvc.perform(get(path).cookie(activeSession)
              .param("status", "CONFIRMED").param("name", "不存在的收件候选人"))
          .andExpect(status().isOk()).andExpect(jsonPath(resultSize).value(0));
      mockMvc.perform(get(path).cookie(activeSession)
              .param("status", "CONFIRMED")
              .param("from", intakeCreatedAt.plusSeconds(1).toString())
              .param("to", intakeCreatedAt.plusSeconds(2).toString()))
          .andExpect(status().isOk()).andExpect(jsonPath(resultSize).value(0));
    }

    mockMvc.perform(get("/api/v1/candidates/{candidateId}/resume-versions/{versionId}/source",
            candidateId, resumeVersionId).cookie(recruiterSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CANDIDATE_RESUME_ACCESS_FORBIDDEN"));
    mockMvc.perform(get("/api/v1/candidates/{candidateId}/resume-versions/{versionId}/source",
            candidateId, resumeVersionId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intakeId").value(intakeId))
        .andExpect(jsonPath("$.sourceChannelId").value(sourceId))
        .andExpect(jsonPath("$.sourceChannelName").isNotEmpty())
        .andExpect(jsonPath("$.objectKey").doesNotExist());

    String parseTraceId = "http-resume-parse-" + ids.newId();
    MvcResult resumeParse =
        mockMvc
            .perform(
                post("/api/v1/resume-versions/{id}/parse", resumeVersionId)
                    .header("X-Trace-Id", parseTraceId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-resume-parse-1"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn();
    String resumeTaskId =
        body(resumeParse)
            .get("taskId")
            .asString();
    assertThat(resumeParse.getResponse().getHeader("X-Trace-Id")).isEqualTo(parseTraceId);
    assertThat(MDC.get("traceId")).isNull();
    mockMvc.perform(post("/api/v1/resume-versions/{id}/parse", resumeVersionId)
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .header("Idempotency-Key", "mysql-it-resume-parse-1")
            .header("X-Trace-Id", "different-replayed-request-123456"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.taskId").value(resumeTaskId));
    assertThat(jdbcTemplate.queryForObject("SELECT trace_id FROM reporting_async_task WHERE id=?",
        String.class, resumeTaskId)).isEqualTo(parseTraceId);
    var modelContexts = new ArrayList<Map<String, String>>();
    var workerContext = new AtomicReference<Map<String, String>>();
    doAnswer(call -> {
      modelContexts.add(MDC.getCopyOfContextMap());
      if (modelContexts.size() == 1) throw new ModelUnavailableException("synthetic temporary unavailability");
      return call.callRealMethod();
    }).when(resumeParseModel).parse(any());
    // 由生产轮询器在独立线程领取，不能靠 HTTP 线程或 TaskDecorator 偶然携带上下文。
    try (var executor = Executors.newSingleThreadExecutor()) {
      var worker = new ResumeParseWorker();
      var properties = new PlatformExecutorProperties();
      properties.getAi().setCoreSize(1);
      properties.getAiWorkloadSlots().setResumeParse(1);
      TaskExecutor taskExecutor = runnable -> executor.submit(() -> {
        MDC.setContextMap(Map.of("traceId", "unrelated-worker-trace-123456", "sentinel", "preserve"));
        try {
          runnable.run();
          workerContext.set(MDC.getCopyOfContextMap());
        } finally {
          MDC.clear();
        }
      });
      ReflectionTestUtils.setField(worker, "executorProperties", properties);
      ReflectionTestUtils.setField(worker, "parsing", resumeParsing);
      ReflectionTestUtils.setField(worker, "taskBatchRunner", taskBatchRunner);
      ReflectionTestUtils.setField(worker, "taskExecutor", taskExecutor);
      ReflectionTestUtils.invokeMethod(worker, "initializeConcurrency");
      worker.poll();
      executor.submit(() -> {}).get(60, TimeUnit.SECONDS);
      assertThat(workerContext.get()).containsExactlyInAnyOrderEntriesOf(
          Map.of("traceId", "unrelated-worker-trace-123456", "sentinel", "preserve"));
      mockMvc.perform(get("/api/v1/tasks/{id}", resumeTaskId).cookie(activeSession))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
      mockMvc.perform(post("/api/v1/tasks/{id}/retry-failed", resumeTaskId).cookie(activeSession)
              .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
              .header("X-Trace-Id", "different-retry-request-123456")
              .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"合成模型恢复后重试\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QUEUED"));
      worker.poll();
      executor.submit(() -> {}).get(60, TimeUnit.SECONDS);
    }
    mockMvc
        .perform(get("/api/v1/tasks/{id}", resumeTaskId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    assertThat(workerContext.get()).containsExactlyInAnyOrderEntriesOf(
        Map.of("traceId", "unrelated-worker-trace-123456", "sentinel", "preserve"));
    assertThat(modelContexts).hasSize(2);
    for (int attempt = 0; attempt < modelContexts.size(); attempt++) {
      assertThat(modelContexts.get(attempt)).containsEntry("taskId", resumeTaskId)
          .containsEntry("taskType", TalentConstants.RESUME_PARSE_TASK_TYPE)
          .containsEntry("attemptNo", Integer.toString(attempt + 1)).containsEntry("traceId", parseTraceId);
    }
    assertThat(jdbcTemplate.queryForObject("SELECT trace_id FROM reporting_async_task WHERE id=?",
        String.class, resumeTaskId)).isEqualTo(parseTraceId);
    assertThat(jdbcTemplate.queryForList("SELECT trace_id FROM reporting_outbox_event WHERE aggregate_id=?",
        String.class, candidateId)).contains(parseTraceId);
    MvcResult resumeFacts =
        mockMvc
            .perform(
                get("/api/v1/resume-versions/{id}/facts", resumeVersionId).cookie(activeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skills[0].skillName").value("Java"))
            .andExpect(jsonPath("$.snapshot.sourceType").value("AI_PARSE"))
            .andReturn();
    JsonNode resumeFactJson = body(resumeFacts);
    String baseFactSnapshotId = resumeFactJson.get("snapshot").get("id").asString();
    mockMvc
        .perform(
            get("/api/v1/resume-versions/{id}/extracted-text", resumeVersionId)
                .cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.route").value("NATIVE_TEXT"))
        .andExpect(jsonPath("$.pages[0].text").value(Matchers.containsString("Java")));
    mockMvc
        .perform(
            post("/api/v1/fact-corrections")
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"resumeVersionId":"%s","baseFactSnapshotId":"%s","reason":"候选人电话确认当前职级",
                     "sourceType":"HUMAN_PROVIDED","facts":{
                      "candidate":{"fullName":"合成候选人","email":"candidate@example.com","phone":"13800000000","currentRole":"高级工程师","city":"上海","age":32,"gender":"FEMALE","jobIntention":"Java 后端工程师"},
                      "workExperiences":[],"projectExperiences":[],"educations":[],
                      "skills":[{"id":"skill_1","name":"Java","level":"熟练","years":3}],
                      "credentials":[],"languages":[],"industryTags":[],"responsibilityTags":["客户支持"],"unknownItems":[],"evidence":[]}}
                    """
                        .formatted(resumeVersionId, baseFactSnapshotId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.snapshotVersion").value(2))
        .andExpect(jsonPath("$.affectedCapabilities").isArray());

    // 采用共享解析并完成公司默认评分，才满足当前简历库的业务准入条件。
    asyncTasks.claim(ResumeParsingApplicationManager.INTAKE_ADOPTION_TASK_TYPE, 10).forEach(task ->
        backgroundActors.execute(task.requestedByUserId(), () -> {
          resumeParsing.processIntakeAdoption(task, intakeProcessing.findByResumeVersion(task.resourceId()));
          return null;
        }));
    intakeCompletion.completeAvailable();
    asyncTasks.claim(MatchRunApplicationManager.TASK_TYPE, 10).forEach(matchRuns::process);
    intakeCompletion.completeAvailable();
    var completedFirstIntake = body(mockMvc.perform(get("/api/v1/intakes").param("status", "COMPLETED").cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(intakeId))
        .andExpect(jsonPath("$.items[0].scoreStatus").value("SUCCEEDED")).andReturn()).get("items").get(0);
    String defaultMatchRunId = completedFirstIntake.get("scoreRunId").asString();
    int scoringCallsAfterDefault = SyntheticProfessionalScoringModel.calls();
    JsonNode defaultResults = body(mockMvc
        .perform(get("/api/v1/match-runs/{id}/results", defaultMatchRunId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].professionalScore").value(88))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.provider").value("synthetic-it"))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.model").value("professional-v1"))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.defaultProfessionalScore").value(88))
        .andReturn());
    String frozenAssessmentId = defaultResults.at("/items/0/details/aiAssessment/assessmentId").asString();
    assertThat(frozenAssessmentId).isNotBlank();
    String frozenAssessmentJson = jdbcTemplate.queryForObject(
        "SELECT result_json FROM matching_ai_professional_assessment WHERE id=?",
        String.class, frozenAssessmentId);
    assertThat(objectMapper.readTree(frozenAssessmentJson).get("professionalScore").decimalValue())
        .isEqualByComparingTo("88");

    // 共享库现在要求归属人先完成当前简历筛选；导入/解析本身不代表准入。
    mockMvc.perform(get("/api/v1/candidate-pool-statuses").cookie(activeSession).param("candidateIds", candidateId))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].libraryEligible").value(false));
    JsonNode candidateBeforeScreen = body(mockMvc.perform(get("/api/v1/candidates/{id}", candidateId).cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.ownerUserId").value(adminUserId)).andReturn());
    mockMvc.perform(post("/api/v1/candidates/{id}/screen-out", candidateId).cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("version", candidateBeforeScreen.get("version").asLong(),
                "reason", "归属人完成当前简历筛选，转入共享库验收"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.libraryEligible").value(true));
    assertThat(searchProjection.projectNext(200)).isOne();
    // Elasticsearch refresh is asynchronous; wait for the persisted projection to become searchable.
    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        mockMvc.perform(get("/api/v1/talent-search").cookie(activeSession).param("q", "Java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].candidateId").value(candidateId)));
    MvcResult talentSearch =
        mockMvc
            .perform(
                get("/api/v1/talent-search")
                    .cookie(activeSession)
                    .param("q", "Java")
                    .param("skills", "Java")
                    .param("cities", "上海")
                    .param("sourceChannel", sourceCode)
                    .param("intention", "Java 后端")
                    .param("gender", "FEMALE")
                    .param("minAge", "30")
                    .param("maxAge", "35")
                    .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].candidateId").value(candidateId))
            .andExpect(jsonPath("$.items[0].resumeVersionId").value(resumeVersionId))
            .andExpect(jsonPath("$.items[0].conditionMatches[0].matched").value(true))
            .andExpect(jsonPath("$.items[0].age").value(32))
            .andExpect(jsonPath("$.items[0].gender").value("FEMALE"))
            .andExpect(jsonPath("$.items[0].sourceChannel").value(sourceCode))
            .andExpect(jsonPath("$.items[0].engagementLock").doesNotExist())
            .andReturn();
    assertThat(
            body(talentSearch)
                .get("items")
                .size())
        .isOne();

    MvcResult acquiredLock =
        mockMvc
            .perform(
                post("/api/v1/candidates/{id}/engagement-locks", candidateId)
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"电话沟通后候选人确认继续跟进\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.candidateId").value(candidateId))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    JsonNode acquiredLockJson = body(acquiredLock);
    String engagementLockId = acquiredLockJson.get("id").asString();
    Instant firstExpiry =
        Instant.parse(acquiredLockJson.get("expiresAt").asString());
    mockMvc
        .perform(
            post("/api/v1/candidates/{id}/engagement-locks", candidateId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"重复联系应被拒绝\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANDIDATE_ENGAGEMENT_LOCKED"));
    mockMvc
        .perform(
            post("/api/v1/candidates/{id}/engagement-progress", candidateId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progressType\":\"CANDIDATE_REPLY\",\"reason\":\"候选人回复并确认面试时间\"}"))
        .andExpect(status().isNoContent());
    Instant renewedExpiry =
        jdbcTemplate.queryForObject(
            "SELECT expires_at FROM recruitment_candidate_engagement_lock WHERE id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            engagementLockId);
    assertThat(renewedExpiry).isAfterOrEqualTo(firstExpiry);
    mockMvc
        .perform(get("/api/v1/talent-search").cookie(activeSession).param("skills", "Java"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].engagementLock.lockId").value(engagementLockId))
        .andExpect(jsonPath("$.items[0].engagementLock.canContact").value(true));
    mockMvc
        .perform(
            post("/api/v1/engagement-locks/{id}/release", engagementLockId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"候选人明确要求暂不继续\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RELEASED"));
    mockMvc
        .perform(
            post("/api/v1/candidates/{id}/engagement-locks", "01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不存在的候选人不可创建孤儿锁\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CANDIDATE_NOT_FOUND"));

    mockMvc.perform(post("/api/v1/screening-admissions").cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .header("Idempotency-Key", "mysql-it-screening-admission")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("items", List.of(
                Map.of("candidateId", candidateId, "resumeVersionId", resumeVersionId,
                    "jdVersionId", jdVersionId, "defaultMatchRunId", defaultMatchRunId))))))
        .andExpect(status().isCreated());

    MvcResult matchRun =
        mockMvc
            .perform(
                post("/api/v1/match-runs")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-match-run-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"jdVersionId":"%s","resumeVersionIds":["%s"],
                         "weightProfileVersionId":"%s","selectedCriterionIds":[],
                         "sortMode":"REFERENCE_SCORE"}
                        """
                            .formatted(jdVersionId, resumeVersionId, weightProfileVersionId)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn();
    JsonNode matchRunJson = body(matchRun);
    String matchRunId = matchRunJson.get("id").asString();
    String matchTaskId = matchRunJson.get("taskId").asString();
    asyncTasks.claim(MatchRunApplicationManager.TASK_TYPE, 3).forEach(matchRuns::process);
    mockMvc
        .perform(get("/api/v1/tasks/{id}", matchTaskId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    mockMvc
        .perform(get("/api/v1/match-runs/{id}", matchRunId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.succeededCount").value(1));
    JsonNode defaultWeightResults = body(mockMvc
        .perform(get("/api/v1/match-runs/{id}/results", matchRunId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].candidateId").value(candidateId))
        .andExpect(jsonPath("$.items[0].professionalScore").value(71.7))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.assessmentId").value(frozenAssessmentId))
        .andExpect(jsonPath("$.items[0].mustTier").value("PASS"))
        .andExpect(jsonPath("$.items[0].details.dimensions.length()").value(5))
        .andReturn());
    assertThat(defaultWeightResults.at("/items/0/details/aiAssessment/defaultProfessionalScore").decimalValue())
        .isEqualByComparingTo("88");
    assertThat(SyntheticProfessionalScoringModel.calls()).isEqualTo(scoringCallsAfterDefault);

    MvcResult skillsOnlyProfile = mockMvc.perform(post("/api/v1/weight-profiles")
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .header("Idempotency-Key", "mysql-it-weight-skills-priority")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"技能优先权重","weights":{"skills":96,"roleExperience":1,
                 "relatedYears":1,"responsibilityMatch":1,"industryAndProjects":1}}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currentVersion.skillsWeight").value(96))
        .andReturn();
    String skillsOnlyVersionId = body(skillsOnlyProfile).at("/currentVersion/id").asString();
    MvcResult reweightedRun = mockMvc.perform(post("/api/v1/match-runs")
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .header("Idempotency-Key", "mysql-it-match-run-reweighted")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"jdVersionId":"%s","resumeVersionIds":["%s"],
                 "weightProfileVersionId":"%s","selectedCriterionIds":[],
                 "sortMode":"PROFESSIONAL_SCORE"}
                """.formatted(jdVersionId, resumeVersionId, skillsOnlyVersionId)))
        .andExpect(status().isAccepted()).andReturn();
    JsonNode reweightedRunJson = body(reweightedRun);
    String reweightedRunId = reweightedRunJson.get("id").asString();
    asyncTasks.claim(MatchRunApplicationManager.TASK_TYPE, 3).forEach(matchRuns::process);
    mockMvc.perform(get("/api/v1/tasks/{id}", reweightedRunJson.get("taskId").asString())
            .cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    mockMvc.perform(get("/api/v1/match-runs/{id}/results", reweightedRunId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].professionalScore").value(89.0))
        .andExpect(jsonPath("$.items[0].details.dimensions[0].configuredWeight").value(96))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.assessmentId").value(frozenAssessmentId))
        .andExpect(jsonPath("$.items[0].details.aiAssessment.defaultProfessionalScore").value(88));
    assertThat(SyntheticProfessionalScoringModel.calls()).isEqualTo(scoringCallsAfterDefault);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT result_json FROM matching_ai_professional_assessment WHERE id=?",
        String.class, frozenAssessmentId)).isEqualTo(frozenAssessmentJson);

    MvcResult cancelledMatchRun =
        mockMvc
            .perform(
                post("/api/v1/match-runs")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-match-run-cancel-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"jdVersionId":"%s","resumeVersionIds":["%s"],
                         "weightProfileVersionId":"%s","selectedCriterionIds":[],
                         "sortMode":"PROFESSIONAL_SCORE"}
                        """
                            .formatted(jdVersionId, resumeVersionId, weightProfileVersionId)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn();
    JsonNode cancelledMatchRunJson = body(cancelledMatchRun);
    String cancelledMatchRunId = cancelledMatchRunJson.get("id").asString();
    String cancelledMatchTaskId = cancelledMatchRunJson.get("taskId").asString();
    mockMvc
        .perform(
            post("/api/v1/match-runs/{id}/cancel", cancelledMatchRunId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"集成测试验证排队态取消\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mockMvc
        .perform(get("/api/v1/match-runs/{id}", cancelledMatchRunId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mockMvc
        .perform(get("/api/v1/tasks/{id}", cancelledMatchTaskId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    MvcResult secondBatch =
        mockMvc
            .perform(
                post("/api/v1/intake-batches")
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-intake-batch-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"sourceChannelId":"%s","jdVersionId":"%s"}
                        """
                            .formatted(sourceId, jdVersionId)))
            .andExpect(status().isCreated())
            .andReturn();
    String secondBatchId = id(secondBatch);
    byte[] secondResumeBytes =
        resumePdf("Name Synthetic Candidate email another@example.com Skills Spring Experience Project Education");
    MvcResult secondIntake =
        mockMvc
            .perform(
                multipart("/api/v1/intake-batches/{batchId}/files", secondBatchId)
                    .file(
                        new MockMultipartFile(
                            "file", "candidate-v2.pdf", "application/pdf", secondResumeBytes))
                    .cookie(activeSession)
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .header("Idempotency-Key", "mysql-it-resume-upload-2"))
            .andExpect(status().isCreated())
            .andReturn();
    String secondIntakeId = id(secondIntake);

    // 同上，以下只覆盖既有身份冲突人工核对夹具，不代表上传后可直接确认。
    jdbcTemplate.update("UPDATE talent_resume_intake SET status='READY_FOR_CONFIRMATION' WHERE id=?", secondIntakeId);

    mockMvc
        .perform(
            post("/api/v1/intakes/{intakeId}/confirm", secondIntakeId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"fullName":"%s","email":"another@example.com","version":0}
                    """.formatted(intakeCandidateName)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDENTITY_CONFLICT_REQUIRES_REVIEW"));

    MvcResult conflicts =
        mockMvc
            .perform(
                get("/api/v1/identity-conflicts").param("status", "OPEN").cookie(activeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].candidateIdA").value(candidateId))
            .andExpect(jsonPath("$.items[0].conflictType").value("NAME_MATCH_CONTACT_MISMATCH"))
            .andReturn();
    String conflictId =
        body(conflicts)
            .get("items")
            .get(0)
            .get("id")
            .asString();
    mockMvc
        .perform(
            post("/api/v1/identity-conflicts/{id}/resolve", conflictId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"resolution":"CREATE_SEPARATE","reason":"联系方式不同，人工确认是同名不同人"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    mockMvc
        .perform(
            post("/api/v1/intakes/{intakeId}/confirm", secondIntakeId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"fullName":"%s","email":"another@example.com","version":0}
                    """.formatted(intakeCandidateName)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM talent_candidate", Integer.class))
        .isEqualTo(2);

    mockMvc
        .perform(
            get("/api/v1/resume-versions/{id}/preview", resumeVersionId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileName").value("candidate.pdf"))
        .andExpect(jsonPath("$.downloadable").value(false));

    MvcResult downloadGrant =
        mockMvc
            .perform(
                post("/api/v1/resume-versions/{id}/download-authorizations", resumeVersionId)
                    .cookie(activeSession)
                    .header(
                        adminToken.get("headerName").asString(), adminToken.get("token").asString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();
    String downloadToken =
        body(downloadGrant)
            .get("token")
            .asString();
    mockMvc
        .perform(
            get("/api/v1/download-authorizations/{token}", downloadToken).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(resumeBytes));

    MvcResult applicationCreated =
        mockMvc
            .perform(
                post("/api/v1/applications")
                    .cookie(activeSession)
                    .header("Idempotency-Key", "application-create-it")
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"candidateId":"%s","jdVersionId":"%s","resumeVersionId":"%s",
                         "reason":"真实数据库创建招聘活动"}
                        """
                            .formatted(candidateId, jdVersionId, resumeVersionId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.application.currentStage").value("PATH_SELECTION"))
            .andExpect(jsonPath("$.application.workflowTemplateVersionId").value(workflowId))
            .andExpect(jsonPath("$.application.matchPlanVersionId").value(activeScoringId))
            .andExpect(jsonPath("$.application.resumeVersionId").value(resumeVersionId))
            .andExpect(jsonPath("$.workflowSteps.length()").value(7))
            .andReturn();
    JsonNode applicationJson = body(applicationCreated);
    String applicationId = applicationJson.get("application").get("id").asString();
    String internalInterviewStepId = workflowStep(applicationJson, "PRE_SUBMISSION_INTERNAL_INTERVIEW").get("id").asString();

    mockMvc
        .perform(
            post("/api/v1/applications")
                .cookie(activeSession)
                .header("Idempotency-Key", "application-create-it")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"candidateId":"%s","jdVersionId":"%s","resumeVersionId":"%s",
                     "reason":"真实数据库创建招聘活动"}
                    """
                        .formatted(candidateId, jdVersionId, resumeVersionId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.application.id").value(applicationId));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .cookie(activeSession)
                .header("Idempotency-Key", "application-create-duplicate-it")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"candidateId":"%s","jdVersionId":"%s","resumeVersionId":"%s",
                     "reason":"验证同一候选人与 JD 只能有一个有效活动"}
                    """
                        .formatted(candidateId, jdVersionId, resumeVersionId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTIVE_APPLICATION_EXISTS"));

    MvcResult pathSelected = mockMvc.perform(post("/api/v1/applications/{id}/next-actions", applicationId)
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"stepCode":"PRE_SUBMISSION_INTERNAL_INTERVIEW","version":"0",
                 "reason":"当前先执行推荐前内部面试，不预定后续路径"}
                """))
        .andExpect(status().isOk()).andExpect(jsonPath("$.application.currentStage").value("IN_PROCESS"))
        .andReturn();
    JsonNode pathJson = body(pathSelected);
    String submissionStepId = workflowStep(pathJson, "CLIENT_SUBMISSION").get("id").asString();
    for (JsonNode step : pathJson.get("workflowSteps")) {
      assertThat(step.get("status").asString()).isEqualTo(
          step.get("id").asString().equals(internalInterviewStepId) ? "IN_PROGRESS" : "AVAILABLE");
    }

    Instant interviewStart = Instant.now().plusSeconds(7200);
    Instant interviewEnd = interviewStart.plusSeconds(3600);
    MvcResult interviewCreated =
        mockMvc
            .perform(
                post("/api/v1/interviews")
                    .cookie(activeSession)
                    .header("Idempotency-Key", "interview-create-it")
                    .header("X-Recruitment-Action", "INTERVIEW")
                    .header("X-Recruitment-Operation", "3346b521-715d-42a5-b859-c05c13335b7a")
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"applicationId":"%s","workflowStepId":"%s",
                         "startAt":"%s","endAt":"%s","timezoneId":"Asia/Shanghai",
                         "meetingMode":"ONLINE","meetingUrl":"https://meeting.example/mysql-it",
                         "participantUserIds":["%s"],"candidateNotificationChannel":"IN_APP",
                         "reason":"真实数据库安排推荐前内部面试"}
                        """
                            .formatted(
                                applicationId,
                                internalInterviewStepId,
                                interviewStart,
                                interviewEnd,
                                adminUserId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.interview.interviewType").value("INTERNAL"))
            .andExpect(jsonPath("$.interview.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.participants[0].userId").value(adminUserId))
            .andExpect(jsonPath("$.notifications").doesNotExist())
            .andExpect(jsonPath("$.feedback").doesNotExist())
            .andExpect(jsonPath("$.scheduleHistory").doesNotExist())
            .andReturn();
    JsonNode interviewJson = body(interviewCreated);
    String interviewId = interviewJson.get("interview").get("id").asString();
    mockMvc.perform(get("/api/v1/interviews/{id}/notifications", interviewId)
            .cookie(activeSession).param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc.perform(get("/api/v1/interviews/{id}/schedule-history", interviewId).cookie(activeSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].actionType").value("CREATE"))
        .andExpect(jsonPath("$.hasMore").value(false));

    mockMvc
        .perform(
            post("/api/v1/interviews")
                .cookie(activeSession)
                .header("Idempotency-Key", "interview-create-it")
                    .header("X-Recruitment-Action", "INTERVIEW")
                    .header("X-Recruitment-Operation", "3346b521-715d-42a5-b859-c05c13335b7a")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"applicationId":"%s","workflowStepId":"%s",
                     "startAt":"%s","endAt":"%s","timezoneId":"Asia/Shanghai",
                     "meetingMode":"ONLINE","meetingUrl":"https://meeting.example/mysql-it",
                     "participantUserIds":["%s"],"candidateNotificationChannel":"IN_APP",
                     "reason":"真实数据库安排推荐前内部面试"}
                    """
                        .formatted(
                            applicationId,
                            internalInterviewStepId,
                            interviewStart,
                            interviewEnd,
                            adminUserId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.interview.id").value(interviewId));

    var interviewActivity = body(mockMvc.perform(get("/api/v1/applications/{id}/activity-records", applicationId)
            .cookie(activeSession).param("type", "INTERVIEW").param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("面试"))
        .andExpect(jsonPath("$.items[0].comment").value("真实数据库安排推荐前内部面试"))
        .andExpect(jsonPath("$.items[0].facts").isNotEmpty()).andReturn()).get("items").get(0);
    var factTimes = new ArrayList<Instant>();
    for (JsonNode fact : interviewActivity.get("facts")) {
      factTimes.add(Instant.parse(fact.get("occurredAt").asString()));
    }
    assertThat(Instant.parse(interviewActivity.get("occurredAt").asString()))
        .isEqualTo(factTimes.stream().min(Instant::compareTo).orElseThrow());
    var activityFirstPage = body(mockMvc.perform(get("/api/v1/applications/{id}/activity-records", applicationId)
            .cookie(activeSession).param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.hasMore").value(true)).andReturn());
    var activityNextPage = body(mockMvc.perform(get("/api/v1/applications/{id}/activity-records", applicationId)
            .cookie(activeSession).param("limit", "1").param("cursor", activityFirstPage.get("nextCursor").asString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andReturn());
    assertThat(activityNextPage.get("items").get(0).get("id").asString())
        .isNotEqualTo(activityFirstPage.get("items").get(0).get("id").asString());

    mockMvc.perform(post("/api/v1/interviews/{id}/reschedule", interviewId)
            .cookie(activeSession)
            .header("Idempotency-Key", "interview-reschedule-it")
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"startAt":"%s","endAt":"%s","timezoneId":"Asia/Shanghai",
                 "version":"%s","reason":"真实数据库改期并验证排期历史分页"}
                """.formatted(interviewStart.plusSeconds(3600), interviewEnd.plusSeconds(3600),
                    interviewJson.get("interview").get("version").asString())))
        .andExpect(status().isOk());
    var scheduleFirstPage = body(mockMvc.perform(get("/api/v1/interviews/{id}/schedule-history", interviewId)
            .cookie(activeSession).param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].actionType").value("RESCHEDULE"))
        .andExpect(jsonPath("$.hasMore").value(true)).andReturn());
    var scheduleLastPage = body(mockMvc.perform(get("/api/v1/interviews/{id}/schedule-history", interviewId)
            .cookie(activeSession).param("limit", "1").param("cursor", scheduleFirstPage.get("nextCursor").asString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].actionType").value("CREATE"))
        .andExpect(jsonPath("$.hasMore").value(false)).andReturn());
    assertThat(scheduleLastPage.get("items").get(0).get("id").asString())
        .isNotEqualTo(scheduleFirstPage.get("items").get(0).get("id").asString());

    mockMvc
        .perform(get("/api/v1/my/interview-tasks").cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.interviewId=='%s')]".formatted(interviewId)).exists());

    // 仅构造升级前已保存的草稿历史；新请求只能正式提交。
    String legacyFeedbackId = ids.newId();
    jdbcTemplate.update("""
        INSERT INTO interview_feedback(id,interview_id,author_user_id,feedback_kind,status,current_version_no)
        VALUES(?,?,?,'INTERNAL','DRAFT',1)
        """, legacyFeedbackId, interviewId, adminUserId);
    jdbcTemplate.update("""
        INSERT INTO interview_feedback_version(id,feedback_id,version_no,verdict,short_comment,
          feedback_occurred_at,change_reason,create_id)
        VALUES(?,?,1,'PASS','内部面试基本符合',UTC_TIMESTAMP(6),'升级前已保存的历史草稿',?)
        """, ids.newId(), legacyFeedbackId, adminUserId);

    for (int replay = 0; replay < 2; replay++) {
    mockMvc
        .perform(
            post("/api/v1/interviews/{id}/feedback", interviewId)
                .cookie(activeSession)
                .header("Idempotency-Key", "interview-feedback-submit-it")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verdict":"PASS","shortComment":"内部面试通过",
                     "status":"SUBMITTED","changeReason":"面试官正式提交本人面评"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feedback.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.currentVersion.versionNo").value(2))
        .andExpect(jsonPath("$.currentVersion.shortComment").value("内部面试通过"));
    }
    mockMvc.perform(post("/api/v1/interviews/{id}/feedback", interviewId)
            .cookie(activeSession).header("Idempotency-Key", "interview-feedback-after-complete-it")
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"verdict":"PASS","shortComment":"尝试修改已完成面评","status":"SUBMITTED",
                 "changeReason":"验证完成后新请求不能写入"}
                """))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTERVIEW_COMPLETED"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM interview_feedback_version WHERE version_no=1 AND short_comment=?",
        Integer.class, "内部面试基本符合")).isOne();
    mockMvc.perform(get("/api/v1/interviews/{id}/feedback", interviewId).cookie(activeSession).param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].currentVersion.versionNo").value(2))
        .andExpect(jsonPath("$.hasMore").value(false));
    mockMvc
        .perform(get("/api/v1/interviews/{id}", interviewId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interview.status").value("COMPLETED"));

    mockMvc
        .perform(
            post(
                    "/api/v1/applications/{id}/workflow-steps/{stepId}/follow-ups",
                    applicationId,
                    internalInterviewStepId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"channel":"IN_PERSON","summary":"内部面试已完成且面评已提交",
                     "nextPlan":"进入甲方推荐步骤"}
                    """))
        .andExpect(status().isCreated());
    JsonNode internalStepBeforeComplete = workflowStep(body(mockMvc
        .perform(get("/api/v1/applications/{id}", applicationId).cookie(activeSession))
        .andExpect(status().isOk()).andReturn()), "PRE_SUBMISSION_INTERNAL_INTERVIEW");
    assertThat(internalStepBeforeComplete.get("id").asString()).isEqualTo(internalInterviewStepId);
    MvcResult internalStepCompleted = mockMvc
        .perform(
            post(
                    "/api/v1/applications/{id}/workflow-steps/{stepId}/complete",
                    applicationId,
                    internalInterviewStepId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"result":"PASSED","reason":"推荐前内部面试通过","version":%d}
                    """.formatted(internalStepBeforeComplete.get("version").asLong())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application.currentStage").value("PATH_SELECTION"))
        .andReturn();
    long applicationAfterInterviewVersion = body(internalStepCompleted).get("application").get("version").asLong();
    MvcResult startedStep = mockMvc.perform(post("/api/v1/applications/{id}/next-actions", applicationId)
            .cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"stepCode":"CLIENT_SUBMISSION","version":"%d","reason":"内部面试通过后推荐给客户"}
                """.formatted(applicationAfterInterviewVersion)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.application.currentStage").value("IN_PROCESS"))
        .andReturn();
    assertThat(workflowStep(body(startedStep), "CLIENT_SUBMISSION").get("status").asString())
        .isEqualTo("IN_PROGRESS");

    MvcResult submissionCreated =
        mockMvc
            .perform(
                post("/api/v1/client-submissions")
                    .cookie(activeSession)
                    .header("Idempotency-Key", "client-submission-create-it")
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"applicationId":"%s"}
                        """
                            .formatted(applicationId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.submission.deliveryStatus").value("DRAFT"))
            .andExpect(jsonPath("$.submission.resumeVersionId").value(resumeVersionId))
            .andReturn();
    String clientSubmissionId =
        body(submissionCreated)
            .get("submission")
            .get("id")
            .asString();
    mockMvc
        .perform(
            post("/api/v1/client-submissions/{id}/send", clientSubmissionId)
                .cookie(activeSession)
                .header("Idempotency-Key", "client-submission-send-it")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"recipient":"client@example.com","channel":"EMAIL",
                     "reason":"甲方收件信息已人工确认","version":0}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submission.deliveryStatus").value("SENT"))
        .andExpect(jsonPath("$.submission.processStatus").value("SCREENING"))
        .andExpect(jsonPath("$.sendSnapshot.contentSha256").isNotEmpty());

    JsonNode boundSubmissionStep = workflowStep(body(mockMvc
        .perform(get("/api/v1/applications/{id}", applicationId).cookie(activeSession))
        .andExpect(status().isOk()).andReturn()), "CLIENT_SUBMISSION");
    assertThat(boundSubmissionStep.get("id").asString()).isEqualTo(submissionStepId);
    assertThat(boundSubmissionStep.get("clientSubmissionId").asString()).isEqualTo(clientSubmissionId);
    long startedSubmissionStepVersion = workflowStep(body(startedStep), "CLIENT_SUBMISSION").get("version").asLong();
    long boundSubmissionStepVersion = boundSubmissionStep.get("version").asLong();
    assertThat(boundSubmissionStepVersion).isEqualTo(startedSubmissionStepVersion + 1);

    mockMvc
        .perform(
            post(
                    "/api/v1/applications/{id}/workflow-steps/{stepId}/follow-ups",
                    applicationId,
                    submissionStepId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"channel":"CLIENT_SYSTEM","summary":"已向甲方发送并人工核验跟进事实",
                     "nextPlan":"进入人工招聘决定"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.channel").value("CLIENT_SYSTEM"));

    // 发送绑定已递增步骤版本，旧页面持有的版本必须被拒绝。
    mockMvc.perform(post("/api/v1/applications/{id}/workflow-steps/{stepId}/complete",
            applicationId, submissionStepId)
        .cookie(activeSession)
        .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"result":"PASSED","reason":"甲方推荐步骤完成",
             "clientSubmissionId":"%s","version":%d}
            """.formatted(clientSubmissionId, startedSubmissionStepVersion)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPLICATION_STATE_CHANGED"));

    MvcResult submissionCompleted = mockMvc
        .perform(
            post(
                    "/api/v1/applications/{id}/workflow-steps/{stepId}/complete",
                    applicationId,
                    submissionStepId)
                .cookie(activeSession)
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"result":"PASSED","reason":"甲方推荐步骤完成",
                     "clientSubmissionId":"%s","version":%d}
                    """
                        .formatted(clientSubmissionId, boundSubmissionStepVersion)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application.currentStage").value("PATH_SELECTION")).andReturn();
    long applicationAfterSubmissionVersion = body(submissionCompleted).get("application").get("version").asLong();
    mockMvc.perform(post("/api/v1/applications/{id}/next-actions", applicationId).cookie(activeSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"stepCode":"DECISION","version":"%d","reason":"客户推荐完成后进入人工招聘决定"}
                """.formatted(applicationAfterSubmissionVersion)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.application.currentStage").value("DECISION"));

    MvcResult decisionCreated =
        mockMvc
            .perform(
                post("/api/v1/applications/{id}/decisions", applicationId)
                    .cookie(activeSession)
                    .header("Idempotency-Key", "application-decision-it")
                    .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"decision":"REJECT","reason":"招聘负责人依据完整事实作出人工决定"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.decision").value("REJECT"))
            .andExpect(jsonPath("$.decidedByUserId").isNotEmpty())
            .andReturn();
    String decisionId = id(decisionCreated);
    mockMvc
        .perform(
            post("/api/v1/applications/{id}/decisions", applicationId)
                .cookie(activeSession)
                .header("Idempotency-Key", "application-decision-it")
                .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"decision":"REJECT","reason":"招聘负责人依据完整事实作出人工决定"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(decisionId));
    mockMvc
        .perform(get("/api/v1/applications/{id}", applicationId).cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application.currentStage").value("TERMINATED"))
        .andExpect(jsonPath("$.application.outcome").value("REJECT"))
        .andExpect(jsonPath("$.application.terminalAt").isNotEmpty())
        .andExpect(jsonPath("$.elapsedSeconds").isNumber());
    mockMvc
        .perform(get("/api/v1/workbench").cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.applicationId=='%s')]".formatted(applicationId)).isEmpty());

    mockMvc
        .perform(get("/api/v1/tasks").cookie(activeSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].taskId").isNotEmpty());
    metricCaptureWorker.pollIncremental();
    Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        mockMvc.perform(get("/api/v1/reports/funnel").cookie(activeSession))
            .andExpect(result -> assertThat(result.getResolvedException()).isNull())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataUpdatedAt").isNotEmpty())
            .andExpect(jsonPath("$.projectionStatus").isNotEmpty())
            .andExpect(jsonPath("$.definitions[?(@.eventType=='SUBMISSION_SENT')]").isNotEmpty())
            .andExpect(jsonPath("$.points[?(@.eventType=='SUBMISSION_SENT')].eventCount").value(1)));
    auditCaptureWorker.pollIncremental();
    Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        mockMvc.perform(get("/api/v1/audit-events").cookie(activeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].sourceModule").isNotEmpty()));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
        .isEqualTo(appliedMigrationsBefore);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demand_audit_event", Integer.class))
        .isGreaterThanOrEqualTo(7);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_audit_event", Integer.class))
        .isGreaterThanOrEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_resume_version", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM talent_process_event", Integer.class))
        .isGreaterThanOrEqualTo(6);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demand_jd_fact_snapshot", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_resume_fact_snapshot", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_fact_correction", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matching_match_run WHERE run_purpose='CUSTOM'", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_match_run WHERE run_purpose='CUSTOM' AND status='SUCCEEDED'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_match_run WHERE run_purpose='CUSTOM' AND status='CANCELLED'", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_match_run_item_outcome o JOIN matching_match_run r ON r.id=o.match_run_id WHERE r.run_purpose='CUSTOM' AND o.status='SUCCEEDED'",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_candidate_engagement_lock_history",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_application", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_workflow_path_change_record", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_workflow_follow_up_record", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_decision_record", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_idempotency_record", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_application_workflow_step", Integer.class))
        .isEqualTo(7);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recruitment_application_workflow_step WHERE"
                    + " status='NOT_SELECTED'",
                Integer.class))
        .isEqualTo(4);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interview_interview", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_feedback_version", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_notification_attempt WHERE status='FAILED'",
                Integer.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_client_submission", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_send_snapshot", Integer.class))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matching_weighted_result w JOIN matching_match_run r ON r.id=w.match_run_id WHERE r.run_purpose='CUSTOM'", Integer.class))
        .isEqualTo(2);
    // 主招聘简历已人工更正，AI不得覆盖；解析重领使用另一份真实确认且未人工更正的版本。
    String parseRecoveryResumeVersionId = jdbcTemplate.queryForObject(
        "SELECT resume_version_id FROM talent_resume_intake WHERE id=?", String.class, secondIntakeId);
    new ParseTerminalExternalScenario(jdbcTemplate, asyncTasks, ids, resumeParsing, jdParsing,
        taskBatchRunner, rawArchive, documentExtraction, taskPersistence).verify(parseRecoveryResumeVersionId, jdVersionId, adminUserId);
    new JdParsingCancellationExternalScenario(jdbcTemplate, asyncTasks, ids, jdParsing,
        jdParseModel, rawArchive, taskPersistence).verify(jdVersionId, adminUserId);
    new MatchRunDispatchExternalScenario(jdbcTemplate, asyncTasks, ids, matchRunPersistence,
        matchRuns, taskBatchRunner, matchAttemptPersistence, transactions).verify(matchRunId);
    new MatchRunLeaseExternalScenario(jdbcTemplate, asyncTasks, ids, matchRunPersistence,
        matchAttemptPersistence, matchRuns, matchingEngine, taskPersistence).verify(matchRunId);
    new MatchRunTerminalExternalScenario(jdbcTemplate, asyncTasks, ids, matchRunPersistence,
        matchAttemptPersistence, mockMvc, activeSession, adminToken, transactions).verify(matchRunId);
    for (String scenario : List.of("details-update-first", "withdraw-before-details-update", "cancel-first", "withdraw-before-cancel",
        "reschedule-update-first", "withdraw-before-reschedule-update",
        "feedback-first", "withdraw-before-feedback", "feedback-replay",
        "withdraw-before-notification", "notification-first", "notification-failure",
        "feedback-limits", "bound", "legacy", "withdraw-first", "reschedule-first",
        "create-first", "withdraw-before-create", "completed-page")) {
      new SubmissionWithdrawalExternalScenario(mockMvc, jdbcTemplate, objectMapper, activeSession, adminToken, scenario, notificationDelivery)
          .verify(candidateId, jdVersionId, resumeVersionId, adminUserId);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "HR_IDENTITY_CREDENTIAL_IT", matches = "true")
  void createsAndResetsRandomCredentialsWithRealMySqlSessions() throws Exception {
    Cookie[] adminSession = loginAndChangePassword(
        "admin-it@example.com", "Test-Only-Admin-123!", "Changed-Admin-456");
    JsonNode adminToken = body(mockMvc.perform(get("/api/v1/auth/csrf")
        .cookie(adminSession)).andReturn());
    MvcResult department = mockMvc.perform(post("/api/v1/admin/departments")
            .cookie(adminSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"RANDOM_CREDENTIAL_IT\",\"name\":\"随机口令测试部\"}"))
        .andExpect(status().isCreated()).andReturn();
    String departmentId = id(department);
    String email = "recruiter-credential-it@example.com";
    MvcResult created = mockMvc.perform(post("/api/v1/admin/users")
            .cookie(adminSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "displayName", "随机口令测试账号", "workEmail", " Recruiter-Credential-IT@Example.COM ",
                "homeDepartmentId", departmentId, "roles", List.of("RECRUITER"),
                "reason", "随机口令集成测试"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.oneTimeCredential").isString())
        .andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
    String userId = body(created).get("userId").asString();
    String initialCredential = body(created).get("oneTimeCredential").asString();
    assertRandomCredential(initialCredential);
    assertThat(initialCredential.equals(email)).isFalse();
    String initialHash = jdbcTemplate.queryForObject(
        "SELECT credential_hash FROM identity_user_account WHERE normalized_email = ?",
        String.class, email);
    assertThat(initialHash).startsWith("{bcrypt}");
    assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(initialCredential, initialHash)).isTrue();
    assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(email, initialHash)).isFalse();
    assertEmailIsNotPassword(email);

    Cookie[] userSession = loginAndChangePassword(email, initialCredential, "Changed-User-123!");
    mockMvc.perform(get("/api/v1/me").cookie(userSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));
    MvcResult reset = mockMvc.perform(post("/api/v1/admin/users/{userId}/credentials/reset", userId)
            .cookie(adminSession)
            .header(adminToken.get("headerName").asString(), adminToken.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"管理员手工重置\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oneTimeCredential").isString())
        .andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
    String resetCredential = body(reset).get("oneTimeCredential").asString();
    assertRandomCredential(resetCredential);
    assertThat(resetCredential.equals(initialCredential)).isFalse();
    assertEmailIsNotPassword(email);
    mockMvc.perform(get("/api/v1/me").cookie(userSession))
        .andExpect(status().isUnauthorized());
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    JsonNode token = body(csrf);
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
            .cookie(csrf.getResponse().getCookies())
            .header(token.get("headerName").asString(), token.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("workEmail", email, "password", resetCredential))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
    mockMvc.perform(get("/api/v1/admin/users").cookie(login.getResponse().getCookies()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    Cookie[] changedSession = loginAndChangePassword(email, resetCredential, "Changed-Again-789!");
    mockMvc.perform(get("/api/v1/me").cookie(changedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  private static void assertRandomCredential(String credential) {
    assertThat(credential != null && credential.matches("[A-Za-z0-9_-]{32}"))
        .as("临时口令必须是32字符安全随机值").isTrue();
  }

  private void assertEmailIsNotPassword(String email) throws Exception {
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    JsonNode token = body(csrf);
    mockMvc.perform(post("/api/v1/auth/login")
        .cookie(csrf.getResponse().getCookies())
        .header(token.get("headerName").asString(), token.get("token").asString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("workEmail", email, "password", email))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
  }

  private Cookie[] loginAndChangePassword(String email, String initial, String password)
      throws Exception {
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    JsonNode token = body(csrf);
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
        .cookie(csrf.getResponse().getCookies())
        .header(token.get("headerName").asString(), token.get("token").asString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("workEmail", email, "password", initial))))
        .andExpect(status().isOk()).andReturn();
    Cookie[] session = login.getResponse().getCookies();
    token = body(mockMvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn());
    mockMvc.perform(post("/api/v1/me/password").cookie(session)
        .header(token.get("headerName").asString(), token.get("token").asString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("currentPassword", initial, "newPassword", password))))
        .andExpect(status().isOk());
    csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    token = body(csrf);
    login = mockMvc.perform(post("/api/v1/auth/login")
        .cookie(csrf.getResponse().getCookies())
        .header(token.get("headerName").asString(), token.get("token").asString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("workEmail", email, "password", password))))
        .andExpect(status().isOk()).andReturn();
    return login.getResponse().getCookies();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private String id(MvcResult result) throws Exception {
    return body(result).get("id").asString();
  }

  private static JsonNode workflowStep(JsonNode details, String code) {
    for (JsonNode step : details.get("workflowSteps")) {
      if (code.equals(step.get("stepCode").asString())) return step;
    }
    throw new AssertionError("招聘活动缺少预期步骤 " + code);
  }

  private static byte[] resumePdf(String text) throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(48, 740);
        stream.showText(text);
        stream.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AiTestConfiguration {
    @Bean
    @Primary
    ProfessionalScoringModelPort professionalScoringModelPort() {
      return new SyntheticProfessionalScoringModel();
    }

    @Bean
    @Primary
    ControlledNotificationDelivery notificationDelivery() {
      return new ControlledNotificationDelivery();
    }

    @Bean
    @Primary
    ResumeParseModelPort resumeParseModelPort() {
      return new ResumeParseModelPort() {
        @Override
        public ModelResult parse(ParseRequest request) {
        String text = request.pages().getFirst().text();
        String skill = text.contains("Java") ? "Java" : "Spring";
        String name = "Synthetic Candidate";
        String email = text.contains("candidate@example.com")
            ? "candidate@example.com" : "another@example.com";
        int skillStart = text.indexOf(skill);
        int nameStart = text.indexOf(name);
        int emailStart = text.indexOf(email);
        assertThat(List.of(skillStart, nameStart, emailStart))
            .as("合成模型身份与技能证据必须来自当前简历文本").allMatch(offset -> offset >= 0);
        String raw =
            """
            {"candidate":{"fullName":"%s","email":"%s","phone":null,"currentRole":null,"city":null,"age":null,"gender":null,"jobIntention":null},
             "workExperiences":[],"projectExperiences":[],"educations":[],
             "skills":[{"id":"skill_1","name":"%s","level":null,"years":null}],
             "credentials":[],"languages":[],"industryTags":[],"responsibilityTags":[],"unknownItems":[],
             "evidence":[
              {"jsonPointer":"/candidate/fullName","pageNo":1,"startOffset":%d,"endOffset":%d,"quote":"%s","boundingBox":[]},
              {"jsonPointer":"/candidate/email","pageNo":1,"startOffset":%d,"endOffset":%d,"quote":"%s","boundingBox":[]},
              {"jsonPointer":"/skills/0/name","pageNo":1,"startOffset":%d,"endOffset":%d,"quote":"%s","boundingBox":[]}]}
            """
                .formatted(name, email, skill,
                    nameStart, nameStart + name.length(), name,
                    emailStart, emailStart + email.length(), email,
                    skillStart, skillStart + skill.length(), skill);
        return new ResumeParseModelPort.ModelResult("synthetic-it", "resume-v1", raw, 100, 50, 10);
        }
      };
    }

    @Bean
    @Primary
    JdParseModelPort jdParseModelPort() {
      return new JdParseModelPort() {
        @Override
        public ModelResult parse(ParseRequest request) {
        String text = request.rawText();
        int skillStart = text.indexOf("客户支持");
        int responsibilityStart = text.indexOf("故障定位");
        int educationStart = text.indexOf("本科");
        int locationStart = text.indexOf("客户现场");
        assertThat(List.of(skillStart, responsibilityStart, educationStart, locationStart))
            .as("合成 JD 的每项事实都必须有原文证据").allMatch(offset -> offset >= 0);
        String raw =
            """
            {"title":"售后工程师","summary":"负责客户现场支持","professional":{"coreSkills":[{"id":"skill_1","name":"客户支持","required":true,"aliases":[]}],"roleExperiences":[],"minimumYears":null,"preferredYears":null,"responsibilities":["故障定位"],"industryTags":[],"projectTags":[]},
             "controlledConditions":{"ageMin":null,"ageMax":null,"sex":"ANY","educations":["本科"],"credentials":[],"languages":[]},
             "fulfillmentConditions":{"locations":["客户现场"],"availability":null,"salaryMin":null,"salaryMax":null,"salaryPeriod":null},
             "evidence":[
              {"jsonPointer":"/professional/coreSkills/0/name","startOffset":%d,"endOffset":%d,"quote":"客户支持"},
              {"jsonPointer":"/professional/responsibilities/0","startOffset":%d,"endOffset":%d,"quote":"故障定位"},
              {"jsonPointer":"/controlledConditions/educations/0","startOffset":%d,"endOffset":%d,"quote":"本科"},
              {"jsonPointer":"/fulfillmentConditions/locations/0","startOffset":%d,"endOffset":%d,"quote":"客户现场"}]}
            """
                .formatted(skillStart, skillStart + "客户支持".length(),
                    responsibilityStart, responsibilityStart + "故障定位".length(),
                    educationStart, educationStart + "本科".length(),
                    locationStart, locationStart + "客户现场".length());
        return new JdParseModelPort.ModelResult("synthetic-it", "jd-v1", raw, 80, 40, 8);
        }
      };
    }
  }
}
