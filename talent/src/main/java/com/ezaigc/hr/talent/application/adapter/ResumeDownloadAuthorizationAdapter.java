package com.ezaigc.hr.talent.application.adapter;

import com.ezaigc.hr.talent.api.ResumeDownloadAuthorizationService;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ResumeDownloadAuthorizationAdapter implements ResumeDownloadAuthorizationService {
  @Resource private TalentIntakeApplicationManager manager;

  @Override
  public String authorize(String resumeVersionId) {
    return manager.authorizeDownload(resumeVersionId).getToken();
  }
}
