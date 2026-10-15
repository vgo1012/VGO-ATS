package com.ezaigc.hr.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.application.adapter.IdentityDirectoryAdapter;
import com.ezaigc.hr.identity.application.manager.IdentityDirectoryApplicationManager;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityDirectoryWiringTest {

  @ParameterizedTest
  @ValueSource(classes = {IdentityDirectoryAdapter.class, IdentityDirectoryApplicationManager.class})
  void directoryComponentsResolvePersistenceServiceWhenMapperAlsoExists(Class<?> component) {
    var persistence = mock(IdentityAdministrationPersistenceService.class);
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean("identityAdministrationPersistenceServiceImpl",
          IdentityAdministrationPersistenceService.class, () -> persistence);
      context.registerBean(IdentityAdministrationMapper.class.getName(),
          IdentityAdministrationMapper.class, () -> mock(IdentityAdministrationMapper.class));
      context.registerBean(IdentityAccountPersistenceService.class,
          () -> mock(IdentityAccountPersistenceService.class));
      context.registerBean(AuthorizationService.class, () -> mock(AuthorizationService.class));
      context.registerBean(CursorCodec.class, () -> mock(CursorCodec.class));
      context.register(component);

      context.refresh();

      assertThat(ReflectionTestUtils.getField(context.getBean(component), "administration"))
          .isSameAs(persistence);
    }
  }
}
