package com.fcareer.tactics.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SlotSelection {

    @NotBlank
    private String slotId;

    @Size(max = 6)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private String roleId;
}
