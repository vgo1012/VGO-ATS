package com.ezaigc.hr.recruiting.interview.service;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_FEEDBACK_VERSIONS_PER_BATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackVersionMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.FeedbackVersionPersistenceServiceImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FeedbackVersionBatchQueryTest {
  private final FeedbackVersionMapper mapper = mock(FeedbackVersionMapper.class);
  private final FeedbackVersionPersistenceServiceImpl service = new FeedbackVersionPersistenceServiceImpl();

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), FeedbackVersion.class);
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void maximumBatchUsesExactParameterizedPairsInsteadOfIndependentIdAndVersionSets() {
    Map<String, Integer> requested = batch(MAX_FEEDBACK_VERSIONS_PER_BATCH);
    String hostileId = "' OR 1=1 --";
    requested.remove("0".repeat(26));
    requested.put(hostileId, 7);
    FeedbackVersion found = new FeedbackVersion();
    found.setFeedbackId(hostileId);
    found.setVersionNo(7);
    when(mapper.selectList(any())).thenReturn(List.of(found));

    assertThat(service.findFeedbackVersions(requested)).containsOnly(Map.entry(hostileId, found));

    ArgumentCaptor<LambdaQueryWrapper<FeedbackVersion>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    LambdaQueryWrapper<FeedbackVersion> query = captor.getValue();
    String sql = query.getSqlSegment();
    assertThat(sql).doesNotContain(hostileId, " IN ");
    Map<String, Object> parameters = query.getParamNameValuePairs();
    assertThat(parameters).hasSize(MAX_FEEDBACK_VERSIONS_PER_BATCH * 2);
    Pattern pairPattern = Pattern.compile(
        "\\(feedback_id = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\} AND version_no = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}\\)");
    Matcher pairs = pairPattern.matcher(sql);
    Map<String, Integer> actual = new LinkedHashMap<>();
    while (pairs.find()) {
      actual.put((String) parameters.get(pairs.group(1)), (Integer) parameters.get(pairs.group(2)));
    }
    assertThat(actual).isEqualTo(requested);
    assertThat(pairPattern.matcher(sql).replaceAll("PAIR").replace("(", "").replace(")", ""))
        .isEqualTo(String.join(" OR ", Collections.nCopies(MAX_FEEDBACK_VERSIONS_PER_BATCH, "PAIR")));
  }

  @Test
  void emptyAndInvalidBatchesNeverAccessTheMapper() {
    assertThat(service.findFeedbackVersions(Map.of())).isEmpty();
    assertThatThrownBy(() -> service.findFeedbackVersions(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findFeedbackVersions(batch(MAX_FEEDBACK_VERSIONS_PER_BATCH + 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findFeedbackVersions(Map.of(" ", 1))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findFeedbackVersions(Map.of("F".repeat(26), 0))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findFeedbackVersions(Map.of("F".repeat(26), -1))).isInstanceOf(IllegalArgumentException.class);
    Map<String, Integer> missingKey = new HashMap<>();
    missingKey.put(null, 1);
    assertThatThrownBy(() -> service.findFeedbackVersions(missingKey)).isInstanceOf(IllegalArgumentException.class);
    Map<String, Integer> missingVersion = new HashMap<>();
    missingVersion.put("F".repeat(26), null);
    assertThatThrownBy(() -> service.findFeedbackVersions(missingVersion)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mapper);
  }

  private static Map<String, Integer> batch(int size) {
    Map<String, Integer> values = new LinkedHashMap<>();
    for (int index = 0; index < size; index++) {
      values.put(String.format("%026d", index), index + 1);
    }
    return values;
  }
}
