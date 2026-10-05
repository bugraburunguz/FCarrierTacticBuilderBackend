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
public class TacticRuleDefinition {
    private String id;
    private RuleSeverity severity;
    private String titleTr;
    private String messageTr;
    private String fixTr;
    private List<RuleCondition> conditions;
}
