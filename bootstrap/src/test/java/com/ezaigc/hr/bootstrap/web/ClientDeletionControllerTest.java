package com.ezaigc.hr.bootstrap.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.application.manager.ClientDeletionApplicationManager;
import com.ezaigc.hr.recruiting.demand.controller.DemandCatalogController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ClientDeletionControllerTest {
  private static final String ID = "01J" + "A".repeat(23);
  private final ClientDeletionApplicationManager manager = mock(ClientDeletionApplicationManager.class);
  private MockMvc mvc;

  @BeforeEach void setup() {
    var controller = new DemandCatalogController();
    ReflectionTestUtils.setField(controller, "clientDeletion", manager);
    ReflectionTestUtils.setField(controller, "assembler", new DemandCatalogAssembler());
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test void deletesWithExactVersionAndReasonAndReturnsNoContent() throws Exception {
    mvc.perform(delete("/api/v1/clients/{id}", ID).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":\"9007199254740993\",\"reason\":\"管理员删除客户\"}"))
        .andExpect(status().isNoContent());
    verify(manager).deleteClient(eq(ID), argThat(command -> command.getVersion() == 9007199254740993L
        && command.getReason().equals("管理员删除客户")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"version\":\"0\"}", "{\"version\":\"-1\",\"reason\":\"删除\"}",
      "{\"reason\":\"删除\"}", "{\"version\":\"1.5\",\"reason\":\"删除\"}",
      "{\"version\":\"0\",\"reason\":\" \"}"})
  void rejectsMissingOrInvalidWritePreconditions(String body) throws Exception {
    mvc.perform(delete("/api/v1/clients/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }
}
