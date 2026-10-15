package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import java.util.UUID;
import java.util.Map;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 只验证管理链范围行为；MySQL 方言由显式外部测试单独验证。 */
class IdentityReportingManagementScopeTest {
  private JdbcTemplate jdbc;
  private IdentityAccountPersistenceServiceImpl accounts;
  private MybatisConfiguration config;

  @BeforeEach void setUp() {
    var source=new JdbcDataSource();
    source.setURL("jdbc:h2:mem:report_scope_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
    jdbc=new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE identity_user_account(id VARCHAR(26) PRIMARY KEY,"
        +"direct_leader_user_id VARCHAR(26),status VARCHAR(20))");
    jdbc.update("INSERT INTO identity_user_account VALUES ('manager',NULL,'ACTIVE'),"
        +"('active','manager','ACTIVE'),('disabled','manager','DISABLED'),"
        +"('indirect','disabled','ACTIVE'),('outsider',NULL,'ACTIVE'),('other','outsider','ACTIVE')");
    config=new MybatisConfiguration();
    config.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
    config.addMapper(IdentityAccountMapper.class);
    var sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
    accounts=new IdentityAccountPersistenceServiceImpl();
    ReflectionTestUtils.setField(accounts,"baseMapper",sessions.getMapper(IdentityAccountMapper.class));
  }

  @AfterEach void tearDown() { jdbc.execute("DROP ALL OBJECTS"); }

  @Test void historicalReportTraversesInactiveNodesAndExcludesUnrelatedAccounts() {
    assertThat(accounts.findReportingManagedUserIds("manager"))
        .containsExactly("active","disabled","indirect");
    assertThat(config.getMappedStatement(IdentityAccountMapper.class.getName()+".findManagedUserIds")
        .getBoundSql(Map.of("userId","manager")).getSql())
        .contains("status = 'ACTIVE'", "account.status = 'ACTIVE'");
  }

  @Test void aChangedLeaderImmediatelyRemovesTheOldManagersHistoricalScope() {
    jdbc.update("UPDATE identity_user_account SET direct_leader_user_id='outsider' WHERE id='disabled'");
    assertThat(accounts.findReportingManagedUserIds("manager")).containsExactly("active");
    assertThat(accounts.findReportingManagedUserIds("outsider"))
        .containsExactly("disabled","indirect","other");
  }
}
