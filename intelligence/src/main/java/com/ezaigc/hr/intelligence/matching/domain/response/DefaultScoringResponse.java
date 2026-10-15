package com.ezaigc.hr.intelligence.matching.domain.response;

import lombok.Value;

/** 所有岗位共同使用的公司默认规则及权重，只读。 */
@Value
public class DefaultScoringResponse {
  MatchPlanDetailsResponse scoringConfiguration;
  WeightProfileDetailsResponse weightProfile;
}
