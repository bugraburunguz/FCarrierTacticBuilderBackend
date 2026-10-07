package com.fcareer.roles.seed;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.common.redis.RedisJsonCache;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.roles.entity.BehaviorTagEntity;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.repository.BehaviorTagRepository;
import com.fcareer.roles.repository.RoleRepository;
import com.fcareer.tactics.entity.PresetEntity;
import com.fcareer.tactics.entity.TacticRuleEntity;
import com.fcareer.tactics.repository.PresetRepository;
import com.fcareer.tactics.repository.TacticRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fcareer.common.constants.RedisConstants.CACHE_BEHAVIOR_TAGS;
import static com.fcareer.common.constants.RedisConstants.CACHE_PRESETS;
import static com.fcareer.common.constants.RedisConstants.CACHE_ROLES;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcareer.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReferenceDataSeeder implements ApplicationRunner {

    private final SeedReader seedReader;
    private final RoleLibraryGenerator roleLibraryGenerator;
    private final SeedValidator seedValidator;
    private final RoleRepository roleRepository;
    private final BehaviorTagRepository behaviorTagRepository;
    private final PresetRepository presetRepository;
    private final TacticRuleRepository tacticRuleRepository;
    private final RedisJsonCache redisJsonCache;
    private final FitEngineProperties fitEngineProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ReferenceSeedBundle bundle = seedReader.read();
        RoleCalibration calibration = "v2".equalsIgnoreCase(fitEngineProperties.getRoleWeightsVersion()) ? seedReader.readCalibration() : null;
        log.info("Role weights version : {}", Objects.isNull(calibration) ? "v1" : calibration.version());
        List<RoleEntity> roles = roleLibraryGenerator.generate(bundle.roles(), bundle.weighting(), calibration);
        Map<String, List<String>> conflicts = seedValidator.symmetricConflicts(bundle.tags());
        List<String> errors = seedValidator.validate(bundle, roles, conflicts);
        if (!errors.isEmpty()) {
            log.error("Reference seed is invalid error count : {} first : {}", errors.size(), errors.get(0));
            throw new IllegalStateException("Reference seed invalid: " + errors);
        }

        roleRepository.saveAll(roles);
        behaviorTagRepository.saveAll(toTags(bundle.tags(), conflicts));
        presetRepository.saveAll(bundle.presets().stream().map(this::toPreset).toList());
        tacticRuleRepository.saveAll(bundle.rules().stream().map(this::toRule).toList());
        redisJsonCache.evict(CACHE_ROLES, CACHE_BEHAVIOR_TAGS, CACHE_PRESETS);

        log.info("Reference data seeded roles : {} tags : {} presets : {} rules : {}",
                roles.size(), bundle.tags().size(), bundle.presets().size(), bundle.rules().size());
    }

    private List<BehaviorTagEntity> toTags(List<BehaviorTagSeed> seeds, Map<String, List<String>> conflicts) {
        List<BehaviorTagEntity> tags = new ArrayList<>();
        short order = 0;
        for (BehaviorTagSeed seed : seeds) {
            BehaviorTagEntity tag = new BehaviorTagEntity();
            tag.setId(seed.id());
            tag.setPosition(seed.position());
            tag.setLabelTr(seed.labelTr());
            tag.setWeaponAttrs(seed.weaponAttrs());
            tag.setPlaystyleWeapons(Objects.isNull(seed.playstyleWeapons()) ? List.of() : seed.playstyleWeapons());
            tag.setExpectedMin((short) seed.expectedMin());
            tag.setRoleVote(seed.roleVote());
            tag.setConflicts(conflicts.getOrDefault(seed.id(), List.of()));
            tag.setNlgTr(seed.nlgTr());
            tag.setAccelerateHint(seed.accelerateHint());
            tag.setSortOrder(order++);
            tags.add(tag);
        }
        return tags;
    }

    private PresetEntity toPreset(PresetSeed seed) {
        PresetEntity preset = new PresetEntity();
        preset.setId(seed.id());
        preset.setKind(seed.kind());
        preset.setName(seed.name());
        preset.setClub(seed.club());
        preset.setCoach(seed.coach());
        preset.setSeason(seed.season());
        preset.setFormation(seed.formation());
        preset.setSettings(seed.settings());
        preset.setSignatureTr(seed.signatureTr());
        preset.setRoleHints(seed.roleHints());
        preset.setSlotTags(seed.slotTags());
        preset.setSourceNote(seed.sourceNote());
        return preset;
    }

    private TacticRuleEntity toRule(TacticRuleDefinition definition) {
        TacticRuleEntity rule = new TacticRuleEntity();
        rule.setId(definition.getId());
        rule.setSeverity(definition.getSeverity());
        rule.setTitleTr(definition.getTitleTr());
        rule.setMessageTr(definition.getMessageTr());
        rule.setFixTr(definition.getFixTr());
        rule.setConditions(definition.getConditions());
        rule.setEnabled(true);
        return rule;
    }
}
