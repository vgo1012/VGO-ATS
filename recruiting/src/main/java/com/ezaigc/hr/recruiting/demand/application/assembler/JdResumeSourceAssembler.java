package com.ezaigc.hr.recruiting.demand.application.assembler;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdResumeSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdResumeSourceRequest;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.MailSourceMessageEntity;
import com.ezaigc.hr.recruiting.demand.domain.response.JdResumeSourceResponse;
import com.ezaigc.hr.recruiting.demand.domain.response.MailSourceMessageResponse;
import org.springframework.stereotype.Component;
@Component
public class JdResumeSourceAssembler {
  public CreateJdResumeSourceDTO toDTO(CreateJdResumeSourceRequest r) {
    return new CreateJdResumeSourceDTO(r.getCandidateId(),r.getResumeVersionId(),r.getSourceMessageId(),r.getSenderEmail(),r.getSubject(),r.getPlainBody());
  }
  public JdResumeSourceResponse toResponse(JdResumeSourceEntity r) {
    return new JdResumeSourceResponse(r.getId(),r.getMailSourceId(),r.getCandidateId(),r.getCandidateName(),r.getResumeVersionId(),r.getOriginalFileName(),r.getContentSha256(),r.getCreateTime());
  }
  public MailSourceMessageResponse toResponse(MailSourceMessageEntity r) {
    return new MailSourceMessageResponse(r.getId(),r.getJdVersionId(),r.getSourceMessageId(),r.getSenderEmail(),r.getRecipientEmail(),r.getSubject(),r.getPlainBody(),r.getDeliveryType(),r.getCreateId(),r.getCreateTime());
  }
}
