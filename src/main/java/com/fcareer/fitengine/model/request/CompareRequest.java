package com.fcareer.fitengine.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
public class CompareRequest {

    @NotEmpty
    @Size(max = 40)
    private List<Long> ids;

    @NotBlank
    private String roleId;

    private Short gameVersionId;

    private String position;

    @Pattern(regexp = "v1|v3")
    private String engineVersion;

    @Size(max = 6)
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
