package com.ezaigc.hr.talent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.request.TalentIntakeVersionReasonRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

class SourceChannelDeleteControllerContractTest {

  @Test
  void exposesVersionedDeleteAsNoContent() throws NoSuchMethodException {
    var manager = mock(TalentIntakeApplicationManager.class);
    var assembler = mock(TalentIntakeAssembler.class);
    var controller = new TalentIntakeController();
    var request = new TalentIntakeVersionReasonRequest("4", "清理误建渠道");
    var dto = new TalentVersionReasonDTO(4, "清理误建渠道");
    when(assembler.toDTO(request)).thenReturn(dto);
    ReflectionTestUtils.setField(controller, "manager", manager);
    ReflectionTestUtils.setField(controller, "assembler", assembler);

    controller.deleteChannel("01JCHANNEL000000000000000", request);

    verify(manager).deleteChannel("01JCHANNEL000000000000000", dto);
    Method method =
        TalentIntakeController.class.getDeclaredMethod(
            "deleteChannel", String.class, TalentIntakeVersionReasonRequest.class);
    assertThat(method.getAnnotation(DeleteMapping.class).value())
        .containsExactly("/source-channels/{id}");
    assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
