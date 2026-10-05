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
