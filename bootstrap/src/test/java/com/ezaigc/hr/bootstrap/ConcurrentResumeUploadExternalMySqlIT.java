package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.enums.IntakeSourceType;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.service.IntakeBatchPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用小连接池验证慢文件扫描期间不会占用数据库连接。 */
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
      "spring.datasource.hikari.connection-timeout=1000",
      "hr.object-storage.mode=local",
      "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
      "hr.search.mode=memory",
      "hr.malware-scan.mode=development",
      "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
      "hr.bootstrap-admin.enabled=true",
      "hr.bootstrap-admin.department-code=ROOT",
      "hr.bootstrap-admin.department-name=并发上传测试总部",
      "hr.bootstrap-admin.display-name=并发上传测试管理员",
      "hr.bootstrap-admin.work-email=concurrent-upload-it@example.com",
      "hr.bootstrap-admin.password=Test-Only-Concurrent-Upload-123!",
      "hr.resume-parsing.worker-enabled=false",
      "hr.resume-intake.worker-enabled=false",
      "hr.jd-parsing.worker-enabled=false",
      "hr.matching.worker-enabled=false",
      "hr.search.worker-enabled=false",
      "hr.messaging.worker-enabled=false",
      "hr.reporting.audit-capture.enabled=false"
    })
class ConcurrentResumeUploadExternalMySqlIT {

  private static final int UPLOAD_COUNT = 5;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformIdGenerator ids;
  @Autowired private TalentIntakeApplicationManager manager;
  @Autowired private SourceChannelPersistenceService channels;
  @Autowired private IntakeBatchPersistenceService batches;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private CurrentActorProvider actorProvider;
  @MockitoBean private MalwareScanPort scanner;
  @MockitoBean private ObjectStoragePort storage;

  private String batchId;

  @BeforeEach
  void setUp() throws Exception {
    String userId =
        jdbc.queryForObject(
            "SELECT id FROM identity_user_account WHERE normalized_email = ?",
            String.class,
            "concurrent-upload-it@example.com");
    String departmentId =
        jdbc.queryForObject(
            "SELECT home_department_id FROM identity_user_account WHERE id = ?",
            String.class,
            userId);
    when(actorProvider.currentActor())
        .thenReturn(Optional.of(new CurrentActor(userId, Set.of("talent.manage"), true)));

    String channelId = ids.newId();
    batchId = ids.newId();
    transactions.executeWithoutResult(
        ignored -> {
          assertThat(
                  channels.insertChannel(
                      channelId,
                      "CONCURRENT-UPLOAD-IT",
                      "并发上传测试渠道",
                      IntakeSourceType.MANUAL.name(),
                      userId,
                      Instant.now()))
              .isOne();
          assertThat(
                  batches.insertBatch(
                      batchId, channelId, userId, userId, departmentId, null, Instant.now()))
              .isOne();
        });

    CyclicBarrier allUploadsScanning = new CyclicBarrier(UPLOAD_COUNT);
    when(scanner.scan(anyString(), any()))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              allUploadsScanning.await(3, TimeUnit.SECONDS);
              return MalwareScanVerdict.CLEAN;
            });
    when(storage.exists(anyString()))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return false;
            });
    doAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return null;
            })
        .when(storage)
        .put(anyString(), any());
  }

  @Test
  void fiveConcurrentUploadsFinishWithAFourConnectionPool() throws Exception {
    List<Future<ResumeIntakeResponse>> uploads = new ArrayList<>();
    try (var pool = Executors.newFixedThreadPool(UPLOAD_COUNT)) {
      for (int index = 0; index < UPLOAD_COUNT; index++) {
        int uploadIndex = index;
        uploads.add(
            pool.submit(
                () ->
                    manager.uploadResponse(
                        batchId,
                        upload(uploadIndex),
                        "concurrent-upload-" + uploadIndex)));
      }
      for (Future<ResumeIntakeResponse> upload : uploads) {
        assertThat(upload.get(10, TimeUnit.SECONDS).getStatus())
            .isEqualTo(ResumeIntakeStatus.PENDING_IMPORT.getCode());
      }
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM talent_resume_intake WHERE batch_id = ?",
                Integer.class,
                batchId))
        .isEqualTo(UPLOAD_COUNT);
  }

  private static ResumeUploadDTO upload(int index) {
    byte[] content =
        ("%PDF-concurrent-upload-" + index).getBytes(StandardCharsets.UTF_8);
    return new ResumeUploadDTO(
        "candidate-" + index + ".pdf",
        content.length,
        () -> new ByteArrayInputStream(content));
  }
}
