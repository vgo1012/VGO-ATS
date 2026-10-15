package com.ezaigc.hr.recruiting.demand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.JdEntityPersistenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class JdCodeSequenceTest {
  private final JdEntityMapper mapper = mock(JdEntityMapper.class);
  private final JdEntityPersistenceService service = new JdEntityPersistenceServiceImpl();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.lockCodeAllocation()).thenReturn(0L);
  }

  @Test
  void departmentCodeIsStableAndNeverReused() {
    when(mapper.insertDepartmentCode("dept-a", "01")).thenReturn(1);
    assertThat(service.departmentCode("dept-a")).isEqualTo("01");
    when(mapper.findDepartmentCode("dept-a")).thenReturn("01");
    when(mapper.lastDepartmentCode()).thenReturn("01");
    when(mapper.insertDepartmentCode("dept-b", "02")).thenReturn(1);
    assertThat(service.departmentCode("dept-a")).isEqualTo("01");
    assertThat(service.departmentCode("dept-b")).isEqualTo("02");
    verify(mapper).insertDepartmentCode("dept-a", "01");
    verify(mapper).insertDepartmentCode("dept-b", "02");
  }

  @Test
  void departmentCodeStopsAtNinetyNineWithoutRecycling() {
    when(mapper.lastDepartmentCode()).thenReturn("99");
    assertThat(service.departmentCode("new-dept")).isNull();
    verify(mapper, never()).insertDepartmentCode("new-dept", "01");
  }

  @ParameterizedTest
  @CsvSource({"1, JD-01001002001", "999, JD-01001002999", "1000, JD-010010021000"})
  void fourPartCodeUsesExpandableGroupSequence(long sequence, String expected) {
    when(mapper.advancePairSequence("01001002")).thenReturn(1);
    when(mapper.currentPairSequence("01001002")).thenReturn(sequence);

    assertThat(service.nextCode("01", "001", "002")).isEqualTo(expected);

    var order = inOrder(mapper);
    order.verify(mapper).lockCodeAllocation();
    order.verify(mapper).initializePairSequence("01001002");
    order.verify(mapper).advancePairSequence("01001002");
    order.verify(mapper).currentPairSequence("01001002");
    order.verify(mapper).findUsedCodeForUpdate(expected);
  }

  @Test
  void deletedJdCodeIsSkipped() {
    when(mapper.advancePairSequence("01001002")).thenReturn(1);
    when(mapper.currentPairSequence("01001002")).thenReturn(1L, 2L);
    when(mapper.findUsedCodeForUpdate("JD-01001002001")).thenReturn("deleted-jd");
    assertThat(service.nextCode("01", "001", "002")).isEqualTo("JD-01001002002");
  }

  @Test
  void missingLockOrExhaustedSequenceRefusesAllocation() {
    when(mapper.lockCodeAllocation()).thenReturn(null);
    assertThatThrownBy(() -> service.nextCode("01", "001", "002"))
        .isInstanceOf(IllegalStateException.class);
    verify(mapper, never()).initializePairSequence("01001002");
    when(mapper.lockCodeAllocation()).thenReturn(0L);
    assertThatThrownBy(() -> service.nextCode("01", "001", "002"))
        .isInstanceOf(IllegalStateException.class);
    verify(mapper, never()).currentPairSequence("01001002");
  }
}
