package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCondition {
    private RuleMetric metric;
    private List<String> slotGroups;
    private List<String> baseRoles;
    private List<String> tags;
    private List<String> attrs;
    private RuleOperator op;
    private double value;
}
