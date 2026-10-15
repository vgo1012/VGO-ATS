package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.monthlyreport.application.assembler.MonthlyReportAssembler;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DemandSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.OfferSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class MonthlyReportAssemblerTest {
    @Test
    void persistedSnapshotIsReadableByHttpResponseAndPreservesUndefinedRates() {
        var json = JsonMapper.builder().build();
        var assembler = new MonthlyReportAssembler();
        ReflectionTestUtils.setField(assembler, "json", json);
        var snapshot = new MonthlyReportSnapshotDTO("1", "1", "1", "2026-08", "2026-09-01", "国内", true,
                new OfferSummaryDTO(0, 0, BigDecimal.ZERO, Map.of(), null, null, null, null, null, null, Map.of()),
                new DemandSummaryDTO(3, 8, 0, 0, 3, 8), List.of(), List.of(), List.of(), List.of());
        var row = new MonthlyReportEntity();
        row.setId("report"); row.setVersion(4); row.setStatus("READY");
        row.setSnapshotJson(json.writeValueAsString(snapshot));

        var response = assembler.detail(row, List.of(), null, Instant.now());
        var document = json.readTree(json.writeValueAsString(response));

        assertThat(response.snapshot().ready()).isTrue();
        assertThat(response.snapshot().demandSummary().approvedHeadcount()).isEqualTo(8);
        assertThat(document.path("version").isString()).isTrue();
        assertThat(document.path("version").asString()).isEqualTo("4");
        assertThat(document.path("snapshot").path("offerSummary").path("onboardedRate").isNull()).isTrue();
    }
}
