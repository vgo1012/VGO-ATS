package com.ezaigc.hr.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import com.ezaigc.hr.identity.domain.request.IdentityAdminDeleteDepartmentRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

class DepartmentDeleteControllerContractTest {

  @Test
  void exposesVersionedDeleteAsNoContent() throws NoSuchMethodException {
    var manager = mock(IdentityAdministrationApplicationManager.class);
    var controller = new IdentityAdminController();
    ReflectionTestUtils.setField(controller, "service", manager);

    controller.deleteDepartment(
        "01JDEPARTMENT0000000000000",
        new IdentityAdminDeleteDepartmentRequest("3", "组织清理"));

    verify(manager).deleteDepartment("01JDEPARTMENT0000000000000", 3, "组织清理");
    Method method =
        IdentityAdminController.class.getDeclaredMethod(
            "deleteDepartment", String.class, IdentityAdminDeleteDepartmentRequest.class);
    assertThat(method.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/departments/{departmentId}");
    assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
