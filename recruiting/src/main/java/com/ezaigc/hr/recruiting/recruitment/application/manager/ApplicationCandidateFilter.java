package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.DegreeQueryAliases;
import com.ezaigc.hr.talent.api.ResumeExperienceYears;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.enums.ResumeFactErrorCode;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 仅处理已经通过招聘活动授权的批次；筛选绑定的简历版本，保留同一候选人的多岗位记录。 */
@Component
public class ApplicationCandidateFilter {
  @Resource private CandidateDirectoryService candidates;
  @Resource private ResumeFactQueryService facts;
  @Resource private Clock clock;

  public Set<String> matchingIds(List<Application> applications, ApplicationCandidateFilterDTO filter) {
    if (applications.isEmpty()) return Set.of();
    var ids = applications.stream().map(Application::getCandidateId).distinct().toList();
    var matching = filter.getQ() == null ? Set.copyOf(ids) : candidates.matchingCandidateIds(ids, filter.getQ());
    // 当前身份命中沿用原授权边界，不因新增全文能力要求读取简历事实。
    var result = applications.stream()
        .filter(app -> !filter.hasFacts() && matching.contains(app.getCandidateId()))
        .map(Application::getId).collect(Collectors.toSet());
    var pending = applications.stream().filter(app -> !result.contains(app.getId())).toList();
    if (pending.isEmpty()) return result;
    var readable = candidates.factReadableCandidateIds(
        pending.stream().map(Application::getCandidateId).distinct().toList());
    var selected = pending.stream().filter(app -> readable.contains(app.getCandidateId())).toList();
    if (selected.isEmpty()) return result;
    var batch = facts.currentFactBatch(selected.stream().map(Application::getResumeVersionId).distinct().toList());
    selected.stream().filter(app -> {
      try {
        var value = batch.get(app.getResumeVersionId());
        return app.getCandidateId().equals(value.getCandidateId())
            && (filter.getQ() == null || matching.contains(app.getCandidateId()) || contains(value, filter.getQ()))
            && filter.getConditions().stream().allMatch(condition -> contains(value, condition))
            && matches(value, filter);
      } catch (PlatformException error) {
        if (ResumeFactErrorCode.RESUME_FACTS_REQUIRED.matches(error.code()))
          return false;
        throw error;
      }
    }).map(Application::getId).forEach(result::add);
    return result;
  }

  private static boolean contains(ResumeFactSnapshotData facts, String query) {
    var values = new ArrayList<String>();
    var candidate = facts.getCandidate();
    values.addAll(Arrays.asList(candidate.fullName(), candidate.email(), candidate.phone(),
        candidate.currentRole(), candidate.city(), candidate.jobIntention()));
    facts.getSkills().forEach(skill -> values.add(skill.name()));
    facts.getSimpleFacts().forEach(fact -> values.add(fact.name()));
    facts.getEducations().forEach(education -> values.addAll(Arrays.asList(
        education.school(), education.degree(), education.major())));
    facts.getWorkExperiences().forEach(work -> values.addAll(Arrays.asList(
        work.employer(), work.role(), work.description())));
    facts.getProjectExperiences().forEach(project -> {
      values.addAll(Arrays.asList(project.projectName(), project.role(), project.description()));
      values.addAll(project.technologies());
    });
    String text = String.join(" ", values.stream().filter(value -> value != null).toList())
        .toLowerCase(Locale.ROOT);
    return Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+"))
        .allMatch(text::contains);
  }

  private boolean matches(ResumeFactSnapshotData facts, ApplicationCandidateFilterDTO filter) {
    var candidate = facts.getCandidate();
    if (filter.getGender() != null && !filter.getGender().equalsIgnoreCase(candidate.gender())) return false;
    if (filter.getMinAge() != null && (candidate.age() == null || candidate.age() < filter.getMinAge())) return false;
    if (filter.getMaxAge() != null && (candidate.age() == null || candidate.age() > filter.getMaxAge())) return false;
    if (!filter.getDegrees().isEmpty()) {
      var degrees = DegreeQueryAliases.expand(filter.getDegrees());
      if (facts.getEducations().stream().noneMatch(education -> degrees.contains(education.degree()))) return false;
    }
    if (filter.getMinWorkYears() != null || filter.getMaxWorkYears() != null) {
      var years = ResumeExperienceYears.calculate(facts.getWorkExperiences(), clock);
      if (years == null || filter.getMinWorkYears() != null && years.compareTo(filter.getMinWorkYears()) < 0
          || filter.getMaxWorkYears() != null && years.compareTo(filter.getMaxWorkYears()) >= 0) return false;
    }
    return true;
  }
}
