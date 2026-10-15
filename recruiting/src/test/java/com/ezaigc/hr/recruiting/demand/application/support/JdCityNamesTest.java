package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdCityNamesTest {
  @ParameterizedTest
  @ValueSource(strings = {"北京", "北京市", " 北京 "})
  void knownAliasesBecomeTheFullCityName(String input) {
    assertThat(JdCityNames.requireCity(input)).isEqualTo("北京市");
    assertThat(JdCityNames.display(input)).isEqualTo("北京市");
  }

  @ParameterizedTest
  @ValueSource(strings = {"海淀区", "上海驻场", "北京、上海", "北京/上海", "不存在市", ""})
  void nonCityAndMultipleCityInputIsRejected(String input) {
    assertThatThrownBy(() -> JdCityNames.requireCity(input))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_INVALID"));
  }

  @Test
  void historicalReadNormalizesKnownCitiesAndPreservesUnknownOrMissingValues() {
    var jd = new JdEntity(); jd.setWorkRegionName("北京");
    assertThat(new DemandCatalogAssembler().toJdResponse(jd).getWorkRegionName()).isEqualTo("北京市");
    assertThat(JdCityNames.display("驻场")).isEqualTo("驻场");
    assertThat(JdCityNames.display(null)).isNull();
    assertThat(JdCityNames.searchQuery("北京市")).isEqualTo("北京");
    assertThat(JdCityNames.searchQuery("算法工程师")).isEqualTo("算法工程师");
  }
}
