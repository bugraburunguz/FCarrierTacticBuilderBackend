package com.fcareer.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import com.fcareer.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 72)
    @MaxUtf8Bytes(72)
    private String password;
}
