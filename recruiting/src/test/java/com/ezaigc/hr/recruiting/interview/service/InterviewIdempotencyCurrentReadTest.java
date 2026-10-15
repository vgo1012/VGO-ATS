package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.interview.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewIdempotencyPersistenceServiceImpl;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewIdempotencyCurrentReadTest {
  @Test
  void actualServiceMapperBindsActorAndKeyAndDisablesCachedLockingReads() {
    RecordingConfiguration configuration = new RecordingConfiguration();
    String actor = "actor' OR '1'='1";
    String key = "key' OR '1'='1";
    IdempotencyRecord winner = record(actor, key, "winner");
    configuration.currentRows = List.of(winner);
    try (SqlSession session = configuration.factory.openSession()) {
      assertThat(service(session).findIdempotencyForUpdate(actor, key)).isSameAs(winner);
      assertThat(configuration.sql).hasSize(1);
      assertThat(configuration.sql.getFirst()).contains(
          "SELECT actor_user_id, idempotency_key, action_code, request_fingerprint, target_id,",
          "ext, remark, create_id, create_name, create_time, update_id, update_name, update_time, version",
          "FROM interview_idempotency_record", "WHERE actor_user_id = ? AND idempotency_key = ?")
          .endsWith("FOR UPDATE").doesNotContain(actor, key, "SELECT *");
      assertThat(configuration.parameters).containsExactly(List.of(actor, key));
      assertThat(configuration.statementIds).containsExactly(
          IdempotencyRecordMapper.class.getName() + ".findIdempotencyForUpdate");
      MappedStatement statement = configuration.getMappedStatement(configuration.statementIds.getFirst());
      assertThat(statement.isUseCache()).isFalse();
      assertThat(statement.isFlushCacheRequired()).isTrue();
      assertThat(statement.getResultMaps().getFirst().getType()).isEqualTo(IdempotencyRecord.class);
      assertThat(configuration.executors).hasSize(1);
      assertThat(configuration.cursorExecutors).isEmpty();
      verifyNoInteractions(configuration.source);
    }
  }

  @Test
  void actualFactoryCursorKeepsOrdinarySnapshotWhileCurrentSessionReadsEveryWinner() throws Exception {
    RecordingConfiguration configuration = new RecordingConfiguration();
    assertThat(configuration.getLocalCacheScope()).isEqualTo(LocalCacheScope.SESSION);
    assertThat(configuration.factory.getConfiguration()).isSameAs(configuration);
    String actor = "A".repeat(26), key = "feedback-key";
    IdempotencyRecord first = record(actor, key, "first");
    IdempotencyRecord second = record(actor, key, "second");
    try (SqlSession session = configuration.factory.openSession()) {
      InterviewIdempotencyPersistenceServiceImpl service = service(session);
      assertThat(service.findIdempotency(actor, key)).isNull();
      configuration.currentRows = List.of(first);
      assertThat(service.findIdempotency(actor, key)).isNull();
      assertThat(configuration.sql).hasSize(2);
      assertThat(service.findIdempotencyForUpdate(actor, key)).isSameAs(first);
      configuration.currentRows = List.of(second);
      assertThat(service.findIdempotencyForUpdate(actor, key)).isSameAs(second);
      assertThat(service.findIdempotency(actor, key)).isNull();
      assertThat(configuration.sql).hasSize(5);
      assertThat(configuration.sql.get(0)).doesNotContain("FOR UPDATE");
      assertThat(configuration.sql.get(1)).isEqualTo(configuration.sql.get(0));
      assertThat(configuration.sql.get(2)).endsWith("FOR UPDATE");
      assertThat(configuration.sql.get(3)).isEqualTo(configuration.sql.get(2));
      assertThat(configuration.sql.get(4)).isEqualTo(configuration.sql.get(0));
      assertThat(configuration.parameters).hasSize(5).allSatisfy(
          values -> assertThat(values).containsExactly(actor, key));
      String mapper = IdempotencyRecordMapper.class.getName();
      assertThat(configuration.statementIds).containsExactly(mapper + ".selectWithCursor",
          mapper + ".selectWithCursor", mapper + ".findIdempotencyForUpdate",
          mapper + ".findIdempotencyForUpdate", mapper + ".selectWithCursor");
      assertThat(configuration.executors).hasSize(4);
      assertThat(configuration.executors.getFirst().isClosed()).isFalse();
      assertThat(configuration.currentExecutors).hasSize(2).allSatisfy(
          executor -> assertThat(executor).isSameAs(configuration.executors.getFirst()));
      assertThat(configuration.cursorExecutors).hasSize(3).doesNotHaveDuplicates().allSatisfy(
          executor -> assertThat(executor.isClosed()).isTrue());
      for (Cursor<?> cursor : configuration.cursors) {
        verify(cursor, atLeastOnce()).close();
      }
      verifyNoInteractions(configuration.source);
      // 3.5.17 普通 selectOne 经真实工厂打开新会话并查询游标，普通 miss 来自旧视图 I/O 模型。
      // 当前注解 SQL 始终使用外层同一执行器，重复查询清理 SESSION 缓存后读取新赢家。
      // 不将 SqlSession 等同于 Spring 事务连接，不证明 MySQL RR、行锁或物理回滚。
    }
  }

  @Test
  void actualCurrentReadReturnsNoWinnerWithoutWriting() {
    RecordingConfiguration configuration = new RecordingConfiguration();
    try (SqlSession session = configuration.factory.openSession()) {
      assertThat(service(session).findIdempotencyForUpdate("actor", "absent")).isNull();
      assertThat(configuration.sql).hasSize(1).allSatisfy(sql -> assertThat(sql).endsWith("FOR UPDATE"));
      assertThat(configuration.parameters).containsExactly(List.of("actor", "absent"));
      assertThat(configuration.cursorExecutors).isEmpty();
      verifyNoInteractions(configuration.source);
    }
  }

  private static InterviewIdempotencyPersistenceServiceImpl service(SqlSession session) {
    InterviewIdempotencyPersistenceServiceImpl service = new InterviewIdempotencyPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(IdempotencyRecordMapper.class));
    return service;
  }

  private static IdempotencyRecord record(String actor, String key, String target) {
    return new IdempotencyRecord(actor, key, "INTERVIEW_FEEDBACK_SAVE", "fingerprint", target);
  }

  /** 工厂、会话、Mapper 和缓存使用真实实现，仅执行器物理 I/O 和返回游标被替代。 */
  private static final class RecordingConfiguration extends MybatisConfiguration {
    private final DataSource source = mock(DataSource.class);
    private final SqlSessionFactory factory;
    private List<IdempotencyRecord> currentRows = List.of();
    private final List<String> sql = new ArrayList<>();
    private final List<List<Object>> parameters = new ArrayList<>();
    private final List<String> statementIds = new ArrayList<>();
    private final List<RecordingExecutor> executors = new ArrayList<>();
    private final List<RecordingExecutor> currentExecutors = new ArrayList<>();
    private final List<RecordingExecutor> cursorExecutors = new ArrayList<>();
    private final List<Cursor<?>> cursors = new ArrayList<>();

    private RecordingConfiguration() {
      setMapUnderscoreToCamelCase(true);
      setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
      addMapper(IdempotencyRecordMapper.class);
      factory = new DefaultSqlSessionFactory(this);
      GlobalConfigUtils.getGlobalConfig(this).setSqlSessionFactory(factory);
    }

    @Override
    public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
      RecordingExecutor executor = new RecordingExecutor(this, transaction);
      executors.add(executor);
      return new CachingExecutor(executor);
    }
  }

  @SuppressWarnings("rawtypes")
  private static final class RecordingExecutor extends BaseExecutor {
    private final RecordingConfiguration recording;

    private RecordingExecutor(RecordingConfiguration recording, Transaction transaction) {
      super(recording, transaction);
      this.recording = recording;
    }

    private String record(MappedStatement statement, Object parameter, BoundSql boundSql) {
      String generatedSql = boundSql.getSql().replaceAll("\\s+", " ").strip();
      recording.sql.add(generatedSql);
      recording.statementIds.add(statement.getId());
      List<Object> values = new ArrayList<>();
      for (ParameterMapping mapping : boundSql.getParameterMappings()) {
        String property = mapping.getProperty();
        values.add(boundSql.hasAdditionalParameter(property) ? boundSql.getAdditionalParameter(property)
            : configuration.newMetaObject(parameter).getValue(property));
      }
      recording.parameters.add(values);
      System.out.println("INTERVIEW_KEY_ADAPTER_SQL " + statement.getId() + " " + generatedSql
          + " useCache=" + statement.isUseCache() + " flushCache=" + statement.isFlushCacheRequired());
      return generatedSql;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E> List<E> doQuery(MappedStatement statement, Object parameter, RowBounds bounds,
        ResultHandler resultHandler, BoundSql boundSql) {
      assertThat(record(statement, parameter, boundSql)).endsWith("FOR UPDATE");
      recording.currentExecutors.add(this);
      return (List<E>) recording.currentRows;
    }

    @Override
    protected int doUpdate(MappedStatement statement, Object parameter) {
      throw new AssertionError("幂等查询测试不得执行写入");
    }

    @Override
    protected List<BatchResult> doFlushStatements(boolean rollback) {
      return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E> Cursor<E> doQueryCursor(MappedStatement statement, Object parameter,
        RowBounds bounds, BoundSql boundSql) {
      assertThat(record(statement, parameter, boundSql)).doesNotContain("FOR UPDATE");
      assertThat(statement.getId()).isEqualTo(IdempotencyRecordMapper.class.getName() + ".selectWithCursor");
      recording.cursorExecutors.add(this);
      // 普通旧视图只由物理 I/O 替身建模，不向普通读喂当前赢家。
      List<E> snapshotRows = List.of();
      Cursor<E> cursor = mock(Cursor.class);
      when(cursor.iterator()).thenReturn(snapshotRows.iterator());
      recording.cursors.add(cursor);
      return cursor;
    }
  }
}
