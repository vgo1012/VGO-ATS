package com.ezaigc.hr.intelligence.matching.domain.service;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHTS;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHT_TEXT_LENGTH;
import static com.ezaigc.hr.intelligence.matching.domain.service.MatchingText.similar;

import com.ezaigc.hr.intelligence.matching.domain.dto.MatchCriterionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionScoreDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionMatchStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchDimensionStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchingFactType;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeExperienceYears;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 历史规则评分的离线回归实现，不注册到运行时；新评分只能经过 AI 评分编排。
 */
@Deprecated
public class DeterministicMatchingEngine {

    @Resource
    private ControlledCriteriaEvaluator controlledCriteria;
    @Resource
    private Clock clock;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public MatchEvaluationDTO evaluate(
            JdFactSnapshotData jd,
            ResumeFactSnapshotData resume,
            MatchPlanVersion plan,
            WeightProfileVersion weights,
            List<ControlledCriterion> criteria,
            Set<String> selectedCriterionIds,
            int planMaxBonus) {
        criteria = PersonalScreeningSettings.merge(criteria, weights);
        if (weights.getScreeningSettingsJson() != null) planMaxBonus = Math.min(30, criteria.stream().filter(item -> ControlledCriterionLevel.PREFER.matches(item.getLevel()))
            .mapToInt(ControlledCriterion::getBonusPoints).sum());
        List<MatchDimensionResultDTO> dimensions =
                limitHighlights(
                    List.of(
                        dimension(
                                "CORE_SKILLS",
                                true,
                                weights.getSkillsWeight(),
                                skills(jd, resume)),
                        dimension(
                                "ROLE_EXPERIENCE",
                                true,
                                weights.getRoleWeight(),
                                roles(jd, resume)),
                        dimension(
                                "RELATED_YEARS",
                                true,
                                weights.getYearsWeight(),
                                years(jd, resume)),
                        dimension(
                                "RESPONSIBILITY_MATCH",
                                true,
                                weights.getResponsibilityWeight(),
                                responsibilities(jd, resume)),
                        dimension(
                                "INDUSTRY_AND_PROJECTS",
                                true,
                                weights.getIndustryProjectWeight(),
                                industryProjects(jd, resume))));
        int denominator =
                dimensions.stream()
                        .filter(MatchDimensionResultDTO::isEnabled)
                        .mapToInt(MatchDimensionResultDTO::getConfiguredWeight)
                        .sum();
        BigDecimal professional = denominator == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : dimensions.stream()
                        .filter(MatchDimensionResultDTO::isEnabled)
                        .map(
                                item ->
                                        item.getScore()
                                                .multiply(BigDecimal.valueOf(item.getConfiguredWeight()))
                                                .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
        List<MatchCriterionResultDTO> criterionResults =
                controlledCriteria.evaluate(criteria, resume, selectedCriterionIds);
        String mustTier =
                criterionResults.stream()
                        .filter(
                                v ->
                                        ControlledCriterionLevel.MUST.matches(v.getLevel())
                                                && CriterionMatchStatus.FAIL.matches(v.getStatus()))
                        .findAny()
                        .isPresent()
                        ? CriterionMatchStatus.FAIL.getCode()
                        : criterionResults.stream()
                                .filter(
                                        v ->
                                                ControlledCriterionLevel.MUST.matches(v.getLevel())
                                                        && CriterionMatchStatus.UNKNOWN.matches(v.getStatus()))
                                .findAny()
                                .isPresent()
                                ? CriterionMatchStatus.UNKNOWN.getCode()
                                : CriterionMatchStatus.PASS.getCode();
        int selectedCap =
                Math.min(
                        planMaxBonus,
                        criterionResults.stream()
                                .filter(MatchCriterionResultDTO::isSelected)
                                .filter(v -> ControlledCriterionLevel.PREFER.matches(v.getLevel()))
                                .mapToInt(MatchCriterionResultDTO::getConfiguredBonus)
                                .sum());
        int bonus =
                Math.min(
                        selectedCap,
                        criterionResults.stream()
                                .filter(MatchCriterionResultDTO::isSelected)
                                .filter(
                                        v ->
                                                ControlledCriterionLevel.PREFER.matches(v.getLevel())
                                                        && CriterionMatchStatus.PASS.matches(v.getStatus()))
                                .mapToInt(MatchCriterionResultDTO::getConfiguredBonus)
                                .sum());
        return new MatchEvaluationDTO(
                jd.getSnapshotId(),
                resume.getSnapshotId(),
                resume.getCandidateId(),
                resume.getResumeVersionId(),
                LocalDate.now(clock),
                professional,
                bonus,
                professional.add(BigDecimal.valueOf(bonus)).setScale(2, RoundingMode.HALF_UP),
                mustTier,
                selectedCap,
                dimensions,
                criterionResults);
    }

    private MatchDimensionScoreDTO skills(JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
        List<JdFactSnapshotData.Skill> targets = jd.getProfessional().coreSkills();
      if (targets.isEmpty()) {
        return coverage(jd, referenceTargets(jd), professionalEvidence(resume), false,
                "JD 未单列技能，依据已明确的岗位职责与场景评价能力");
      }
        List<String> actual = Stream.concat(
                        resume.getSkills().stream().map(ResumeFactSnapshotData.Skill::name),
                        professionalEvidence(resume).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        List<List<JdFactSnapshotData.Skill>> groups = targets.stream()
                .map(List::of)
                .toList();
        List<List<JdFactSnapshotData.Skill>> core = groups.stream()
                .filter(group -> group.stream().anyMatch(JdFactSnapshotData.Skill::required)).toList();
        List<List<JdFactSnapshotData.Skill>> preferred = groups.stream()
                .filter(group -> group.stream().noneMatch(JdFactSnapshotData.Skill::required)).toList();
        List<String> highlights = new ArrayList<>();
        int coreMatched = matchedSkills(core, actual, highlights);
        int preferredMatched = matchedSkills(preferred, actual, highlights);
        if (coreMatched + preferredMatched == 0) return unknown("能力信息不足，不能据此判定不具备能力");
        // 优先能力只提高本维度分数；缺少优先项不能降低主要能力得分。
        BigDecimal score = core.isEmpty() ? percent(preferredMatched, preferred.size())
                : percent(coreMatched, core.size()).add(preferred.isEmpty() ? BigDecimal.ZERO
                        : percent(preferredMatched, preferred.size()).multiply(new BigDecimal("0.20")))
                        .min(HUNDRED);
        return known(
                score,
                coreMatched + "/" + core.size() + " 组主要能力匹配，"
                        + preferredMatched + "/" + preferred.size() + " 组优先能力匹配（只增益，不作淘汰条件）",
                highlights);
    }

    private static int matchedSkills(
            List<List<JdFactSnapshotData.Skill>> targets, List<String> actual, List<String> highlights) {
        int matched = 0;
        for (List<JdFactSnapshotData.Skill> group : targets) {
            List<String> alternatives = group.stream().flatMap(target ->
                    Stream.concat(Stream.of(target.name()), target.aliases().stream())).toList();
            String hit =
                    actual.stream()
                            .filter(value -> alternatives.stream().anyMatch(alias ->
                                    ProfessionalEvidenceMatcher.matches(value, alias)))
                            .findFirst()
                            .orElse(null);
            if (hit != null) {
                matched++;
                highlights.add(highlight(group.stream().map(JdFactSnapshotData.Skill::name)
                        .collect(Collectors.joining(" / ")), hit));
            }
        }
        return matched;
    }

    private MatchDimensionScoreDTO roles(JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
        List<String> targets =
                jd.getProfessional().roleExperiences().stream().map(JdFactSnapshotData.Role::role).toList();
      if (targets.isEmpty()) {
        return coverage(jd, referenceTargets(jd), workEvidence(resume), true,
                "JD 未单列岗位经历，按工作中的相关能力与职责评价，不新增岗位门槛");
      }
        List<String> actual =
                Stream.concat(
                                Stream.of(resume.getCandidate().currentRole()),
                                resume.getWorkExperiences().stream().map(ResumeFactSnapshotData.Work::role))
                        .filter(v -> v != null && !v.isBlank())
                        .toList();
        List<String> highlights = matchingHighlights(targets, actual);
        int matched = highlights.size();
        if (matched == 0) return coverage(jd, referenceTargets(jd), workEvidence(resume), true,
                "岗位名称未直接匹配，按工作中的可迁移能力与职责评价");
        return known(
                percent(matched, targets.size()),
                matched + "/" + targets.size() + " 项岗位经历命中",
                highlights);
    }

    private MatchDimensionScoreDTO years(JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
        BigDecimal required =
                jd.getProfessional().minimumYears() != null
                        ? jd.getProfessional().minimumYears()
                        : jd.getProfessional().preferredYears();
        boolean defaultReference = required == null;
        if (defaultReference) required = new BigDecimal("3");
        List<String> targets = referenceTargets(jd);
        List<ResumeFactSnapshotData.Work> related = resume.getWorkExperiences().stream()
                .filter(work -> jd.getProfessional().roleExperiences().stream()
                        .anyMatch(role -> ProfessionalEvidenceMatcher.matches(work.role(), role.role()))
                        || workEvidence(work).stream().anyMatch(value -> targets.stream()
                                .anyMatch(target -> matchesTarget(jd, value, target, true))))
                .toList();
        List<ResumeFactSnapshotData.Work> dated = related.stream()
                .filter(work -> ResumeExperienceYears.calculate(List.of(work), clock) != null).toList();
        BigDecimal workYears = ResumeExperienceYears.calculate(dated, clock);
        BigDecimal skillYears = resume.getSkills().stream()
                .filter(skill -> skill.years() != null && jd.getProfessional().coreSkills().stream()
                        .anyMatch(target -> matchesTarget(jd, skill.name(), target.name(), false)))
                .map(ResumeFactSnapshotData.Skill::years).max(BigDecimal::compareTo).orElse(null);
        BigDecimal actual = workYears == null ? skillYears
                : skillYears == null ? workYears : workYears.max(skillYears);
        if (actual == null) return unknown("相关履历或技能年限信息不足；不以总工龄替代相关年限"
                + (defaultReference ? "；默认 3 年仅作评价参照，不是 JD 门槛" : ""));
        BigDecimal score =
                required.signum() == 0
                        ? HUNDRED
                        : actual.multiply(HUNDRED).divide(required, 8, RoundingMode.HALF_UP).min(HUNDRED);
        return known(
                score,
                "识别相关年限 " + actual.setScale(1, RoundingMode.HALF_UP) + " 年 / "
                        + (defaultReference ? "评价参照 3 年（不是 JD 门槛）" : "JD 要求 " + required + " 年")
                        + (dated.size() < related.size() ? "；日期不全的经历未计入，当前为可证明下界" : ""),
                List.of());
    }

    private MatchDimensionScoreDTO responsibilities(
            JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
        List<String> targets = jd.getProfessional().responsibilities();
      if (targets.isEmpty()) {
        return coverage(jd, referenceTargets(jd), experienceEvidence(resume), true,
                "JD 未单列职责，按已明确能力的实际工作与项目应用评价");
      }
        List<String> corpus = new ArrayList<>(experienceEvidence(resume));
        resume.getSimpleFacts().stream()
                .filter(v -> MatchingFactType.RESPONSIBILITY_TAG.matches(v.type()) && v.name() != null)
                .map(ResumeFactSnapshotData.SimpleFact::name)
                .forEach(corpus::add);
        List<List<String>> groups = responsibilityGroups(targets);
        List<String> highlights = new ArrayList<>();
        int matched = 0;
        for (List<String> group : groups) {
            boolean groupMatched = false;
            for (String target : group) {
                String hit = corpus.stream().filter(value -> ProfessionalEvidenceMatcher.matchesExperience(value, target))
                        .findFirst().orElse(null);
                if (hit != null) {
                    highlights.add(highlight(target, hit));
                    groupMatched = true;
                }
            }
            if (groupMatched) matched++;
        }
        if (matched == 0) return unknown("职责信息不足，需补充实际职责说明");
        return known(
                percent(matched, groups.size()),
                matched + "/" + groups.size() + " 组 JD 职责来源命中",
                highlights);
    }

    private static List<List<String>> responsibilityGroups(List<String> targets) {
        return targets.stream().map(List::of).toList();
    }

    private MatchDimensionScoreDTO industryProjects(
            JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
        List<String> targets =
                Stream.concat(
                                jd.getProfessional().industryTags().stream(), jd.getProfessional().projectTags().stream())
                        .toList();
      if (targets.isEmpty()) {
        return coverage(jd, referenceTargets(jd), projectEvidence(resume), true,
                "JD 未限定行业或项目，按相关项目与工作实践评价，不新增行业门槛");
      }
        List<String> corpus = new ArrayList<>(experienceEvidence(resume));
        resume.getSimpleFacts().stream()
                .filter(v -> MatchingFactType.INDUSTRY_TAG.matches(v.type()) && v.name() != null)
                .map(ResumeFactSnapshotData.SimpleFact::name)
                .forEach(corpus::add);
        resume
                .getProjectExperiences()
                .forEach(
                        v -> {
                          if (v.projectName() != null) {
                            corpus.add(v.projectName());
                          }
                          if (v.description() != null) {
                            corpus.add(v.description());
                          }
                            corpus.addAll(v.technologies());
                        });
        List<List<String>> groups = new ArrayList<>();
        jd.getProfessional().industryTags().forEach(tag -> groups.add(List.of(tag)));
        jd.getProfessional().projectTags().forEach(tag -> groups.add(List.of(tag)));
        List<String> highlights = new ArrayList<>();
        int matched = 0;
        for (List<String> group : groups) {
            String found = null;
            for (String target : group) {
                String hit = corpus.stream().filter(value -> ProfessionalEvidenceMatcher.matches(value, target))
                        .findFirst().orElse(null);
                if (hit != null) {
                    found = highlight(target, hit);
                    break;
                }
            }
            if (found != null) {
                matched++;
                highlights.add(found);
            }
        }
        if (matched == 0) return unknown("行业或项目信息不足，需补充相关实践");
        return known(
                percent(matched, groups.size()),
                matched + "/" + groups.size() + " 组行业/项目证据命中",
                highlights);
    }

    /** 缺少独立 JD 字段时使用已明确的职业事实，不编造额外招聘条件。 */
    private static List<String> referenceTargets(JdFactSnapshotData jd) {
        var facts = jd.getProfessional();
        return Stream.of(facts.coreSkills().stream().map(JdFactSnapshotData.Skill::name),
                        facts.responsibilities().stream(), facts.projectTags().stream(), facts.industryTags().stream())
                .flatMap(stream -> stream).filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static boolean matchesTarget(JdFactSnapshotData jd, String actual, String target, boolean experience) {
        List<String> alternatives = new ArrayList<>(List.of(target));
        jd.getProfessional().coreSkills().stream().filter(skill -> skill.name().equals(target))
                .forEach(skill -> alternatives.addAll(skill.aliases()));
        return alternatives.stream().anyMatch(alias -> experience
                ? ProfessionalEvidenceMatcher.matchesExperience(actual, alias)
                : ProfessionalEvidenceMatcher.matches(actual, alias));
    }

    private static MatchDimensionScoreDTO coverage(JdFactSnapshotData jd, List<String> targets,
            List<String> actual, boolean experience, String explanation) {
        List<String> highlights = new ArrayList<>();
        for (String target : targets) {
            actual.stream().filter(value -> matchesTarget(jd, value, target, experience)).findFirst()
                    .ifPresent(value -> highlights.add(highlight(target, value)));
        }
        if (highlights.isEmpty()) return unknown(explanation + "；匹配信息不足，待核实");
        return known(percent(highlights.size(), targets.size()), explanation + "；"
                + highlights.size() + "/" + targets.size() + " 项匹配", highlights);
    }

    private static List<String> workEvidence(ResumeFactSnapshotData resume) {
        return resume.getWorkExperiences().stream().flatMap(work -> workEvidence(work).stream()).toList();
    }

    private static List<String> workEvidence(ResumeFactSnapshotData.Work work) {
        return Stream.of(Stream.of(work.description()), work.responsibilities().stream(), work.achievements().stream())
                .flatMap(stream -> stream).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static List<String> projectEvidence(ResumeFactSnapshotData resume) {
        return Stream.concat(experienceEvidence(resume).stream(), resume.getProjectExperiences().stream()
                .map(ResumeFactSnapshotData.Project::projectName))
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static List<String> experienceEvidence(ResumeFactSnapshotData resume) {
        List<String> result = new ArrayList<>();
        resume.getWorkExperiences().forEach(work -> {
            result.add(work.description());
            result.addAll(work.responsibilities());
            result.addAll(work.achievements());
        });
        resume.getProjectExperiences().forEach(project -> {
            result.add(project.description());
            result.addAll(project.achievements());
        });
        return result.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static List<String> professionalEvidence(ResumeFactSnapshotData resume) {
        return Stream.concat(experienceEvidence(resume).stream(), resume.getProjectExperiences().stream()
                .flatMap(project -> project.technologies().stream())).toList();
    }

    private static MatchDimensionResultDTO dimension(
            String code, boolean enabled, int weight, MatchDimensionScoreDTO score) {
        return new MatchDimensionResultDTO(
                code,
                enabled,
                weight,
                score.getStatus(),
                score.getScore().setScale(2, RoundingMode.HALF_UP),
                score.getExplanation(),
                score.getHighlights());
    }

    private static MatchDimensionScoreDTO unknown(String explanation) {
        return new MatchDimensionScoreDTO(
                MatchDimensionStatus.UNKNOWN.getCode(), BigDecimal.ZERO, explanation, List.of());
    }

    private static MatchDimensionScoreDTO known(
            BigDecimal score, String explanation, Collection<String> highlights) {
        return new MatchDimensionScoreDTO(
                MatchDimensionStatus.KNOWN.getCode(), score, explanation, List.copyOf(highlights));
    }

    /** 各维度轮询取值，避免某个维度占满全部亮点展示位。 */
    private static List<MatchDimensionResultDTO> limitHighlights(
            List<MatchDimensionResultDTO> dimensions) {
        List<List<String>> candidates =
                dimensions.stream()
                        .map(
                                dimension ->
                                        List.copyOf(
                                                new LinkedHashSet<>(dimension.getHighlights())))
                        .toList();
        List<List<String>> selected = new ArrayList<>(dimensions.size());
        dimensions.forEach(ignored -> selected.add(new ArrayList<>()));
        int selectedCount = 0;
        for (int candidateIndex = 0; selectedCount < MAX_MATCH_HIGHLIGHTS; candidateIndex++) {
            boolean found = false;
            for (int dimensionIndex = 0;
                    dimensionIndex < dimensions.size() && selectedCount < MAX_MATCH_HIGHLIGHTS;
                    dimensionIndex++) {
                List<String> dimensionCandidates = candidates.get(dimensionIndex);
                if (candidateIndex < dimensionCandidates.size()) {
                    selected.get(dimensionIndex).add(dimensionCandidates.get(candidateIndex));
                    selectedCount++;
                    found = true;
                }
            }
            if (!found) {
                break;
            }
        }
        List<MatchDimensionResultDTO> result = new ArrayList<>(dimensions.size());
        for (int index = 0; index < dimensions.size(); index++) {
            MatchDimensionResultDTO value = dimensions.get(index);
            result.add(
                    new MatchDimensionResultDTO(
                            value.getCode(),
                            value.isEnabled(),
                            value.getConfiguredWeight(),
                            value.getStatus(),
                            value.getScore(),
                            value.getExplanation(),
                            selected.get(index)));
        }
        return List.copyOf(result);
    }

    private static List<String> matchingHighlights(List<String> targets, List<String> actual) {
        List<String> result = new ArrayList<>();
        for (String target : targets) {
            actual.stream()
                    .filter(value -> similar(value, target))
                    .findFirst()
                    .ifPresent(value -> result.add(highlight(target, value)));
        }
        return result;
    }

    private static String highlight(String target, String actual) {
        String value = target + " ← " + actual;
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= MAX_MATCH_HIGHLIGHT_TEXT_LENGTH) {
            return value;
        }
        int contentLength = MAX_MATCH_HIGHLIGHT_TEXT_LENGTH - 3;
        int endIndex = value.offsetByCodePoints(0, contentLength);
        return value.substring(0, endIndex) + "...";
    }

    private static BigDecimal percent(int matched, int total) {
        return total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(matched)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
    }

}
