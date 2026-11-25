package com.fcareer.fitengine.service;

import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.RoleAlternative;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tacticcode.service.TacticCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoleSuggestService {

    private static final Comparator<RoleAlternative> ORDER = Comparator.comparingDouble(RoleAlternative::getCurrentFit).reversed()
            .thenComparing(RoleAlternative::getLevelFit, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(RoleAlternative::getRoleId);

    private final RoleService roleService;
    private final RoleFitV3Service roleFitV3Service;
    /** Öneri ve seçici yalnız oyunda o mevkide olan (rol, odak) çiftlerinden; seçilemeyen rol taslağa girmez. */
    private final TacticCodeService tacticCodeService;

    /** Slot mevkisinin bütün rolleri bu oyuncuya göre, en uygun önce. */
    public List<RoleAlternative> alternatives(FitPlayer player, String position) {
        return rank(roleService.fitRolesById().values().stream()
                .filter((role) -> role.getPositions().contains(position) && tacticCodeService.inGame(position, role))
                .map((role) -> {
                    RoleFitResponse fit = roleFitV3Service.calculate(player, role, null, position);
                    return RoleAlternative.builder().roleId(role.getId()).roleName(role.getName()).currentFit(fit.getCurrentFit()).levelFit(fit.getLevelFit()).build();
                })
                .toList());
    }

    public static List<RoleAlternative> rank(Collection<RoleAlternative> alternatives) {
        return alternatives.stream().sorted(ORDER).toList();
    }

    public static Optional<RoleAlternative> better(String currentRoleId, List<RoleAlternative> ranked, double minGain) {
        Optional<RoleAlternative> current = ranked.stream().filter((a) -> a.getRoleId().equals(currentRoleId)).findFirst();
        if (ranked.isEmpty() || current.isEmpty()) {
            return Optional.empty();
        }
        RoleAlternative best = ranked.get(0);
        boolean better = !best.getRoleId().equals(currentRoleId) && best.getCurrentFit() - current.get().getCurrentFit() >= minGain;
        return better ? Optional.of(best) : Optional.empty();
    }
}
