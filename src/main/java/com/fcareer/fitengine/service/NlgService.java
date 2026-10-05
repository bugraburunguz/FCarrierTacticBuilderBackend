package com.fcareer.fitengine.service;

import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.fitengine.model.WeaponState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NlgService {

    private static final String SENTENCE_SEPARATOR = " ";

    private final MessageService messageService;

    public String attrLabel(Attr attr) {
        return messageService.getMessage(attr.getMessageKey());
    }

    public String weaponReason(Weapon weapon, int value, int effectiveMin, WeaponState state, boolean psPlusCompensated) {
        String label = attrLabel(weapon.getAttr());
        String reason = switch (state) {
            case GREEN -> messageService.getMessage("nlg.weapon.green", label, value, weapon.getTarget());
            case YELLOW -> messageService.getMessage("nlg.weapon.yellow", label, value, weapon.getTarget());
            case RED -> messageService.getMessage("nlg.weapon.red", label, value, effectiveMin);
        };
        if (psPlusCompensated && state != WeaponState.GREEN) {
            return reason + SENTENCE_SEPARATOR + messageService.getMessage("nlg.weapon.psPlusCompensated");
        }
        return reason;
    }

    public String badgeText(WeaponState badge, List<String> weakWeapons) {
        String weapons = String.join(", ", weakWeapons);
        return switch (badge) {
            case GREEN -> messageService.getMessage("nlg.badge.green");
            case YELLOW -> messageService.getMessage("nlg.badge.yellow", weapons);
            case RED -> messageService.getMessage("nlg.badge.red", weapons);
        };
    }

    public String outOfPosition(String position) {
        return messageService.getMessage("nlg.outOfPosition", position);
    }

    public String floorReason(Attr attr, int value, int floor) {
        return messageService.getMessage("nlg.floor", attrLabel(attr), value, floor);
    }

    public String accelerateReason(boolean match, AcceleRate playerRate) {
        String rate = messageService.getMessage("accelerate." + playerRate.name());
        if (match) {
            return messageService.getMessage("nlg.accelerate.match", rate);
        }
        return messageService.getMessage("nlg.accelerate.mismatch", rate);
    }

    public String playstyleReason(String playstyle, boolean plus) {
        String label = playstyleLabel(playstyle);
        if (plus) {
            return messageService.getMessage("nlg.playstyle.plus", label);
        }
        return messageService.getMessage("nlg.playstyle.has", label);
    }

    public String playstyleLabel(String playstyle) {
        return messageService.getMessage("ps." + playstyle);
    }

    public String tagNote(FitTag tag, int value, int expected, WeaponState state) {
        String label = attrLabel(tag.primaryWeapon());
        return switch (state) {
            case GREEN -> messageService.getMessage("nlg.tag.ok", tag.getLabelTr(), label, value, expected);
            case YELLOW -> messageService.getMessage("nlg.tag.weak", tag.getLabelTr(), label, value, expected);
            case RED -> messageService.getMessage("nlg.tag.cannot", tag.getLabelTr(), label, value, expected);
        };
    }

    public String weakLink(String slotId, String playerName, String roleName, double score, String badgeText) {
        return messageService.getMessage("nlg.weakLink", slotId, playerName, roleName, Math.round(score), badgeText);
    }

    public String emptySlot(String slotId) {
        return messageService.getMessage("nlg.emptySlot", slotId);
    }

    public String squadSummary(double squadFit, Double intentFit) {
        if (Objects.isNull(intentFit)) {
            return messageService.getMessage("nlg.squad.summary.noIntent", Math.round(squadFit));
        }
        String key = intentFit < squadFit - 10 ? "nlg.squad.summary.intentGap" : "nlg.squad.summary";
        return messageService.getMessage(key, Math.round(squadFit), Math.round(intentFit));
    }

    public String attackPattern(List<SlotPattern> patterns) {
        String body = patterns.stream()
                .filter(p -> Objects.nonNull(p.sentence()) && !p.sentence().isBlank())
                .map(p -> messageService.getMessage("nlg.pattern.slot", p.slotId(), lowerFirst(p.sentence())))
                .collect(Collectors.joining("; "));
        if (body.isBlank()) {
            return messageService.getMessage("nlg.pattern.none");
        }
        return messageService.getMessage("nlg.pattern.prefix") + SENTENCE_SEPARATOR + body + ".";
    }

    private String lowerFirst(String sentence) {
        return Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
    }

    public record SlotPattern(String slotId, String sentence) {
    }
}
