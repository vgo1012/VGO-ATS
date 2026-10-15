package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_PASSWORD_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_WORK_EMAIL_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthChangePasswordRequest {

    @ToString.Exclude
    private @NotBlank
    @Size(max = MAX_WORK_EMAIL_LENGTH) String currentPassword;
    @ToString.Exclude
    private @NotBlank
    @Size(max = MAX_PASSWORD_LENGTH) String newPassword;

}
