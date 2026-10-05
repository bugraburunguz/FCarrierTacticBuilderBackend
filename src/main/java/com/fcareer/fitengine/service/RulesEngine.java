package com.fcareer.fitengine.service;

import com.fcareer.fitengine.model.AssignedSlot;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.RuleCondition;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.fitengine.model.response.RuleFinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

@Slf4j
@Service
public class RulesEngine {

    private static final double TRUE = 1;
    private static final double FALSE = 0;

    public List<RuleFinding> evaluate(List<AssignedSlot> slots, List<TacticRuleDefinition> rules) {
        return rules.stream()
                .filter(rule -> matches(slots, rule))
                .map(rule -> RuleFinding.builder()
                        .ruleId(rule.getId())
                        .severity(rule.getSeverity())
                        .title(rule.getTitleTr())
                        .message(rule.getMessageTr())
                        .fix(rule.getFixTr())
                        .build())
                .toList();
    }

    private boolean matches(List<AssignedSlot> slots, TacticRuleDefinition rule) {
        if (rule.getConditions().isEmpty()) {
            return false;
        }
        for (RuleCondition condition : rule.getConditions()) {
            OptionalDouble actual = measure(slots, condition);
            if (actual.isEmpty() || !condition.getOp().test(actual.getAsDouble(), condition.getValue())) {
                return false;
            }
        }
        log.debug("Rule matched id : {}", rule.getId());
        return true;
    }

    private OptionalDouble measure(List<AssignedSlot> slots, RuleCondition condition) {
        List<AssignedSlot> filtered = slots.stream().filter(s -> inScope(s, condition)).toList();
        return switch (condition.getMetric()) {
            case SLOT_COUNT -> OptionalDouble.of(filtered.size());
            case SLOT_TAG_COUNT -> OptionalDouble.of(filtered.stream().filter(s -> hasAnyTag(s, condition.getTags())).count());
            case AVG_ASSIGNED_ATTR -> averageAttr(filtered, condition.getAttrs());
            case ALL_SLOTS_HAVE_TAG -> OptionalDouble.of(allHaveTag(filtered, condition.getTags()) ? TRUE : FALSE);
        };
    }

    private boolean inScope(AssignedSlot slot, RuleCondition condition) {
        boolean groupScoped = isPresent(condition.getSlotGroups());
        boolean roleScoped = isPresent(condition.getBaseRoles());
        if (!groupScoped && !roleScoped) {
            return true;
        }
        boolean groupMatch = groupScoped && condition.getSlotGroups().contains(slot.slot().getSlotGroup());
        boolean roleMatch = roleScoped && Objects.nonNull(slot.slot().getResolvedRole())
                && condition.getBaseRoles().contains(slot.slot().getResolvedRole().getBaseRole());
        return groupMatch || roleMatch;
    }

    private boolean hasAnyTag(AssignedSlot slot, List<String> tags) {
        if (!isPresent(tags)) {
            return false;
        }
        return tags.stream().anyMatch(slot.slot()::hasTag);
    }

    private boolean allHaveTag(List<AssignedSlot> slots, List<String> tags) {
        if (slots.isEmpty()) {
            return false;
        }
        return slots.stream().allMatch(s -> hasAnyTag(s, tags));
    }

    private OptionalDouble averageAttr(List<AssignedSlot> slots, List<String> attrKeys) {
        if (!isPresent(attrKeys)) {
            return OptionalDouble.empty();
        }
        List<Attr> attrs = attrKeys.stream().map(Attr::requireKey).toList();
        return slots.stream()
                .filter(s -> Objects.nonNull(s.player()))
                .mapToDouble(s -> attrs.stream().mapToInt(a -> s.player().attr(a)).average().orElse(0))
                .average();
    }

    private boolean isPresent(List<String> values) {
        return Objects.nonNull(values) && !values.isEmpty();
    }
}
