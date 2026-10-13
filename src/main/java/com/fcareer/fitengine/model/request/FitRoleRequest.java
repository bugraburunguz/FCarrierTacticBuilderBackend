package com.fcareer.fitengine.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitRoleRequest {

    @NotNull
    private Long playerId;

    @NotBlank
    private String roleId;

    private Short gameVersionId;

    private String position;

    /** Opsiyonel geçersiz kılma: v1 | v3 (yoksa yapılandırmadaki engineVersion). */
    @jakarta.validation.constraints.Pattern(regexp = "v1|v3")
    private String engineVersion;

    @Size(max = 6)
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
