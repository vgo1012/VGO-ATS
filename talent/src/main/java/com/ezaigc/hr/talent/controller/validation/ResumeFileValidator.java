package com.ezaigc.hr.talent.controller.validation;

import com.ezaigc.hr.talent.domain.validation.ResumeUploadRules;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

/** Multipart 边界校验器；文件内容真实性仍由后续恶意文件扫描和签名检测负责。 */
public final class ResumeFileValidator
    implements ConstraintValidator<ValidResumeFile, MultipartFile> {

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
    if (file == null || !ResumeUploadRules.isValidSize(file.getSize())) return false;
    String fileName = ResumeUploadRules.normalizeFileName(file.getOriginalFilename());
    return ResumeUploadRules.hasSupportedExtension(fileName);
  }
}
