package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.identity.service.impl.IdentityAdministrationPersistenceServiceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityAdministrationBatchReadTest {
  @Test
  void batchSqlPreservesLinkedAccountViewAndDoesNotBroadenEmptyIds() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:directory_batch_" + UUID.randomUUID() + ";MODE=MySQL");
    try (var connection = dataSource.getConnection(); var ddl = connection.createStatement()) {
      ddl.execute("CREATE TABLE identity_department(id VARCHAR(26) PRIMARY KEY,name VARCHAR(128))");
      ddl.execute("""
          CREATE TABLE identity_user_account(id VARCHAR(26) PRIMARY KEY,display_name VARCHAR(128),
            work_email VARCHAR(128),source_title VARCHAR(128),home_department_id VARCHAR(26),
            direct_leader_user_id VARCHAR(26),status VARCHAR(32),must_change_password BOOLEAN,version BIGINT)
          """);
      ddl.execute("INSERT INTO identity_department VALUES('d1','部门')");
      ddl.execute("""
          INSERT INTO identity_user_account VALUES
          ('leader','负责人','leader@example.invalid',NULL,'d1',NULL,'ACTIVE',FALSE,1),
          ('u1','关联用户','user@example.invalid','岗位','d1','leader','DISABLED',TRUE,7),
          ('other','不关联用户',NULL,NULL,'d1',NULL,'ACTIVE',FALSE,1)
          """);
      MybatisConfiguration configuration = new MybatisConfiguration();
      configuration.setEnvironment(new Environment("batch-test", new JdbcTransactionFactory(), dataSource));
      configuration.addMapper(IdentityAdministrationMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        IdentityAdministrationMapper mapper = session.getMapper(IdentityAdministrationMapper.class);
        assertThat(mapper.findUsersByIds(List.of("u1", "missing"))).singleElement().satisfies(user -> {
          assertThat(user.getId()).isEqualTo("u1");
          assertThat(user.getHomeDepartmentName()).isEqualTo("部门");
          assertThat(user.getDirectLeaderName()).isEqualTo("负责人");
          assertThat(user.getStatus()).isEqualTo("DISABLED");
          assertThat(user.getVersion()).isEqualTo(7);
        });
        assertThat(mapper.findUsersByIds(List.of())).isEmpty();
      }
    }
  }

  @Test
  void boundsQuerySizeAndDeduplicatesLargeDirectories() {
    IdentityAdministrationMapper mapper = mock(IdentityAdministrationMapper.class);
    IdentityAdministrationPersistenceServiceImpl service = new IdentityAdministrationPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    assertThat(service.findUsersByIds(List.of())).isEmpty();
    assertThat(service.findUsersByIds(null)).isEmpty();
    verifyNoInteractions(mapper);
    when(mapper.findUsersByIds(any())).thenAnswer(call -> {
      List<String> ids = call.getArgument(0);
      return ids.stream().map(id -> { AdminUserRecord user = new AdminUserRecord(); user.setId(id); return user; }).toList();
    });
    List<String> ids = new ArrayList<>(IntStream.range(0, 1001).mapToObj(i -> "u" + i).toList());
    ids.add("u0"); ids.add(null);
    assertThat(service.findUsersByIds(ids)).hasSize(1001);
    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass((Class) List.class);
    verify(mapper, times(3)).findUsersByIds(batches.capture());
    assertThat(batches.getAllValues()).extracting(List::size).containsExactly(500, 500, 1);
  }
}
