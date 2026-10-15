package com.ezaigc.hr.recruiting.submission.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubmissionMapperProjectionTest {

  @ParameterizedTest
  @ValueSource(strings = {"lock", "currentRestriction"})
  void selectsAuditFieldsOnceAndPreservesBoundConditions(String methodName) {
    var method = Arrays.stream(SubmissionMapper.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    var source = new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Map.class);
    var bound = source.getBoundSql(Map.of("id", "submission", "clientId", "client", "candidateId", "candidate"));
    String rendered = bound.getSql();
    List<String> columns = Arrays.stream(rendered.substring(6, rendered.indexOf(" FROM ")).split(","))
        .map(String::strip).map(column -> column.substring(column.lastIndexOf('.') + 1)).toList();

    assertThat(columns).doesNotHaveDuplicates().doesNotContain("*")
        .contains("id", "version", "create_id", "create_name", "create_time",
            "update_id", "update_name", "update_time", "ext", "remark");
    if (methodName.equals("lock")) {
      assertThat(rendered).contains("FROM submission_client_submission WHERE id=? FOR UPDATE");
      assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty()).containsExactly("id");
    } else {
      assertThat(rendered).contains("JOIN submission_client_restriction r ON r.id=c.record_id",
          "WHERE c.client_id=? AND c.candidate_id=?");
      assertThat(bound.getParameterMappings()).extracting(mapping -> mapping.getProperty())
          .containsExactly("clientId", "candidateId");
    }
  }
}
