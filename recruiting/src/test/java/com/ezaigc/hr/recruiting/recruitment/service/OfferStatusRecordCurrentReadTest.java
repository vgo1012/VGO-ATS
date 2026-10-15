package com.ezaigc.hr.recruiting.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.mapper.OfferStatusRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.OfferStatusRecordPersistenceServiceImpl;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
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

class OfferStatusRecordCurrentReadTest {
  @Test
  void actualServiceMapperBindsApplicationAndRecordBeforeCurrentLock() throws Exception {
    RecordingConfiguration configuration = new RecordingConfiguration();
    String applicationId = "app' OR '1'='1";
    String recordId = "record' OR '1'='1";
    OfferStatusRecord record = offer(applicationId, recordId);
    configuration.currentRows = List.of(record);
    try (SqlSession session = configuration.factory.openSession()) {
      OfferStatusRecordPersistenceServiceImpl service = service(session);
      assertThat(service.findOfferForUpdate(applicationId, recordId)).isSameAs(record);
      assertThat(configuration.sql).hasSize(1);
      assertThat(configuration.sql.getFirst()).contains("FROM recruitment_offer_status_record",
          "WHERE (application_id = ? AND id = ?)", "record_type", "status", "occurred_at",
          "operated_by_user_id", "create_time").endsWith("FOR UPDATE")
          .doesNotContain(applicationId, recordId, "SELECT *");
      assertThat(configuration.parameters.getFirst()).containsExactly(applicationId, recordId);
      assertThat(configuration.statementIds).containsExactly(
          OfferStatusRecordMapper.class.getName() + ".selectWithCursor");
      assertThat(configuration.executors).hasSize(2);
      assertThat(configuration.executors.getFirst().isClosed()).isFalse();
      assertThat(configuration.cursorExecutors.getFirst().isClosed()).isTrue();
      verify(configuration.cursors.getFirst(), atLeastOnce()).close();
      verifyNoInteractions(configuration.source);
    }
  }

  @Test
  void actualGetOneUsesFreshClosedCursorSessionsForOrdinaryAndCurrentReads() throws Exception {
    RecordingConfiguration configuration = new RecordingConfiguration();
    assertThat(configuration.getLocalCacheScope()).isEqualTo(LocalCacheScope.SESSION);
    assertThat(configuration.factory.getConfiguration()).isSameAs(configuration);
    String applicationId = "P".repeat(26);
    String recordId = "I".repeat(26);
    OfferStatusRecord committed = offer(applicationId, recordId);
    try (SqlSession session = configuration.factory.openSession()) {
      OfferStatusRecordPersistenceServiceImpl service = service(session);
      assertThat(service.findOffer(applicationId, recordId)).isNull();
      configuration.currentRows = List.of(committed);
      assertThat(service.findOffer(applicationId, recordId)).isNull();
      assertThat(service.findOfferForUpdate(applicationId, recordId)).isSameAs(committed);
      assertThat(service.findOfferForUpdate(applicationId, recordId)).isSameAs(committed);
      assertThat(service.findOffer(applicationId, recordId)).isNull();
      assertThat(configuration.sql).hasSize(5);
      assertThat(configuration.sql.get(0)).doesNotContain("FOR UPDATE");
      assertThat(configuration.sql.get(1)).isEqualTo(configuration.sql.get(0));
      assertThat(configuration.sql.get(2)).endsWith("FOR UPDATE");
      assertThat(configuration.sql.get(3)).isEqualTo(configuration.sql.get(2));
      assertThat(configuration.sql.get(4)).isEqualTo(configuration.sql.get(0));
      assertThat(configuration.parameters).hasSize(5).allSatisfy(
          values -> assertThat(values).containsExactly(applicationId, recordId));
      assertThat(configuration.statementIds).hasSize(5).allSatisfy(
          id -> assertThat(id).isEqualTo(OfferStatusRecordMapper.class.getName() + ".selectWithCursor"));
      assertThat(configuration.executors).hasSize(6);
      assertThat(configuration.executors.getFirst().isClosed()).isFalse();
      assertThat(configuration.cursorExecutors).hasSize(5).doesNotHaveDuplicates().allSatisfy(
          executor -> assertThat(executor.isClosed()).isTrue());
      for (Cursor<?> cursor : configuration.cursors) {
        verify(cursor, atLeastOnce()).close();
      }
      verifyNoInteractions(configuration.source);
      // 3.5.17 getOne 经真实工厂打开新会话并查询游标，不使用 query 的 SESSION 缓存。
      // 旧普通视图和已提交事实仅由物理 I/O 替身建模，不证明 MySQL 隔离或 Spring 连接绑定。
    }
  }

  private static OfferStatusRecordPersistenceServiceImpl service(SqlSession session) {
    OfferStatusRecordPersistenceServiceImpl service = new OfferStatusRecordPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(OfferStatusRecordMapper.class));
    return service;
  }

  private static OfferStatusRecord offer(String applicationId, String recordId) {
    OfferStatusRecord record = new OfferStatusRecord();
    record.setId(recordId);
    record.setApplicationId(applicationId);
    record.setRecordType("STATUS");
    record.setStatus("ISSUED");
    return record;
  }

  /** 工厂、会话、mapper 和游标调用链使用真实实现；仅执行器的物理 I/O 被替代。 */
  private static final class RecordingConfiguration extends MybatisConfiguration {
    private final DataSource source = mock(DataSource.class);
    private final SqlSessionFactory factory;
    private List<OfferStatusRecord> currentRows = List.of();
    private final List<String> sql = new ArrayList<>();
    private final List<List<Object>> parameters = new ArrayList<>();
    private final List<String> statementIds = new ArrayList<>();
    private final List<RecordingExecutor> executors = new ArrayList<>();
    private final List<RecordingExecutor> cursorExecutors = new ArrayList<>();
    private final List<Cursor<?>> cursors = new ArrayList<>();

    private RecordingConfiguration() {
      setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
      addMapper(OfferStatusRecordMapper.class);
      factory = new DefaultSqlSessionFactory(this);
      GlobalConfigUtils.getGlobalConfig(this).setSqlSessionFactory(factory);
    }

    @Override
    public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
      RecordingExecutor executor = new RecordingExecutor(this, transaction);
      executors.add(executor);
      return executor;
    }
  }

  @SuppressWarnings("rawtypes")
  private static final class RecordingExecutor extends BaseExecutor {
    private final RecordingConfiguration recording;

    private RecordingExecutor(RecordingConfiguration recording, Transaction transaction) {
      super(recording, transaction);
      this.recording = recording;
    }

    @Override
    protected <E> List<E> doQuery(MappedStatement statement, Object parameter, RowBounds bounds,
        ResultHandler resultHandler, BoundSql boundSql) {
      throw new AssertionError("getOne 应通过实际 selectWithCursor 路径读取");
    }

    @Override
    protected int doUpdate(MappedStatement statement, Object parameter) {
      throw new AssertionError("查询测试不得执行写入");
    }

    @Override
    protected List<BatchResult> doFlushStatements(boolean rollback) {
      return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E> Cursor<E> doQueryCursor(MappedStatement statement, Object parameter,
        RowBounds bounds, BoundSql boundSql) {
      String sql = boundSql.getSql().replaceAll("\\s+", " ").strip();
      recording.sql.add(sql);
      recording.statementIds.add(statement.getId());
      recording.cursorExecutors.add(this);
      List<Object> values = new ArrayList<>();
      for (ParameterMapping mapping : boundSql.getParameterMappings()) {
        String property = mapping.getProperty();
        values.add(boundSql.hasAdditionalParameter(property) ? boundSql.getAdditionalParameter(property)
            : configuration.newMetaObject(parameter).getValue(property));
      }
      recording.parameters.add(values);
      List<E> rows = (List<E>) (sql.endsWith("FOR UPDATE") ? recording.currentRows : List.of());
      Cursor<E> cursor = mock(Cursor.class);
      when(cursor.iterator()).thenReturn(rows.iterator());
      recording.cursors.add(cursor);
      return cursor;
    }
  }
}
