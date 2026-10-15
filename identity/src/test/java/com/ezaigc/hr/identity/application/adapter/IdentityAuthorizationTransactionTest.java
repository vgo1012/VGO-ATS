package com.ezaigc.hr.identity.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.ScopeGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class IdentityAuthorizationTransactionTest {

  private static final String USER_ID = "01JUSER0000000000000000000";

  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final ObjectGrantPersistenceService objectGrants =
      mock(ObjectGrantPersistenceService.class);
  private final ScopeGrantPersistenceService scopeGrants =
      mock(ScopeGrantPersistenceService.class);
  private final UserSupportDepartmentPersistenceService supportDepartments =
      mock(UserSupportDepartmentPersistenceService.class);

  @BeforeEach
  void setUp() {
    when(accounts.findById(USER_ID))
        .thenAnswer(
            ignored -> {
              assertReadOnlyTransaction();
              return activeAccount();
            });
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("RECRUITER"));
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of());
    when(supportDepartments.findSupportDepartmentIds(USER_ID)).thenReturn(List.of());
    when(scopeGrants.findGrantedDepartmentIds(USER_ID)).thenReturn(List.of());
    when(scopeGrants.countCompanyScope(USER_ID)).thenReturn(0);
  }

  @Test
  void requirePermissionLoadsTheAuthorizationSnapshotInOneReadOnlyTransaction() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    AuthorizationService authorization = proxiedAuthorization(transactions);

    authorization.requirePermission("talent.read");

    assertThat(transactions.begun()).isOne();
    assertThat(transactions.committed()).isOne();
  }

  @Test
  void requireAccessLoadsTheSnapshotAndObjectGrantInOneReadOnlyTransaction() {
    when(objectGrants.countObjectGrant(USER_ID, "APPLICATION", "application-1", "talent.read"))
        .thenAnswer(
            ignored -> {
              assertReadOnlyTransaction();
              return 1;
            });
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    AuthorizationService authorization = proxiedAuthorization(transactions);

    authorization.requireAccess(
        "talent.read",
        new AuthorizationTarget(
            "APPLICATION", "application-1", "another-owner", "another-department", Set.of()));

    assertThat(transactions.begun()).isOne();
    assertThat(transactions.committed()).isOne();
  }

  @Test
  void everyAuthorizationOperationDeclaresAReadOnlyTransactionBoundary() throws Exception {
    AnnotationTransactionAttributeSource transactionAttributes =
        new AnnotationTransactionAttributeSource();

    for (var contractMethod : AuthorizationService.class.getDeclaredMethods()) {
      var implementationMethod =
          IdentityAuthorizationAdapter.class.getMethod(
              contractMethod.getName(), contractMethod.getParameterTypes());
      var attribute =
          transactionAttributes.getTransactionAttribute(
              implementationMethod, IdentityAuthorizationAdapter.class);

      assertThat(attribute)
          .as("授权入口 %s 必须声明事务边界", contractMethod.toGenericString())
          .isNotNull();
      assertThat(attribute.isReadOnly())
          .as("授权入口 %s 必须使用只读事务", contractMethod.toGenericString())
          .isTrue();
    }
  }

  private AuthorizationService proxiedAuthorization(RecordingTransactionManager transactions) {
    IdentityAuthorizationAdapter target = new IdentityAuthorizationAdapter();
    CurrentActorProvider actorProvider =
        () -> Optional.of(new CurrentActor(USER_ID, Set.of("talent.read"), false));
    ReflectionTestUtils.setField(target, "actorProvider", actorProvider);
    ReflectionTestUtils.setField(target, "accountService", accounts);
    ReflectionTestUtils.setField(target, "objectGrantPersistenceService", objectGrants);
    ReflectionTestUtils.setField(
        target, "userSupportDepartmentPersistenceService", supportDepartments);

    ProxyFactory proxyFactory = new ProxyFactory(target);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (AuthorizationService) proxyFactory.getProxy();
  }

  private static void assertReadOnlyTransaction() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
  }

  private static UserAccountRecord activeAccount() {
    return new UserAccountRecord(
        USER_ID,
        "测试账号",
        "user@example.com",
        "user@example.com",
        "{noop}secret",
        "home-department",
        "ACTIVE",
        false,
        0);
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      assertThat(definition.isReadOnly()).isTrue();
      begun.incrementAndGet();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    int begun() {
      return begun.get();
    }

    int committed() {
      return committed.get();
    }
  }
}
