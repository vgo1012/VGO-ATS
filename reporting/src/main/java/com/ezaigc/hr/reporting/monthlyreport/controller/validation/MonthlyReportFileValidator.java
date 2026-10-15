package com.ezaigc.hr.reporting.monthlyreport.controller.validation;

import com.ezaigc.hr.talent.api.ResumeUploadPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;

public class MonthlyReportFileValidator implements ConstraintValidator<ValidMonthlyReportFile, MultipartFile> {
    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty() || file.getSize() > ResumeUploadPolicy.MAX_FILE_SIZE_BYTES) return false;
        String name = file.getOriginalFilename();
        return name != null && name.length() <= 200 && !name.contains("/") && !name.contains("\\")
                && name.codePoints().noneMatch(Character::isISOControl)
                && name.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }
}
