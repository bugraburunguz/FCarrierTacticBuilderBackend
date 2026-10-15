package com.fcareer.tactics.service;

import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.roles.seed.SeedReader;
import com.fcareer.tactics.model.FormationDefinition;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormationRegistry {

    private final SeedReader seedReader;
    private final Map<String, FormationDefinition> formations = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        seedReader.readFormations().forEach(f -> formations.put(f.id(), f));
        log.info("Formations loaded count : {}", formations.size());
    }

    /**
     * V3-05d-b: EA formasyon kodu (ör. "f41212") → bizim formasyon kimliği. TEK eşleme yeri (formations.json `eaFormationId`);
     * yakalama, SBC görevi ve aktif kadro buradan okur. Eşlemesi olmayan kod haritada yok (çağıran ham kodu saklar).
     */
    public Map<String, String> idsByEaCode() {
        Map<String, String> byCode = new LinkedHashMap<>();
        formations.values().stream().filter(f -> Objects.nonNull(f.eaFormationId())).forEach(f -> byCode.put(f.eaFormationId(), f.id()));
        return byCode;
    }

    public List<FormationDefinition> findAll() {
        return List.copyOf(formations.values());
    }

    public FormationDefinition get(String formationId) {
        FormationDefinition formation = formations.get(formationId);
        if (Objects.isNull(formation)) {
            log.warn("Unknown formation requested : {}", formationId);
            throw new InvalidTacticException("formation " + formationId);
        }
        return formation;
    }
}
