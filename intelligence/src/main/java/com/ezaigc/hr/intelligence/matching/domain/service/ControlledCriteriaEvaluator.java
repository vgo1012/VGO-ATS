package com.ezaigc.hr.intelligence.matching.domain.service;

import static com.ezaigc.hr.intelligence.matching.domain.service.MatchingText.normalize;
import static com.ezaigc.hr.intelligence.matching.domain.service.MatchingText.similar;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchCriterionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionMatchStatus;
import com.ezaigc.hr.intelligence.matching.enums.CriterionOperator;
import com.ezaigc.hr.intelligence.matching.enums.MatchingFactType;
import com.ezaigc.hr.talent.api.enums.DegreeLevel;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 明确资格条件的核对；不生成任何五维职业能力分。 */
@Component
public class ControlledCriteriaEvaluator {
    @Resource private ObjectMapper json;

    public List<MatchCriterionResultDTO> evaluate(List<ControlledCriterion> criteria,
            ResumeFactSnapshotData resume, Set<String> selected) {
        return criteria.stream().map(item -> criterion(item, resume, selected)).toList();
    }

    private MatchCriterionResultDTO criterion(
            ControlledCriterion value, ResumeFactSnapshotData resume, Set<String> selected) {
        boolean active =
                !ControlledCriterionLevel.PREFER.matches(value.getLevel())
                        || !value.isSelectableByHr()
                        || selected.contains(value.getId());
        ControlledCriterionCode code = ControlledCriterionCode.fromCode(value.getCriterionCode());
        List<String> facts = criterionFacts(code, resume);
        List<String> expected = expectedValues(value.getExpectedValueJson(), value.getOperator());
        CriterionOperator operator = criterionOperator(value.getOperator());
        List<String> matched = facts.stream()
                .filter(fact -> matchesFact(code, operator, fact, expected))
                .toList();
        CriterionMatchStatus status;
        if (ControlledCriterionLevel.DISPLAY_ONLY.matches(value.getLevel())) {
            status = facts.isEmpty() ? CriterionMatchStatus.UNKNOWN : CriterionMatchStatus.PASS;
        } else if (!matched.isEmpty()) {
            status = CriterionMatchStatus.PASS;
        } else if (facts.isEmpty()) {
            status = CriterionMatchStatus.UNKNOWN;
        } else if (explicitlyContradicts(code, operator, facts, expected)) {
            status = CriterionMatchStatus.FAIL;
        } else {
            status = CriterionMatchStatus.UNKNOWN;
        }
        return new MatchCriterionResultDTO(
                value.getId(),
                value.getCriterionCode(),
                value.getLevel(),
                status.getCode(),
                active,
                value.getBonusPoints(),
                active
                        && status == CriterionMatchStatus.PASS
                        && ControlledCriterionLevel.PREFER.matches(value.getLevel())
                        ? value.getBonusPoints()
                        : 0,
                value.getReason(),
                facts,
                expected);
    }

    private static List<String> criterionFacts(
            ControlledCriterionCode code, ResumeFactSnapshotData resume) {
        List<String> facts = new ArrayList<>();
        switch (code) {
            case AGE -> {
                if (resume.getCandidate().age() != null) {
                    facts.add(String.valueOf(resume.getCandidate().age()));
                }
            }
            case GENDER -> addFact(facts, resume.getCandidate().gender());
            case EDUCATION -> {
                for (var education : resume.getEducations()) {
                    addFact(facts, education.degree());
                }
            }
            case CERTIFICATE -> {
                List<ResumeFactSnapshotData.SimpleFact> credentials = resume.getSimpleFacts().stream()
                        .filter(item -> MatchingFactType.CREDENTIAL.matches(item.type()))
                        .toList();
                for (var credential : credentials) {
                    addFact(facts, credential.name());
                }
            }
            case LANGUAGE -> {
                List<ResumeFactSnapshotData.SimpleFact> languages = resume.getSimpleFacts().stream()
                        .filter(item -> MatchingFactType.LANGUAGE.matches(item.type()))
                        .toList();
                for (var language : languages) {
                    addFact(facts, language.name());
                    addFact(facts, language.level());
                }
            }
        }
        return List.copyOf(facts);
    }

    private static void addFact(List<String> facts, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(value);
        }
    }

    private static boolean matchesFact(
            ControlledCriterionCode code, CriterionOperator criterionOperator, String actual, List<String> expected) {
        if (code == ControlledCriterionCode.EDUCATION
                && (criterionOperator == CriterionOperator.EQ || criterionOperator == CriterionOperator.IN)) {
            int actualRank = DegreeLevel.rankOf(actual);
            return actualRank > 0 && expected.stream().mapToInt(DegreeLevel::rankOf)
                    .anyMatch(requiredRank -> requiredRank > 0 && actualRank >= requiredRank);
        }
        if (code == ControlledCriterionCode.CERTIFICATE
                && (criterionOperator == CriterionOperator.EQ || criterionOperator == CriterionOperator.IN)) {
            return expected.stream().anyMatch(value -> similar(actual, value));
        }
        return matches(criterionOperator, actual, expected);
    }

    private static boolean explicitlyContradicts(
            ControlledCriterionCode code,
            CriterionOperator criterionOperator,
            List<String> supported,
            List<String> expected) {
        if (code == ControlledCriterionCode.AGE || code == ControlledCriterionCode.GENDER) {
            return true;
        }
        if (code == ControlledCriterionCode.EDUCATION
                && (criterionOperator == CriterionOperator.EQ || criterionOperator == CriterionOperator.IN)) {
            int highestActual = supported.stream().mapToInt(DegreeLevel::rankOf).max().orElse(0);
            int lowestRequired = expected.stream().mapToInt(DegreeLevel::rankOf)
                    .filter(rank -> rank > 0).min().orElse(0);
            return highestActual > 0 && lowestRequired > 0 && highestActual < lowestRequired;
        }
        return false;
    }

    private static CriterionOperator criterionOperator(String operator) {
        try {
            return CriterionOperator.fromCode(operator);
        } catch (IllegalArgumentException exception) {
            throw invalidConfiguration(exception);
        }
    }

    private List<String> expectedValues(String value, String operator) {
        try {
            List<String> result = new ArrayList<>();
            String normalizedOperator = CommonUtils.upperOrEmpty(operator);
            var reader = json.reader();
            if (CriterionOperator.LTE.matches(normalizedOperator)
                    || CriterionOperator.GTE.matches(normalizedOperator)
                    || CriterionOperator.BETWEEN.matches(normalizedOperator)) {
                reader = reader.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            }
            JsonNode root = reader.readTree(value);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("expected value must be a JSON object");
            }
            // 命名区间按字段含义读取；JSON 对象属性顺序不代表上下界顺序。
            if (CriterionOperator.BETWEEN.matches(normalizedOperator)
                    && root.size() == 2 && root.has("min") && root.has("max")) {
                collect(root.get("min"), result);
                collect(root.get("max"), result);
            } else {
                collect(root, result);
            }
            return result;
        } catch (RuntimeException exception) {
            throw invalidConfiguration(exception);
        }
    }

    private static void collect(JsonNode node, List<String> values) {
      if (node == null || node.isNull()) {
        return;
      }
        if (node.isValueNode()) {
            values.add(node.asString());
            return;
        }
        node.valueStream().forEach(item -> collect(item, values));
    }

    private static boolean matches(CriterionOperator criterionOperator, String actual, List<String> expected) {
      if (expected.isEmpty()) {
        return false;
      }
        if (criterionOperator.isRangeOperator()) {
            try {
                BigDecimal value = new BigDecimal(actual);
                List<BigDecimal> bounds = expected.stream().map(BigDecimal::new).toList();
                return switch (criterionOperator) {
                    case LTE -> value.compareTo(bounds.getFirst()) <= 0;
                    case GTE -> value.compareTo(bounds.getFirst()) >= 0;
                    case BETWEEN -> bounds.size() >= 2
                            && value.compareTo(bounds.get(0)) >= 0
                            && value.compareTo(bounds.get(1)) <= 0;
                    default -> false;
                };
            } catch (RuntimeException ex) {
                throw invalidConfiguration(ex);
            }
        }
        return expected.stream().anyMatch(e -> switch (criterionOperator) {
            case EQ, IN -> canonical(actual).equals(canonical(e));
            case CONTAINS -> similar(actual, e);
            default -> similar(actual, e);
        });
    }

    private static PlatformException invalidConfiguration(Throwable cause) {
        return new PlatformException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "MATCHING_CONFIGURATION_INVALID",
                "匹配配置数据无效，请联系管理员",
                false,
                cause);
    }



    private static String canonical(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "男", "male" -> "male";
            case "女", "female" -> "female";
            case "其他", "other" -> "other";
            default -> normalized;
        };
    }

}
