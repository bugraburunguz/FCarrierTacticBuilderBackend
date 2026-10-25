package com.fcareer.tacticcode.service;

import com.fcareer.common.exception.EaRoleUnavailableException;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.model.EaCodecData;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.model.EaTactic.EaSlot;
import com.fcareer.tacticcode.model.TacticCodeModels.ExportResponse;
import com.fcareer.tacticcode.model.TacticCodeModels.ImportResponse;
import com.fcareer.tacticcode.model.TacticCodeModels.ImportedSlot;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.fcareer.roles.seed.RoleLibraryGenerator.roleId;

@Slf4j
@Service
@RequiredArgsConstructor
public class TacticCodeService {

    private static final int DEFAULT_LINE_HEIGHT = 50;
    private static final int MIN_LINE_HEIGHT = 1;
    private static final int MAX_LINE_HEIGHT = 100;
    private static final String SHORT = "short";
    private static final String COUNTER = "counter";

    private final EaTacticCodec codec;
    private final TacticResolver tacticResolver;
    private final FormationRegistry formationRegistry;
    private final RoleService roleService;

    public ExportResponse export(TacticRequest request) {
        TacticResolver.ResolvedTactic resolved = tacticResolver.resolve(request);
        FormationDefinition ours = formationRegistry.get(resolved.formation());
        EaCodecData.Formation ea = eaFormationFor(ours)
                .orElseThrow(() -> new InvalidTacticException("formation not available as an EA tactic code: " + ours.id()));
        List<String> warnings = new ArrayList<>();
        Map<Integer, IntentProfile> ourSlotByEaIndex = slotMapping(ours, ea, resolved.slots());
        List<EaSlot> slots = new ArrayList<>();
        for (int i = 0; i < ea.positions().size(); i++) {
            slots.add(eaSlot(ea, i, ourSlotByEaIndex.get(i), warnings));
        }
        TeamSetup setup = request.getSetup();
        EaBuildUp buildUp = buildUpOf(setup);
        int lineHeight = lineHeightOf(setup);
        String code = codec.encode(new EaTactic(ea.id(), buildUp, lineHeight, slots));
        log.info("Tactic code exported formation : {} warnings : {}", ours.id(), warnings.size());
        return ExportResponse.builder().code(code).formation(ours.id()).exact(warnings.isEmpty()).warnings(warnings).build();
    }

    /**
     * Replika kodu (REP-03): slotId → (rol adı, focus) ile doğrudan EA kodu üretir. Slot sırası EA kuralıdır (aynı mevkiler sağdan sola); (mevki, rol, focus) EA tablosunda yoksa 422.
     * Dönen kod decode edilince aynı ayarları verir.
     */
    public String exportSlots(FormationDefinition ours, EaBuildUp buildUp, int lineHeight, Map<String, String[]> roleBySlot) {
        EaCodecData.Formation ea = eaFormationFor(ours).orElseThrow(() -> new InvalidTacticException("formation not available as an EA tactic code: " + ours.id()));
        Map<Integer, FormationSlot> mapping = formationSlotMapping(ours, ea);
        List<EaSlot> slots = new ArrayList<>();
        for (int i = 0; i < ea.positions().size(); i++) {
            final int index = i;
            FormationSlot slot = mapping.get(i);
            String[] rf = roleBySlot.get(slot.slotId());
            if (Objects.isNull(rf)) {
                throw new InvalidTacticException("slot " + slot.slotId());
            }
            EaCodecData.Role role = codec.roles().stream().filter(r -> r.position().equals(ea.positions().get(index)) && normalize(r.name()).equals(normalize(rf[0]))).findFirst()
                    .orElseThrow(() -> new EaRoleUnavailableException(slot.slotId() + ": " + rf[0] + " EA'da " + slot.position() + " için yok"));
            int focus = focusIndex(role, rf[1]);
            if (focus < 0) {
                throw new EaRoleUnavailableException(slot.slotId() + ": " + rf[0] + " odağı " + rf[1] + " EA'da yok");
            }
            slots.add(new EaSlot(role.id(), focus));
        }
        return codec.encode(new EaTactic(ea.id(), buildUp, lineHeight, slots));
    }

    /** Formasyonun EA slot sırası (mevkiler; aynı mevkiler sağdan sola). */
    public List<String> eaPositions(FormationDefinition ours) {
        return eaFormationFor(ours).orElseThrow(() -> new InvalidTacticException("formation not available as an EA tactic code: " + ours.id())).positions();
    }

    public ImportResponse importCode(String code) {
        EaTactic tactic;
        try {
            tactic = codec.decode(code).orElseThrow(() -> new InvalidTacticException("code is not a valid EA tactic code"));
        } catch (IllegalArgumentException e) {
            throw new InvalidTacticException(e.getMessage());
        }
        EaCodecData.Formation ea = codec.formation(tactic.formationId()).orElseThrow();
        FormationDefinition ours = ourFormationFor(ea)
                .orElseThrow(() -> new InvalidTacticException("formation not available in this site: " + ea.label()));
        List<String> warnings = new ArrayList<>();
        Map<Integer, FormationSlot> ourSlotByEaIndex = formationSlotMapping(ours, ea);
        Map<String, FitRole> ourRoles = roleService.fitRolesById();
        List<ImportedSlot> slots = new ArrayList<>();
        for (int i = 0; i < tactic.slots().size(); i++) {
            FormationSlot slot = ourSlotByEaIndex.get(i);
            EaSlot eaSlot = tactic.slots().get(i);
            EaCodecData.Role eaRole = codec.role(eaSlot.roleId()).orElseThrow();
            String focus = eaRole.focuses().get(eaSlot.focusIndex());
            Optional<String> baseRole = ourBaseRole(ourRoles, eaRole.name());
            String variant = baseRole.map(b -> roleId(b, focus)).orElse(null);
            if (Objects.isNull(variant) || !ourRoles.containsKey(variant)) {
                warnings.add(slot.slotId() + ": " + eaRole.name() + " (" + focus + ") bu sitede yok; varsayılan rol bırakıldı");
                continue;
            }
            slots.add(ImportedSlot.builder().slotId(slot.slotId()).roleId(variant).roleName(ourRoles.get(variant).getName()).build());
        }
        log.info("Tactic code imported formation : {} warnings : {}", ours.id(), warnings.size());
        return ImportResponse.builder().formation(ours.id()).buildUp(buildUpName(tactic.buildUp())).depth(tactic.lineHeight())
                .slots(slots).exact(warnings.isEmpty()).warnings(warnings).build();
    }

    /** EA'nın izin verdiği (mevki, rol, focus) kombinasyonları: rol seçici yalnız bunları göstermelidir (TAC-02). */
    /** FIT-2: (mevki, temel rol, odak) oyunda var mı (EA rol listesi; allowedRoles ile aynı eşleme). */
    public boolean inGame(String position, FitRole role) {
        return codec.roles().stream().anyMatch(r -> r.position().equals(position) && normalize(r.name()).equals(normalize(role.getBaseRole()))
                && focusIndex(r, role.getFocus()) >= 0);
    }

    public List<Map<String, String>> allowedRoles() {
        List<Map<String, String>> allowed = new ArrayList<>();
        for (EaCodecData.Role role : codec.roles()) {
            for (String focus : role.focuses()) {
                allowed.add(Map.of("position", role.position(), "baseRole", role.name(), "focus", focus));
            }
        }
        return allowed;
    }

    private EaSlot eaSlot(EaCodecData.Formation ea, int index, IntentProfile ourSlot, List<String> warnings) {
        String position = ea.positions().get(index);
        FitRole role = ourSlot.getResolvedRole();
        Optional<EaCodecData.Role> eaRole = codec.roles().stream()
                .filter(r -> r.position().equals(position) && normalize(r.name()).equals(normalize(role.getBaseRole())))
                .findFirst();
        if (eaRole.isEmpty()) {
            throw new EaRoleUnavailableException(ourSlot.getSlotId() + ": " + role.getName() + " EA'da " + position + " için yok");
        }
        int focusIndex = focusIndex(eaRole.get(), role.getFocus());
        if (focusIndex < 0) {
            throw new EaRoleUnavailableException(ourSlot.getSlotId() + ": " + role.getName() + " odağı EA'da " + position + " için yok");
        }
        return new EaSlot(eaRole.get().id(), focusIndex);
    }

    private int focusIndex(EaCodecData.Role role, String focus) {
        for (int i = 0; i < role.focuses().size(); i++) {
            if (normalize(role.focuses().get(i)).equals(normalize(focus))) {
                return i;
            }
        }
        return -1;
    }

    private Optional<String> ourBaseRole(Map<String, FitRole> roles, String eaName) {
        return roles.values().stream().map(FitRole::getBaseRole).distinct()
                .filter(base -> normalize(base).equals(normalize(eaName))).findFirst();
    }

    private Optional<EaCodecData.Formation> eaFormationFor(FormationDefinition ours) {
        return Optional.ofNullable(ours.eaFormationId()).flatMap(codec::formation);
    }

    private Optional<FormationDefinition> ourFormationFor(EaCodecData.Formation ea) {
        return formationRegistry.findAll().stream().filter(f -> ea.id().equals(f.eaFormationId())).findFirst();
    }

    /** EA, aynı mevkideki slotları sağdan sola (x azalan) sıralar; bizim slotları da aynı kurala göre eşleriz. */
    private Map<Integer, FormationSlot> formationSlotMapping(FormationDefinition ours, EaCodecData.Formation ea) {
        Map<String, List<FormationSlot>> byPosition = new HashMap<>();
        ours.slots().stream().sorted(Comparator.comparingInt(FormationSlot::x).reversed())
                .forEach(s -> byPosition.computeIfAbsent(s.position(), k -> new ArrayList<>()).add(s));
        Map<String, Integer> used = new HashMap<>();
        Map<Integer, FormationSlot> mapping = new HashMap<>();
        for (int i = 0; i < ea.positions().size(); i++) {
            String position = ea.positions().get(i);
            int n = used.merge(position, 1, Integer::sum) - 1;
            mapping.put(i, byPosition.get(position).get(n));
        }
        return mapping;
    }

    private Map<Integer, IntentProfile> slotMapping(FormationDefinition ours, EaCodecData.Formation ea, List<IntentProfile> profiles) {
        Map<String, IntentProfile> bySlotId = new HashMap<>();
        profiles.forEach(p -> bySlotId.put(p.getSlotId(), p));
        Map<Integer, IntentProfile> mapping = new HashMap<>();
        formationSlotMapping(ours, ea).forEach((index, slot) -> mapping.put(index, bySlotId.get(slot.slotId())));
        return mapping;
    }

    private EaBuildUp buildUpOf(TeamSetup setup) {
        String text = Objects.isNull(setup) || Objects.isNull(setup.getBuildUp()) ? "" : setup.getBuildUp().toLowerCase(Locale.ROOT);
        if (text.startsWith(SHORT)) {
            return EaBuildUp.SHORT_PASSING;
        }
        return text.startsWith(COUNTER) ? EaBuildUp.COUNTER : EaBuildUp.BALANCED;
    }

    private int lineHeightOf(TeamSetup setup) {
        if (Objects.isNull(setup) || Objects.isNull(setup.getDepth())) {
            return DEFAULT_LINE_HEIGHT;
        }
        return Math.max(MIN_LINE_HEIGHT, Math.min(MAX_LINE_HEIGHT, setup.getDepth()));
    }

    private String buildUpName(EaBuildUp buildUp) {
        return switch (buildUp) {
            case SHORT_PASSING -> "Short";
            case COUNTER -> "Counter";
            case BALANCED -> "Balanced";
        };
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
