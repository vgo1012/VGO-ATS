package com.ezaigc.hr.recruiting.interview.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.baomidou.mybatisplus.annotation.Version;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FeedbackMappingTest {

  @Test
  void keepsBusinessVersionSeparateFromTheSingleOptimisticLockField() throws NoSuchFieldException {
    long optimisticLockFields = 0;
    for (Class<?> type = Feedback.class; type != Object.class; type = type.getSuperclass()) {
      optimisticLockFields +=
          Arrays.stream(type.getDeclaredFields())
              .filter(field -> field.isAnnotationPresent(Version.class))
              .count();
    }

    Field businessVersion = Feedback.class.getDeclaredField("currentVersionNo");
    assertFalse(businessVersion.isAnnotationPresent(Version.class));
    assertEquals(1, optimisticLockFields);
  }
}
