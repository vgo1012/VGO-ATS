package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.intelligence.matching.domain.dto.PreviewMatchDTO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

class MatchPreviewTransactionTest {

  @Test
  void previewResponseUsesWriteCapableTransactionForTheJdLifecycleLock() throws NoSuchMethodException {
    var method = MatchRunApplicationManager.class.getMethod("previewResponse", PreviewMatchDTO.class);
    var attribute = new AnnotationTransactionAttributeSource()
        .getTransactionAttribute(method, MatchRunApplicationManager.class);

    assertThat(attribute).isNotNull();
    assertThat(attribute.isReadOnly())
        .as("JD 新业务校验会执行 FOR UPDATE，外层预览事务不能标记为只读")
        .isFalse();
  }
}
