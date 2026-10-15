package com.ezaigc.hr.intelligence.search.application.assembler;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.request.TalentSearchRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/** 人才搜索边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class TalentSearchAssembler {

  public SearchCriteria toDTO(TalentSearchRequest request) {
    SearchCriteria dto = new SearchCriteria();
    dto.setQuery(request.getQ());
    dto.setConditions(copy(request.getConditions()));
    dto.setSkills(copy(request.getSkills()));
    dto.setEmployers(copy(request.getEmployers()));
    dto.setRoles(copy(request.getRoles()));
    dto.setProjects(copy(request.getProjects()));
    dto.setCertificates(copy(request.getCertificates()));
    dto.setLanguages(copy(request.getLanguages()));
    dto.setDegrees(copy(request.getDegrees()));
    dto.setCities(copy(request.getCities()));
    dto.setIndustries(copy(request.getIndustries()));
    dto.setTags(copy(request.getTags()));
    dto.setMinWorkYears(request.getMinWorkYears());
    dto.setMaxWorkYears(request.getMaxWorkYears());
    dto.setScope(request.getScope());
    dto.setJdId(request.getJdId());
    dto.setPoolStage(request.getPoolStage());
    dto.setCandidateIds(copy(request.getCandidateIds()));
    dto.setSourceChannel(request.getSourceChannel());
    dto.setIntention(request.getIntention());
    dto.setGender(request.getGender());
    dto.setMinAge(request.getMinAge());
    dto.setMaxAge(request.getMaxAge());
    dto.setUpdatedFrom(request.getUpdatedFrom());
    dto.setUpdatedTo(request.getUpdatedTo());
    dto.setNaturalCondition(request.getNaturalCondition());
    return dto;
  }

  private static List<String> copy(List<String> values) {
    return values == null
        ? List.of()
        : values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
  }
}
