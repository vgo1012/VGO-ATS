package com.ezaigc.hr.recruiting.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationCollaboratorMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.ApplicationCollaboratorPersistenceServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationCollaboratorCurrentReadTest {
  @Test
  void actualServiceMapperBindsApplicationAndKeepsUserOrderBeforeCurrentLock() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ApplicationCollaboratorMapper.class);
    RecordingExecutor executor = new RecordingExecutor(configuration);
    String applicationId = "app' OR '1'='1";
    executor.rows = List.of(collaborator(applicationId, "member"));
    try (DefaultSqlSession session = new DefaultSqlSession(configuration, executor)) {
      ApplicationCollaboratorPersistenceServiceImpl service = service(session);
      assertThat(service.findCollaboratorsForUpdate(applicationId)).containsExactly("member");
      assertThat(executor.sql).hasSize(1);
      assertThat(executor.sql.getFirst()).contains("FROM recruitment_application_collaborator",
          "WHERE (application_id = ?)").endsWith("ORDER BY user_id ASC FOR UPDATE")
          .doesNotContain(applicationId, "SELECT *");
      assertThat(executor.parameters.getFirst()).containsExactly(applicationId);
      assertThat(executor.statementIds).containsExactly(ApplicationCollaboratorMapper.class.getName() + ".selectList");
    }
  }

  @Test
  void sameRealSessionSeparatesOrdinaryAndCurrentCacheKeysWithoutPretendingToAlwaysFlush() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ApplicationCollaboratorMapper.class);
    assertThat(configuration.getLocalCacheScope()).isEqualTo(LocalCacheScope.SESSION);
    RecordingExecutor executor = new RecordingExecutor(configuration);
    String applicationId = "P".repeat(26);
    executor.rows = List.of(collaborator(applicationId, "old-member"));
    try (DefaultSqlSession session = new DefaultSqlSession(configuration, executor)) {
      ApplicationCollaboratorPersistenceServiceImpl service = service(session);
      assertThat(service.findCollaborators(applicationId)).containsExactly("old-member");
      executor.rows = List.of(collaborator(applicationId, "current-member"));
      assertThat(service.findCollaborators(applicationId)).containsExactly("old-member");
      assertThat(executor.sql).hasSize(1);
      assertThat(service.findCollaboratorsForUpdate(applicationId)).containsExactly("current-member");
      assertThat(service.findCollaboratorsForUpdate(applicationId)).containsExactly("current-member");
      assertThat(executor.sql).hasSize(2);
      assertThat(executor.sql.getFirst()).endsWith("ORDER BY user_id ASC").doesNotContain("FOR UPDATE");
      assertThat(executor.sql.getLast()).endsWith("ORDER BY user_id ASC FOR UPDATE");
      assertThat(executor.statementIds).containsExactly(
          ApplicationCollaboratorMapper.class.getName() + ".selectList",
          ApplicationCollaboratorMapper.class.getName() + ".selectList");
      assertThat(executor.keys.getFirst()).isNotEqualTo(executor.keys.getLast());
      assertThat(executor.parameters).containsExactly(List.of(applicationId), List.of(applicationId));
      // 同current SQL重复读取仍命中SESSION cache；业务helper已持app锁且只读一次。
      // 本测试运行真实mapper/session/cache，不证明真实MySQL RR隔离或数据库行锁。
    }
  }

  private static ApplicationCollaboratorPersistenceServiceImpl service(DefaultSqlSession session) {
    ApplicationCollaboratorPersistenceServiceImpl service = new ApplicationCollaboratorPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", session.getMapper(ApplicationCollaboratorMapper.class));
    return service;
  }

  private static ApplicationCollaborator collaborator(String applicationId, String userId) {
    ApplicationCollaborator collaborator = new ApplicationCollaborator();
    collaborator.setApplicationId(applicationId);
    collaborator.setUserId(userId);
    return collaborator;
  }

  /** 仅替代物理I/O；MappedStatement、BoundSql、mapper代理和BaseExecutor缓存均为实际实现。 */
  @SuppressWarnings("rawtypes")
  private static final class RecordingExecutor extends BaseExecutor {
    private List<ApplicationCollaborator> rows = List.of();
    private final List<String> sql = new ArrayList<>();
    private final List<List<Object>> parameters = new ArrayList<>();
    private final List<String> statementIds = new ArrayList<>();
    private final List<CacheKey> keys = new ArrayList<>();

    private RecordingExecutor(MybatisConfiguration configuration) {
      super(configuration, mock(Transaction.class));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <E> List<E> doQuery(MappedStatement statement, Object parameter, RowBounds bounds,
        ResultHandler resultHandler, BoundSql boundSql) {
      sql.add(boundSql.getSql().replaceAll("\\s+", " ").strip());
      statementIds.add(statement.getId());
      keys.add(createCacheKey(statement, parameter, bounds, boundSql));
      List<Object> values = new ArrayList<>();
      for (ParameterMapping mapping : boundSql.getParameterMappings()) {
        String property = mapping.getProperty();
        values.add(boundSql.hasAdditionalParameter(property) ? boundSql.getAdditionalParameter(property)
            : configuration.newMetaObject(parameter).getValue(property));
      }
      parameters.add(values);
      return (List<E>) rows;
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
    protected <E> Cursor<E> doQueryCursor(MappedStatement statement, Object parameter,
        RowBounds bounds, BoundSql boundSql) {
      throw new AssertionError("此测试不使用游标");
    }
  }
}
