package com.ezaigc.hr.talent.domain.response;

import java.util.List;
import lombok.Value;

@Value
public class CandidatePoolStatusResponse {
  String candidateId;
  String candidateName;
  String resumeVersionId;
  boolean libraryEligible;
  boolean screenedOut;
  boolean canScreenOut;
  int openIdentityConflictCount;
  List<String> stages;
  List<CandidateJobContextResponse> jobContexts;
  ResumeLifecycleResponse lifecycle;
  public CandidatePoolStatusResponse(String candidateId, String candidateName, String resumeVersionId,
      boolean libraryEligible, boolean screenedOut, boolean canScreenOut, int openIdentityConflictCount,
      List<String> stages, List<CandidateJobContextResponse> jobContexts) {
    this(candidateId,candidateName,resumeVersionId,libraryEligible,screenedOut,canScreenOut,
        openIdentityConflictCount,stages,jobContexts,null);
  }
  public CandidatePoolStatusResponse(String candidateId, String candidateName, String resumeVersionId,
      boolean libraryEligible, boolean screenedOut, boolean canScreenOut, int openIdentityConflictCount,
      List<String> stages, List<CandidateJobContextResponse> jobContexts, ResumeLifecycleResponse lifecycle) {
    this.candidateId=candidateId; this.candidateName=candidateName; this.resumeVersionId=resumeVersionId;
    this.libraryEligible=libraryEligible; this.screenedOut=screenedOut; this.canScreenOut=canScreenOut;
    this.openIdentityConflictCount=openIdentityConflictCount; this.stages=stages; this.jobContexts=jobContexts;
    this.lifecycle=lifecycle;
  }
}
