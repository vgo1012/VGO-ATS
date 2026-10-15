package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

/** 仅供隔离 MySQL 业务链测试；固定响应验证持久化，不冒充真实模型质量。 */
final class SyntheticProfessionalScoringModel implements ProfessionalScoringModelPort {
  private static final AtomicInteger CALLS = new AtomicInteger();

  static int calls() { return CALLS.get(); }

  @Override public ModelIdentity cacheIdentity() { return new ModelIdentity("synthetic-it", "professional-v1"); }

  @Override public ModelResult score(ScoreRequest request) {
    CALLS.incrementAndGet();
    var json = new ObjectMapper();
    var jd = json.readTree(request.jdFactsJson()).path("professional");
    var resume = json.readTree(request.resumeFactsJson());
    boolean known = !jd.isMissingNode()
        && (!resume.path("skills").isEmpty() || !resume.path("workExperiences").isEmpty());
    var output = json.createObjectNode().put("professionalScore", known ? 88 : 0).put("summary", "合成模型用于验证冻结评分与权重重算");
    var dimensions = output.putArray("dimensions");
    List<String> codes = List.of("CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS",
        "RESPONSIBILITY_MATCH", "INDUSTRY_AND_PROJECTS");
    List<Integer> scores = List.of(90, 80, 70, 60, 50);
    for (int index = 0; index < codes.size(); index++) {
      String code = codes.get(index);
      var dimension = dimensions.addObject().put("code", code).put("status", known ? "KNOWN" : "UNKNOWN")
          .put("score", known ? scores.get(index) : 0)
          .put("explanation", known ? "隔离测试的合成评分原因" : "信息不足");
      dimension.putArray("highlights");
    }
    return new ModelResult("synthetic-it", "professional-v1", output.toString(), 100, 100, 1);
  }
}
