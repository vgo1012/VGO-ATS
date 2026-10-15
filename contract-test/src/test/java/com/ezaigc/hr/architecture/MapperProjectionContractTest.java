package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdParsingMapper;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewMapper;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.talent.mapper.ResumeParsingMapper;
import com.ezaigc.hr.talent.mapper.RiskMapper;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MapperProjectionContractTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("queries")
  void selectsEachFieldOnceWithoutChangingQueryBehavior(Query query) throws Exception {
    Properties baseline = new Properties();
    try (var stream = getClass().getResourceAsStream("/mapper-projection-baseline.properties")) {
      assertNotNull(stream);
      baseline.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    BoundSql bound = render(query);
    PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(bound.getSql());
    List<String> labels = select.getSelectItems().stream().map(item -> item.getAlias() != null
        ? item.getAliasName() : ((Column) item.getExpression()).getColumnName()).toList();
    assertEquals(labels.size(), labels.stream().distinct().count(), "重复输出列: " + labels);
    assertEquals(baseline.getProperty(query + ".fields"), fields(select), "完整业务与审计投影");
    assertEquals(baseline.getProperty(query + ".query"), queryWithoutProjection(select),
        "DISTINCT、联表、过滤、排序、分页和锁定");
    assertEquals(baseline.getProperty(query + ".bindings"), bindings(bound), "绑定参数顺序");
  }

  // 固定清单仅覆盖本次发现的重复审计投影；动态查询同时覆盖空集合与展开集合。
  static Stream<Query> queries() {
    return Stream.of(
        new Query(ApplicationMapper.class, "lockApplication", false),
        new Query(ApplicationMapper.class, "findApplications", false),
        new Query(InterviewMapper.class, "lockInterview", false),
        new Query(InterviewMapper.class, "findInterviews", false),
        new Query(InterviewMapper.class, "lockNotification", false),
        new Query(InterviewMapper.class, "findMyInterviewTasks", false),
        new Query(JdParsingMapper.class, "findCurrentForUpdate", false),
        new Query(TalentIntakeMapper.class, "findIntake", false),
        new Query(TalentIntakeMapper.class, "findIntakes", false),
        new Query(TalentIntakeMapper.class, "findIntakes", true),
        new Query(TalentIntakeMapper.class, "findCandidates", false),
        new Query(TalentIntakeMapper.class, "findCandidates", true),
        new Query(TalentIntakeMapper.class, "findCandidatesByExactName", false),
        new Query(TalentIntakeMapper.class, "findCandidatesByExactName", true),
        new Query(TalentIntakeMapper.class, "findCandidateResumeVersion", false),
        new Query(TalentIntakeMapper.class, "findResumeVersions", false),
        new Query(TalentIntakeMapper.class, "findConflicts", false),
        new Query(TalentIntakeMapper.class, "findConflicts", true),
        new Query(ResumeParsingMapper.class, "findCurrentFactSnapshotForUpdate", false),
        new Query(MatchRunMapper.class, "findRunByBase", false),
        new Query(RiskMapper.class, "currentBlacklist", false));
  }

  static BoundSql render(Query query) {
    var method = Arrays.stream(query.mapper().getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(query.method())).findFirst().orElseThrow();
    Map<String, Object> parameters = new HashMap<>();
    for (var parameter : method.getParameters()) {
      Param annotation = parameter.getAnnotation(Param.class);
      String name = annotation == null ? parameter.getName() : annotation.value();
      Class<?> type = parameter.getType();
      Object value = null;
      if (Collection.class.isAssignableFrom(type)) {
        value = query.expanded() ? List.of("first", "second") : List.of();
      } else if (type == boolean.class || type == Boolean.class) {
        value = query.expanded();
      } else if (type == int.class || type == Integer.class) {
        value = 5;
      } else if (type == String.class) {
        value = name;
      } else if (type == Instant.class) {
        value = Instant.EPOCH;
      }
      parameters.put(name, value);
    }
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    return new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Map.class)
        .getBoundSql(parameters);
  }

  static String fields(PlainSelect select) {
    return select.getSelectItems().stream().map(Object::toString).distinct().sorted()
        .collect(Collectors.joining(" | "));
  }

  static String queryWithoutProjection(PlainSelect select) {
    select.setSelectItems(List.of(SelectItem.from(new LongValue(1))));
    return select.toString();
  }

  static String bindings(BoundSql bound) {
    return bound.getParameterMappings().stream().map(mapping -> mapping.getProperty())
        .collect(Collectors.joining(","));
  }

  record Query(Class<?> mapper, String method, boolean expanded) {
    @Override
    public String toString() {
      return mapper.getSimpleName() + "." + method + (expanded ? ".expanded" : ".empty");
    }
  }
}
