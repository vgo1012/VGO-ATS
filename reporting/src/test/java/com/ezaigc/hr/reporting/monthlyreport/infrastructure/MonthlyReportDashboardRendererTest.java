package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DimensionMetricDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.OfferSummaryDTO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyReportDashboardRendererTest {
    private final MonthlyReportDashboardRenderer renderer = new MonthlyReportDashboardRenderer();

    @Test
    void rendersHighResolutionDashboardWithCoincidentSenderPoints() throws Exception {
        List<byte[]> pages = renderer.render(snapshot(20, 3), () -> false);
        assertThat(pages).hasSize(1);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pages.getFirst()));
        assertThat(image.getWidth()).isEqualTo(3000);
        assertThat(image.getHeight()).isEqualTo(2500);
        assertThat(image.getRGB(0, 0) & 0xffffff).isEqualTo(0xffffff);
    }

    @Test
    void preservesEverySenderAndLargeDimensionInContinuationPages() {
        // 41名发送人产生两张续图，21个小组产生两张完整维度续图。
        assertThat(renderer.render(snapshot(41, 21), () -> false)).hasSize(5);
    }

    @Test
    void observesCancellationDuringRenderingAndDoesNotReturnPartialDashboard() {
        AtomicInteger checks = new AtomicInteger();
        assertThatThrownBy(() -> renderer.render(snapshot(41, 3), () -> checks.incrementAndGet() >= 3))
                .isInstanceOf(MonthlyReportInterruptedException.class);
        assertThatThrownBy(() -> renderer.render(snapshot(1, 1), () -> true))
                .isInstanceOf(MonthlyReportInterruptedException.class);
    }

    private static MonthlyReportSnapshotDTO snapshot(int senders, int groups) {
        List<DimensionMetricDTO> dimensions = new ArrayList<>();
        for (int index = 0; index < senders; index++) dimensions.add(metric("sender", "同坐标发送人" + index, "小组" + index % groups));
        for (int index = 0; index < groups; index++) dimensions.add(metric("group", "小组" + index, null));
        dimensions.add(metric("employment", "内部员工", null));
        dimensions.add(metric("channel", "BOSS直聘", null));
        dimensions.add(metric("gradeBand", "1.0", null));
        OfferSummaryDTO offers = new OfferSummaryDTO(senders, senders, BigDecimal.valueOf(senders), Map.of("已入职", senders),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "2026-08-01", "2026-08-31", Map.of());
        return new MonthlyReportSnapshotDTO("v1", "v1", "v2", "2026-08", "2026-09-01", "国内", true,
                offers, null, dimensions, List.of(), List.of(), List.of());
    }

    private static DimensionMetricDTO metric(String dimension, String key, String group) {
        return new DimensionMetricDTO(dimension, key, group, 1, BigDecimal.ONE, BigDecimal.ONE, Map.of(), BigDecimal.ONE, BigDecimal.ONE);
    }
}
