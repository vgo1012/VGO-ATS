package com.ezaigc.hr.intelligence.search.application.assembler;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource.ProjectionItem;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeExperienceYears;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource.State;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** 搜索文档对象创建工厂。 */
@Component
public class SearchDocumentFactory {
  @Resource private Clock clock;

  /** 无事实占位和删除墓碑保留候选人、简历两个业务版本。 */
  public SearchDocument create(State state) {
    if (state == null || state.candidateId() == null || state.candidateId().isBlank()
        || state.resumeVersionNo() < 0) {
      throw new IllegalArgumentException("current candidate search state is required");
    }
    if (!state.deleted() && state.facts() != null) {
      if (!state.candidateId().equals(state.facts().getCandidateId())
          || !Objects.equals(state.resumeVersionId(), state.facts().getResumeVersionId())) {
        throw new IllegalArgumentException("search facts do not belong to the current candidate version");
      }
      var document = create(state.facts(), state.updateTime(), state.sourceChannel(), state.resumeVersionNo(),
          state.fullName(), state.email(), state.phone());
      document.setCandidateVersion(state.candidateVersion());
      return document;
    }
    var document = new SearchDocument(state.candidateId(), null, null,
        state.deleted() ? null : state.fullName(), null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        state.deleted() ? "" : contactText(state.fullName(), state.email(), state.phone()), state.updateTime());
    // DELETED 在领域中没有恢复路径；不能用 ES delete 的短期 gc_deletes 充当持久屏障。
    document.setProjectionVersion(state.deleted() ? Long.MAX_VALUE
        : Math.max(1, (long) state.resumeVersionNo() << 32));
    document.setCandidateVersion(state.candidateVersion());
    return document;
  }

  public SearchDocument create(
      ResumeFactSnapshotData f, Instant createTime, String sourceChannel, int resumeVersionNo) {
    return create(f, createTime, sourceChannel, resumeVersionNo, f.getCandidate().fullName(),
        f.getCandidate().email(), f.getCandidate().phone());
  }

  public SearchDocument create(ProjectionItem item) {
    var document = create(item.facts(), item.createTime(), item.sourceChannel(), item.resumeVersionNo(),
        item.fullName(), item.email(), item.phone());
    document.setCandidateVersion(item.candidateVersion());
    return document;
  }

  private SearchDocument create(
      ResumeFactSnapshotData f, Instant createTime, String sourceChannel, int resumeVersionNo,
      String fullName, String email, String phone) {
    if (resumeVersionNo < 1 || f.getSnapshotVersion() < 1) {
      throw new IllegalArgumentException("search projection requires positive business versions");
    }
    var simple = f.getSimpleFacts();
    List<String> cert = names(simple, "CERTIFICATE", "CREDENTIAL"),
        languages = names(simple, "LANGUAGE"),
        industries = names(simple, "INDUSTRY_TAG"),
        tags = names(simple, "RESPONSIBILITY_TAG", "TAG");
    List<String> employers =
        clean(f.getWorkExperiences().stream().map(ResumeFactSnapshotData.Work::employer));
    List<String> roles =
        clean(
            Stream.concat(
                f.getWorkExperiences().stream().map(ResumeFactSnapshotData.Work::role),
                f.getProjectExperiences().stream().map(ResumeFactSnapshotData.Project::role)));
    List<String> projects =
        clean(f.getProjectExperiences().stream().map(ResumeFactSnapshotData.Project::projectName));
    List<String> skills =
        clean(
            Stream.concat(
                f.getSkills().stream().map(ResumeFactSnapshotData.Skill::name),
                f.getProjectExperiences().stream().flatMap(v -> v.technologies().stream())));
    List<String> degrees =
        clean(f.getEducations().stream().map(ResumeFactSnapshotData.Education::degree));
    String text =
        String.join(
            " ",
            clean(
                Stream.of(
                        Stream.of(
                            fullName,
                            email,
                            phone,
                            f.getCandidate().currentRole(),
                            f.getCandidate().city(),
                            f.getCandidate().jobIntention()),
                        employers.stream(),
                        roles.stream(),
                        projects.stream(),
                        skills.stream(),
                        cert.stream(),
                        languages.stream(),
                        degrees.stream(),
                        industries.stream(),
                        tags.stream(),
                        f.getWorkExperiences().stream().map(ResumeFactSnapshotData.Work::description),
                        f.getProjectExperiences().stream()
                            .map(ResumeFactSnapshotData.Project::description))
                    .flatMap(v -> v)));
    var document = new SearchDocument(
        f.getCandidateId(),
        f.getResumeVersionId(),
        f.getSnapshotId(),
        fullName,
        f.getCandidate().currentRole(),
        f.getCandidate().city(),
        employers,
        roles,
        projects,
        skills,
        cert,
        languages,
        degrees,
        industries,
        tags,
        workYears(f.getWorkExperiences()),
        sourceChannel,
        f.getCandidate().jobIntention(),
        f.getCandidate().gender(),
        f.getCandidate().age(),
        text,
        createTime);
    // 两个正 int 按字典序组成正 long；较旧简历的任意事实版本都不能覆盖较新简历。
    document.setProjectionVersion(((long) resumeVersionNo << 32) | f.getSnapshotVersion());
    return document;
  }

  private BigDecimal workYears(List<ResumeFactSnapshotData.Work> work) {
    return ResumeExperienceYears.calculate(work, clock);
  }

  private static List<String> names(
      List<ResumeFactSnapshotData.SimpleFact> facts, String... types) {
    Set<String> accepted = Set.of(types);
    return clean(
        facts.stream()
            .filter(v -> accepted.contains(v.type()))
            .map(ResumeFactSnapshotData.SimpleFact::name));
  }

  private static List<String> clean(Stream<String> values) {
    return values
        .filter(Objects::nonNull)
        .map(String::strip)
        .filter(v -> !v.isBlank())
        .distinct()
        .toList();
  }

  private static String contactText(String fullName, String email, String phone) {
    return String.join(" ", clean(Stream.of(fullName, email, phone)));
  }

}
