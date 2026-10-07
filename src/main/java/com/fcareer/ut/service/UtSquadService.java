package com.fcareer.ut.service;

import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.SetupModifier;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.service.TacticResolver;
import com.fcareer.ut.model.UtMetaModels.MetaCard;
import com.fcareer.ut.model.UtSquadModels.InlineCard;
import com.fcareer.ut.model.UtSquadModels.SlotEvaluation;
import com.fcareer.ut.model.UtSquadModels.SlotInput;
import com.fcareer.ut.model.UtSquadModels.SquadEvaluateRequest;
import com.fcareer.ut.model.UtSquadModels.SquadEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtSquadService {

    private static final String SLOT_ID = "SLOT";

    private final PlayerService playerService;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final RoleFitService roleFitService;
    private final SetupModifier setupModifier;
    private final UtMetaService utMetaService;

    public SquadEvaluation evaluate(SquadEvaluateRequest request) {
        List<Long> ids = request.slots().stream().filter(s -> Objects.isNull(s.card())).map(SlotInput::playerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, FitPlayer> players = ids.isEmpty() ? Map.of() : playerService.fitPlayers(ids, request.gameVersionId()).stream()
                .collect(Collectors.toMap(FitPlayer::getId, Function.identity()));
        List<SlotEvaluation> slots = new ArrayList<>();
        for (SlotInput slot : request.slots()) {
            slots.add(evaluateSlot(slot, resolvePlayer(slot, players), request));
        }
        double meta = slots.stream().mapToDouble(s -> s.card().metaRating()).average().orElse(0);
        double overall = slots.stream().mapToInt(s -> s.card().overall()).average().orElse(0);
        return new SquadEvaluation(slots, round1(meta), round1(overall), true);
    }

    private FitPlayer resolvePlayer(SlotInput slot, Map<Long, FitPlayer> catalogPlayers) {
        if (Objects.nonNull(slot.card())) {
            return inlinePlayer(slot, slot.card());
        }
        FitPlayer player = Objects.isNull(slot.playerId()) ? null : catalogPlayers.get(slot.playerId());
        if (Objects.isNull(player)) {
            throw new InvalidTacticException("slot card " + slot.slotId());
        }
        return player;
    }

    private FitPlayer inlinePlayer(SlotInput slot, InlineCard card) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        card.attrs().forEach((key, value) -> Attr.fromKey(key).ifPresent(attr -> attrs.put(attr, value)));
        List<String> positions = Objects.isNull(card.positions()) || card.positions().isEmpty() ? List.of(slot.position()) : card.positions();
        return FitPlayer.builder().id(-1L).name(Objects.requireNonNullElse(card.name(), slot.slotId())).overall(card.overall())
                .positions(positions).attrs(attrs).playstyles(Map.of()).build();
    }

    private SlotEvaluation evaluateSlot(SlotInput slot, FitPlayer player, SquadEvaluateRequest request) {
        MetaCard card = utMetaService.card(player, slot.position(), true);
        if (Objects.isNull(slot.roleId()) || slot.roleId().isBlank()) {
            return new SlotEvaluation(slot.slotId(), slot.position(), card, null, null, null, null, null, null, null);
        }
        FitRole role = roleService.getFitRole(slot.roleId());
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, slot.position(), null, slot.roleId(), List.of(), request.setup());
        RoleFitResponse fit = roleFitService.calculate(player, role, intent, slot.position());
        double delta = setupModifier.delta(player, intent);
        double score = Math.max(0, Math.min(100, fit.getScore() + delta));
        String reason = Objects.isNull(fit.getReasons()) || fit.getReasons().isEmpty() ? null : fit.getReasons().get(0);
        return new SlotEvaluation(slot.slotId(), slot.position(), card, role.getId(), role.getName(), round1(score), fit.getBadge(),
                fit.getBadgeText(), reason, Objects.isNull(intent.getSetup()) ? null : round1(delta));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
