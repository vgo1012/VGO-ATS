package com.ezaigc.hr.bootstrap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.recruiting.demand.controller.DemandCatalogController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JdTitleSuggestionHttpContractTest {
  @Test
  void retiredTitleSuggestionAndReviewRoutesAreUnavailable() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new DemandCatalogController()).build();
    mvc.perform(get("/api/v1/jd-title-suggestions")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/admin/jd-title-suggestions")).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/admin/jd-title-suggestions/{id}/review", "S".repeat(26))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isNotFound());
  }
}
