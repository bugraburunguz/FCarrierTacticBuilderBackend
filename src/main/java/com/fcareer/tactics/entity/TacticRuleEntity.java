package com.fcareer.tactics.entity;

import com.fcareer.fitengine.model.RuleCondition;
import com.fcareer.fitengine.model.RuleSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "tactic_rule")
public class TacticRuleEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleSeverity severity;

    @Column(nullable = false)
    private String titleTr;

    @Column(nullable = false)
    private String messageTr;

    private String fixTr;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<RuleCondition> conditions;

    @Column(nullable = false)
    private boolean enabled = true;
}
