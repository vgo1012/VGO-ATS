package com.ezaigc.hr.talent.application.adapter;

import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.api.MailResumeIntakeService;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.dto.CreateIntakeBatchDTO;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class MailResumeIntakeAdapter implements MailResumeIntakeService {

  @Resource private BackgroundActorExecutor backgroundActors;
  @Resource private TalentIntakeApplicationManager intakes;
  @Resource private ObjectStoragePort objects;

  @Override
  public Result importResume(Command command) {
    return backgroundActors.execute(command.ownerUserId(), () -> importAsOwner(command));
  }

  private Result importAsOwner(Command command) {
    String key = "mail:" + command.messageId();
    var batch = intakes.createBatch(
        new CreateIntakeBatchDTO(
            command.sourceChannelId(),
            command.ownerUserId(),
            command.ownerDepartmentId(),
            command.jdVersionId()),
        key + ":batch");
    ResumeIntake intake = intakes.upload(
        batch.getId(),
        new ResumeUploadDTO(
            command.fileName(), command.sizeBytes(), () -> objects.open(command.objectKey())),
        key + ":file");
    intakes.completeBatch(batch.getId());
    return new Result(intake.getId(), intake.getStatus());
  }
}
