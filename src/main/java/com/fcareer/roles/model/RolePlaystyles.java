package com.fcareer.roles.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePlaystyles {
    @Builder.Default
    private List<String> priority = new ArrayList<>();
    @Builder.Default
    private List<String> bonus = new ArrayList<>();
}
