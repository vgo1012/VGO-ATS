package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.id.UlidGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.WecomDepartmentDTO;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.api.WecomMemberDTO;
import com.ezaigc.hr.identity.application.assembler.WecomSyncAssembler;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.domain.event.WecomSyncRequested;
import com.ezaigc.hr.identity.domain.request.WecomSyncRequest;
import com.ezaigc.hr.identity.mapper.WecomDepartmentMapper;
import com.ezaigc.hr.identity.mapper.WecomMemberMapper;
import com.ezaigc.hr.identity.mapper.WecomSyncBatchMapper;
import com.ezaigc.hr.identity.mapper.WecomSyncStateMapper;
import com.ezaigc.hr.identity.service.impl.WecomDepartmentPersistenceServiceImpl;
import com.ezaigc.hr.identity.service.impl.WecomMemberPersistenceServiceImpl;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.impl.WecomSyncBatchPersistenceServiceImpl;
import com.ezaigc.hr.identity.service.impl.WecomSyncStatePersistenceServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.reflection.MetaObject;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class WecomSyncPersistenceTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private Connection schemaConnection;
    private WecomSyncTransactionManager transactions;
    private WecomSyncApplicationManager manager;
    private WecomSyncExecutionManager execution;
    private final WecomDirectoryPort remote = mock(WecomDirectoryPort.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
    private final WecomSyncProperties properties = new WecomSyncProperties();

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wecom_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(dataSource);
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("docs/sql/hr_ai_complete_schema.sql"))) root = root.getParent();
        String baseline = Files.readString(root.resolve("docs/sql/hr_ai_complete_schema.sql"));
        String schema = baseline.substring(baseline.indexOf("CREATE TABLE identity_wecom_sync_state"),
                        baseline.indexOf("CREATE TABLE identity_wecom_account_link"))
                .replace("COLLATE utf8mb4_0900_bin", "")
                .replace("CHARACTER SET ascii COLLATE ascii_bin", "")
                .replaceAll("(?m)^    KEY idx_wecom_batch_history[^\\n]+\\n", "")
                .replaceAll("\\bJSON\\b", "CLOB")
                .replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
        schemaConnection = dataSource.getConnection();
        try (Statement ddl = schemaConnection.createStatement()) {
            for (String statement : schema.split(";")) if (!statement.isBlank()) ddl.execute(statement);
        }
        properties.setEnabled(true);
        actor.setSuperAdmin(true);
        actor.setUserId("admin-test");
        when(authorization.currentActor()).thenReturn(actor);
        when(remote.status()).thenReturn(result("READY", List.of()));
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig())
                .setMetaObjectHandler(new TestAuditFill()));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        configuration.addInterceptor(interceptor);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("isolated-test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(WecomSyncStateMapper.class);
        configuration.addMapper(WecomSyncBatchMapper.class);
        configuration.addMapper(WecomDepartmentMapper.class);
        configuration.addMapper(WecomMemberMapper.class);
        SqlSessionTemplate sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class, WecomSyncTransactionManager.class, WecomDirectoryPublishManager.class,
                WecomSyncApplicationManager.class, WecomSyncExecutionManager.class, WecomSyncAssembler.class,
                WecomSyncStatePersistenceServiceImpl.class, WecomSyncBatchPersistenceServiceImpl.class,
                WecomDepartmentPersistenceServiceImpl.class, WecomMemberPersistenceServiceImpl.class, CommitListener.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(WecomSyncStateMapper.class, () -> sessions.getMapper(WecomSyncStateMapper.class));
        context.registerBean(WecomSyncBatchMapper.class, () -> sessions.getMapper(WecomSyncBatchMapper.class));
        context.registerBean(WecomDepartmentMapper.class, () -> sessions.getMapper(WecomDepartmentMapper.class));
        context.registerBean(WecomMemberMapper.class, () -> sessions.getMapper(WecomMemberMapper.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(WecomSyncProperties.class, () -> properties);
        context.registerBean(AuthorizationService.class, () -> authorization);
        context.registerBean(WecomAccountLinkPersistenceService.class,
                () -> mock(WecomAccountLinkPersistenceService.class));
        context.registerBean(WecomDepartmentLinkPersistenceService.class,
                () -> mock(WecomDepartmentLinkPersistenceService.class));
        context.registerBean(IdentityAdministrationPersistenceService.class,
                () -> mock(IdentityAdministrationPersistenceService.class));
        context.registerBean(WecomDirectoryPort.class, () -> remote);
        context.registerBean(Clock.class, Clock::systemUTC);
        context.registerBean(PlatformIdGenerator.class, () -> new UlidGenerator(Clock.systemUTC()));
        context.refresh();
        transactions = context.getBean(WecomSyncTransactionManager.class);
        manager = context.getBean(WecomSyncApplicationManager.class);
        execution = context.getBean(WecomSyncExecutionManager.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
        if (schemaConnection != null) schemaConnection.close();
    }

    @Test
    void publishesStableIdentitiesRelationsAndOnlyChangedRows() {
        WecomSyncBatchEntity first = run(List.of(member("member", "旧名称", List.of("leader-outside"))));
        var stored = manager.stored();
        assertThat(stored.getPublishedBatchId()).isEqualTo(first.getId());
        assertThat(stored.getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getDepartmentIds()).containsExactly(2L, 99L);
            assertThat(member.getDirectLeaderUserIds()).containsExactly("leader-outside");
        });
        String identity = stored.getMembers().getFirst().getId();
        assertThat(manager.batch(first.getId()).getInsertedCount()).isEqualTo(2);
        jdbc.update("UPDATE identity_wecom_member SET department_ids_json=?, direct_leader_ids_json=? WHERE id=?",
                "[2, 99]", "[ \"leader-outside\" ]", identity);
        WecomSyncBatchEntity repeated = run(List.of(member("member", "旧名称", List.of("leader-outside"))));
        assertThat(manager.batch(repeated.getId()).getChangedCount()).isZero();
        assertThat(manager.batch(repeated.getId()).getInsertedCount()).isZero();
        assertThat(manager.stored().getMembers().getFirst().getChangedBatchId()).isEqualTo(first.getId());
        WecomSyncBatchEntity changed = run(List.of(member("member", "新名称", null)));
        assertThat(manager.batch(changed.getId()).getChangedCount()).isEqualTo(1);
        assertThat(manager.stored().getMembers().getFirst().getId()).isEqualTo(identity);
        assertThat(manager.stored().getMembers().getFirst().getDirectLeaderUserIds()).containsExactly("leader-outside");
        run(List.of(member("member", "新名称", List.of())));
        assertThat(manager.stored().getMembers().getFirst().getDirectLeaderUserIds()).isEmpty();
    }

    @Test
    void duplicateMembersAreRejectedBeforePublishingAndRetainLastSuccessfulDirectory() {
        var first = run(List.of(member("member", "原名称", null)));
        var duplicate = run(List.of(member("member", "已改名", List.of()),
                member("member", "重复名称", List.of("leader-outside"))));

        assertThat(manager.batch(duplicate.getId()).getStatus()).isEqualTo("FAILED");
        assertThat(manager.batch(duplicate.getId()).getErrorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(manager.stored().getPublishedBatchId()).isEqualTo(first.getId());
        assertThat(manager.stored().getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getName()).isEqualTo("原名称");
            assertThat(member.getChangedBatchId()).isEqualTo(first.getId());
            assertThat(member.getDirectLeaderUserIds()).isNull();
        });
        assertThat(count("identity_wecom_department")).isEqualTo(1);
        assertThat(count("identity_wecom_member")).isEqualTo(1);
    }

    @Test
    void actualSqlFailureRollsBackAllRowsAndKeepsPublishedSnapshot() {
        WecomSyncBatchEntity first = run(List.of(member("member", "原名称", null)));
        jdbc.execute("ALTER TABLE identity_wecom_member ADD CONSTRAINT test_failure CHECK(name <> 'force-db-failure')");
        allowNext();
        WecomSyncBatchEntity batch = submit();
        when(remote.preview()).thenReturn(result("PREVIEW_READY", List.of(member("member", "已改名", null),
                member("new-member", "force-db-failure", null))));
        execution.execute("corp-test");
        assertThat(manager.batch(batch.getId()).getStatus()).isEqualTo("FAILED");
        assertThat(manager.batch(batch.getId()).getErrorCode()).isEqualTo("SYNC_UNAVAILABLE");
        assertThat(manager.stored().getPublishedBatchId()).isEqualTo(first.getId());
        assertThat(manager.stored().getMembers()).singleElement().satisfies(member -> assertThat(member.getName()).isEqualTo("原名称"));
    }

    @Test
    void cooldownIdempotencyAndSingleActiveBatchWorkAcrossTransactions() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return transactions.submit("corp-test", "same-key", "MANUAL", "admin", null); });
            var second = pool.submit(() -> { start.await(); return transactions.submit("corp-test", "same-key", "MANUAL", "admin", null); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getId()).isEqualTo(second.get(10, TimeUnit.SECONDS).getId());
        }
        assertThat(count("identity_wecom_sync_batch")).isEqualTo(1);
        assertThatThrownBy(this::submit).isInstanceOf(PlatformException.class);
        var claimed = transactions.claim("corp-test");
        assertThat(transactions.claim("corp-test")).isNull();
        transactions.fail(claimed, "IP_NOT_ALLOWED", "请核对可信IP");
        assertThatThrownBy(this::submit).isInstanceOf(PlatformException.class);
        allowNext();
        var retry = transactions.submit("corp-test", "retry-key", "MANUAL", "admin", claimed.getId());
        assertThat(retry.getRetryOfBatchId()).isEqualTo(claimed.getId());
        assertThat(count("identity_wecom_sync_batch")).isEqualTo(2);
    }

    @Test
    void expiredLeaseAndLateCompletionCannotReplaceNewBatch() {
        submit();
        WecomSyncBatchEntity old = transactions.claim("corp-test");
        jdbc.update("UPDATE identity_wecom_sync_batch SET lease_until='2000-01-01 00:00:00' WHERE id=?", old.getId());
        assertThat(transactions.claim("corp-test")).isNull();
        assertThat(manager.batch(old.getId()).getStatus()).isEqualTo("INTERRUPTED");
        allowNext();
        var current = submit();
        transactions.complete(old, result("PREVIEW_READY", List.of(member("member", "迟到", null))));
        assertThat(count("identity_wecom_member")).isZero();
        assertThat(transactions.claim("corp-test").getId()).isEqualTo(current.getId());
    }

    @Test
    void remoteFailureNeverPublishesAndRemoteCallIsOutsideTransaction() {
        WecomSyncBatchEntity first = run(List.of(member("member", "成员", null)));
        allowNext();
        var failed = submit();
        when(remote.preview()).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return result("IP_NOT_ALLOWED", List.of());
        });
        execution.execute("corp-test");
        assertThat(manager.batch(failed.getId()).getErrorCode()).isEqualTo("IP_NOT_ALLOWED");
        assertThat(manager.stored().getPublishedBatchId()).isEqualTo(first.getId());
    }

    @Test
    void majorDropAndDepartmentRemovalRetainSnapshotButMinorMissingIsOnlyPending() {
        List<WecomMemberDTO> original = new ArrayList<>();
        for (int index = 0; index < 10; index++) original.add(member("user-" + index, "人员" + index, null));
        var first = run(original);
        var drop = run(original.subList(0, 8));
        assertThat(manager.batch(drop.getId()).getErrorCode()).isEqualTo("MEMBER_DROP");
        assertThat(manager.stored().getPublishedBatchId()).isEqualTo(first.getId());
        var minor = run(original.subList(0, 9));
        assertThat(manager.batch(minor.getId()).getStatus()).isEqualTo("WARNING");
        assertThat(manager.stored().getMembers()).filteredOn(member -> member.getUserId().equals("user-9"))
                .singleElement().satisfies(member -> assertThat(member.getDirectoryStatus()).isEqualTo("PENDING_VERIFICATION"));
        allowNext();
        var scope = submit();
        when(remote.preview()).thenReturn(new WecomDirectoryResultDTO("PREVIEW_READY", "测试", "corp-test", Instant.now(), 0,
                List.of(new WecomDepartmentDTO(99, 0, "另一范围", 0, false)), original));
        execution.execute("corp-test");
        assertThat(manager.batch(scope.getId()).getErrorCode()).isEqualTo("SCOPE_REDUCED");
        assertThat(manager.stored().getPublishedBatchId()).isEqualTo(minor.getId());
    }

    @Test
    void notificationsOnlyFollowCommitAndRollbackLeavesNoBatch() {
        CommitListener listener = context.getBean(CommitListener.class);
        var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        template.executeWithoutResult(status -> {
            submit();
            assertThat(listener.events).isEmpty();
            status.setRollbackOnly();
        });
        assertThat(listener.events).isEmpty();
        assertThat(count("identity_wecom_sync_batch")).isZero();
        submit();
        assertThat(listener.events).hasSize(1);
    }

    @Test
    void adminBoundaryAndDisabledSwitchPreventSubmissionsAndDirectoryDisclosure() {
        actor.setSuperAdmin(false);
        WecomSyncRequest request = new WecomSyncRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        assertThatThrownBy(() -> manager.synchronize(request)).isInstanceOf(PlatformException.class);
        assertThatThrownBy(manager::stored).isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> manager.history(1)).isInstanceOf(PlatformException.class);
        assertThatThrownBy(() -> manager.batch("guessed-id")).isInstanceOf(PlatformException.class);
        actor.setSuperAdmin(true);
        properties.setEnabled(false);
        assertThatThrownBy(() -> manager.synchronize(request)).isInstanceOf(PlatformException.class);
        assertThat(count("identity_wecom_sync_batch")).isZero();
    }

    @Test
    void dailyTriggerIsIdempotentAndRecordsNoHumanActor() {
        manager.scheduleDaily();
        manager.scheduleDaily();
        assertThat(count("identity_wecom_sync_batch")).isEqualTo(1);
        var batch = transactions.claim("corp-test");
        assertThat(batch.getTriggerType()).isEqualTo("SCHEDULED");
        assertThat(batch.getActorUserId()).isNull();
    }

    @Test
    void malformedCrossCorpAndChangedScopeCannotPublish() {
        var batch = submit();
        when(remote.preview()).thenReturn(new WecomDirectoryResultDTO("PREVIEW_READY", "测试", "another-corp", Instant.now(), 0,
                List.of(new WecomDepartmentDTO(2, 1, "人力", 0, true)), List.of(member("user", "测试", null))));
        execution.execute("corp-test");
        assertThat(manager.batch(batch.getId()).getErrorCode()).isEqualTo("INVALID_SNAPSHOT");
        assertThat(count("identity_wecom_department")).isZero();
        allowNext();
        var changed = submit();
        properties.setScopeVersion("changed-during-run");
        when(remote.preview()).thenReturn(result("PREVIEW_READY", List.of(member("user", "测试", null))));
        execution.execute("corp-test");
        assertThat(manager.batch(changed.getId()).getErrorCode()).isEqualTo("SCOPE_VERSION_CHANGED");
        assertThat(count("identity_wecom_member")).isZero();
    }

    @Test
    void persistedScaleSupports465DepartmentsAnd2586StableMembers() {
        List<WecomDepartmentDTO> departments = new ArrayList<>();
        List<WecomMemberDTO> members = new ArrayList<>();
        for (int index = 1; index <= 465; index++) departments.add(new WecomDepartmentDTO(index, 0, "合成部门" + index, index, false));
        for (int index = 0; index < 2586; index++) members.add(new WecomMemberDTO("synthetic-" + index, "合成人员" + index,
                null, null, 1, List.of(1L + index % 465), null));
        var batch = submit();
        when(remote.preview()).thenReturn(new WecomDirectoryResultDTO("PREVIEW_READY", "测试", "corp-test", Instant.now(), 0, departments, members));
        execution.execute("corp-test");
        assertThat(manager.batch(batch.getId()).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(count("identity_wecom_department")).isEqualTo(465);
        assertThat(count("identity_wecom_member")).isEqualTo(2586);
        allowNext();
        var repeated = submit();
        execution.execute("corp-test");
        assertThat(manager.batch(repeated.getId()).getInsertedCount()).isZero();
        assertThat(manager.batch(repeated.getId()).getChangedCount()).isZero();
        assertThat(count("identity_wecom_member")).isEqualTo(2586);
    }

    private WecomSyncBatchEntity run(List<WecomMemberDTO> members) {
        allowNext();
        var batch = submit();
        when(remote.preview()).thenReturn(result("PREVIEW_READY", members));
        execution.execute("corp-test");
        return batch;
    }

    private void allowNext() {
        jdbc.update("UPDATE identity_wecom_sync_state SET next_allowed_at='2000-01-01 00:00:00' WHERE corp_id='corp-test'");
    }

    private WecomSyncBatchEntity submit() {
        return transactions.submit("corp-test", UUID.randomUUID().toString(), "MANUAL", "admin-test", null);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private WecomMemberDTO member(String userId, String name, List<String> leaders) {
        return new WecomMemberDTO(userId, name, "测试职位", null, 1, List.of(2L, 99L), leaders);
    }

    private WecomDirectoryResultDTO result(String status, List<WecomMemberDTO> members) {
        return new WecomDirectoryResultDTO(status, "测试安全摘要", "corp-test", Instant.now(), 0,
                List.of(new WecomDepartmentDTO(2, 1, "人力中心", 0, true)), members);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {}

    static class CommitListener {
        final List<WecomSyncRequested> events = new ArrayList<>();
        @TransactionalEventListener
        public void requested(WecomSyncRequested event) { events.add(event); }
    }

    static class TestAuditFill implements MetaObjectHandler {
        public void insertFill(MetaObject metaObject) {
            strictInsertFill(metaObject, "createTime", Instant.class, Instant.now());
            strictInsertFill(metaObject, "updateTime", Instant.class, Instant.now());
        }
        public void updateFill(MetaObject metaObject) {
            setFieldValByName("updateTime", Instant.now(), metaObject);
        }
    }
}
