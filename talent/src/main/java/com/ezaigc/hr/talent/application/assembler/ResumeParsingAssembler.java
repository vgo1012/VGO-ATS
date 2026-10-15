package com.ezaigc.hr.talent.application.assembler;

import com.ezaigc.hr.talent.domain.entity.FactBundle;
import com.ezaigc.hr.talent.domain.response.EducationFactResponse;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.domain.response.FactSnapshotResponse;
import com.ezaigc.hr.talent.domain.response.ProjectFactResponse;
import com.ezaigc.hr.talent.domain.response.SimpleFactResponse;
import com.ezaigc.hr.talent.domain.response.SkillFactResponse;
import com.ezaigc.hr.talent.domain.response.WorkFactResponse;
import org.springframework.stereotype.Component;
import com.ezaigc.hr.talent.domain.service.FactDateMetadata;
import com.ezaigc.hr.talent.domain.service.CandidateBirthMetadata;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;

/** 简历解析边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class ResumeParsingAssembler {
  public FactBundleResponse toResponse(FactBundle v) {
    return new FactBundleResponse(
        new FactSnapshotResponse(
            v.getSnapshot().getId(),
            v.getSnapshot().getResumeVersionId(),
            v.getSnapshot().getParseSnapshotId(),
            v.getSnapshot().getPreviousSnapshotId(),
            v.getSnapshot().getSnapshotVersion(),
            v.getSnapshot().getSourceType(),
            v.getSnapshot().getCurrentMarker(),
            v.getSnapshot().getFullName(),
            v.getSnapshot().getEmail(),
            v.getSnapshot().getPhone(),
            v.getSnapshot().getCurrentRole(),
            v.getSnapshot().getCity(),
            v.getSnapshot().getAge(),
            v.getSnapshot().getGender(),
            v.getSnapshot().getJobIntention(),
            v.getSnapshot().getCorrectedByUserId(),
            v.getSnapshot().getCorrectionReason(),
            v.getSnapshot().getCreateTime(),
            CandidateBirthMetadata.birthDate(v.getSnapshot()),
            CandidateBirthMetadata.ageAsOf(v.getSnapshot())),
        v.getWorkExperiences().stream()
            .map(
                x ->
                    new WorkFactResponse(
                        FactDateMetadata.read(x.getExt(), "startDateText", x.getStartDate()),
                        FactDateMetadata.read(x.getExt(), "endDateText", x.getEndDate()),
                        x.getId(),
                        x.getFactSnapshotId(),
                        x.getItemKey(),
                        x.getEmployer(),
                        x.getRoleName(),
                        x.getStartDate(),
                        x.getEndDate(),
                        x.getDescriptionText(),
                        x.getResponsibilitiesJson(),
                        x.getAchievementsJson(), FactOrderMetadata.index(x.getExt())))
            .toList(),
        v.getProjectExperiences().stream()
            .map(
                x ->
                    new ProjectFactResponse(
                        FactDateMetadata.read(x.getExt(), "startDateText", x.getStartDate()),
                        FactDateMetadata.read(x.getExt(), "endDateText", x.getEndDate()),
                        x.getId(),
                        x.getFactSnapshotId(),
                        x.getItemKey(),
                        x.getProjectName(),
                        x.getRoleName(),
                        x.getStartDate(),
                        x.getEndDate(),
                        x.getDescriptionText(),
                        x.getTechnologiesJson(),
                        x.getAchievementsJson(), FactOrderMetadata.index(x.getExt())))
            .toList(),
        v.getEducations().stream()
            .map(
                x ->
                    new EducationFactResponse(
                        FactDateMetadata.read(x.getExt(), "startDateText", x.getStartDate()),
                        FactDateMetadata.read(x.getExt(), "endDateText", x.getEndDate()),
                        x.getId(),
                        x.getFactSnapshotId(),
                        x.getItemKey(),
                        x.getSchool(),
                        x.getDegreeName(),
                        x.getMajor(),
                        x.getStartDate(),
                        x.getEndDate(),
                        x.getLearningMode()))
            .toList(),
        v.getSkills().stream()
            .map(
                x ->
                    new SkillFactResponse(
                        x.getId(),
                        x.getFactSnapshotId(),
                        x.getItemKey(),
                        x.getSkillName(),
                        x.getLevelName(),
                        x.getYearsValue()))
            .toList(),
        v.getSimpleFacts().stream()
            .map(
                x ->
                    new SimpleFactResponse(
                        FactDateMetadata.read(x.getExt(), "startDateText", x.getStartDate()),
                        FactDateMetadata.read(x.getExt(), "endDateText", x.getEndDate()),
                        x.getId(),
                        x.getFactSnapshotId(),
                        x.getFactType(),
                        x.getItemKey(),
                        x.getNameValue(),
                        x.getLevelValue(),
                        x.getIssuerValue(),
                        x.getStartDate(),
                        x.getEndDate(), FactOrderMetadata.index(x.getExt())))
            .toList());
  }
}
