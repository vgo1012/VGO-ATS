package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityDirectorySelectionTest {
  private JdbcTemplate jdbc;
  private IdentityAccountPersistenceServiceImpl accounts;

  @BeforeEach
  void setUp() {
    JdbcDataSource source = new JdbcDataSource();
    source.setURL("jdbc:h2:mem:directory_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE identity_user_account (id VARCHAR(26) PRIMARY KEY, "
        + "display_name VARCHAR(100), home_department_id VARCHAR(26), status VARCHAR(20))");
    jdbc.execute("CREATE TABLE identity_role (id VARCHAR(26) PRIMARY KEY, code VARCHAR(40), status VARCHAR(20))");
    jdbc.execute("CREATE TABLE identity_user_role (user_id VARCHAR(26), role_id VARCHAR(26))");
    jdbc.update("INSERT INTO identity_role VALUES ('admin', 'SUPER_ADMIN', 'ACTIVE'), "
        + "('hr', 'RECRUITER', 'ACTIVE'), ('interviewer', 'INTERVIEWER', 'ACTIVE')");
    jdbc.update("INSERT INTO identity_user_account VALUES "
        + "('01', '会议管理员', 'dept', 'ACTIVE'), ('02', '会议面试官', 'dept', 'ACTIVE'), "
        + "('03', '会议招聘员', 'dept', 'ACTIVE'), ('04', '停用成员', 'dept', 'DISABLED')");
    jdbc.update("INSERT INTO identity_user_role VALUES ('01', 'admin'), ('01', 'hr'), "
        + "('02', 'interviewer'), ('03', 'hr'), ('03', 'interviewer'), ('04', 'hr')");
    MybatisConfiguration config = new MybatisConfiguration();
    config.setMapUnderscoreToCamelCase(true);
    config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
    config.addMapper(IdentityAccountMapper.class);
    SqlSessionTemplate sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
    accounts = new IdentityAccountPersistenceServiceImpl();
    ReflectionTestUtils.setField(accounts, "baseMapper", sessions.getMapper(IdentityAccountMapper.class));
  }

  @AfterEach
  void tearDown() {
    jdbc.execute("DROP ALL OBJECTS");
  }

  @Test
  void excludesSystemRoleBeforePaginationEvenWithBusinessRole() {
    assertThat(accounts.findDirectoryUsers(null, null, 1))
        .extracting(UserAccountRecord::getId).containsExactly("02");
    assertThat(accounts.findDirectoryUsers(null, "02", 2))
        .extracting(UserAccountRecord::getId).containsExactly("03");
  }

  @Test
  void searchExcludesAdminsAndDoesNotDuplicateBusinessMembers() {
    assertThat(accounts.findDirectoryUsers("会议", null, 10))
        .extracting(UserAccountRecord::getId).containsExactly("02", "03");
    assertThat(accounts.findDirectoryUsers("管理员", null, 10)).isEmpty();
  }

  @Test
  void revokedOrInactiveSystemRoleDoesNotExcludeBusinessMember() {
    jdbc.update("DELETE FROM identity_user_role WHERE user_id = '01' AND role_id = 'admin'");
    assertThat(accounts.findDirectoryUsers(null, null, 10))
        .extracting(UserAccountRecord::getId).containsExactly("01", "02", "03");
    jdbc.update("INSERT INTO identity_user_role VALUES ('01', 'admin')");
    jdbc.update("UPDATE identity_role SET status = 'DISABLED' WHERE id = 'admin'");
    assertThat(accounts.findDirectoryUsers(null, null, 10))
        .extracting(UserAccountRecord::getId).containsExactly("01", "02", "03");
  }

  @Test
  void historicalReferencesStillResolveSystemAndDisabledAccounts() {
    assertThat(accounts.resolveDirectoryUsers(Set.of("01", "04")))
        .extracting(UserAccountRecord::getId).containsExactly("01", "04");
  }
}
