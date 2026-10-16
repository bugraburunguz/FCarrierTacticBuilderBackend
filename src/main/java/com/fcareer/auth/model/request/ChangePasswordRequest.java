package com.fcareer.auth.model.request;

import com.fcareer.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** V3-07a: şifre değiştir; yeni şifre kayıtla aynı kurallar (8–72 karakter, bcrypt bayt sınırı). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank
    @Size(max = 72)
    @MaxUtf8Bytes(72)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    @MaxUtf8Bytes(72)
    private String newPassword;
}
