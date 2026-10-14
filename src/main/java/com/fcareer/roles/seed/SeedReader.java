package com.fcareer.roles.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tactics.model.FormationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SeedReader {

    public static final String ROLE_WEIGHTING = "seed/role-weighting.json";
    public static final String ROLE_CALIBRATION = "seed/role-calibration-v2.json";
    public static final String ROLES = "seed/roles.json";
    public static final String BEHAVIOR_TAGS = "seed/behavior-tags.json";
    public static final String FORMATIONS = "seed/formations.json";
    public static final String PRESETS = "seed/presets.json";

    private final ObjectMapper objectMapper;

    public ReferenceSeedBundle read() {
        return new ReferenceSeedBundle(
                read(ROLE_WEIGHTING, new TypeReference<RoleWeighting>() {
                }),
                read(ROLES, new TypeReference<List<RoleSeed>>() {
                }),
                read(BEHAVIOR_TAGS, new TypeReference<List<BehaviorTagSeed>>() {
                }),
                readFormations(),
                read(PRESETS, new TypeReference<List<PresetSeed>>() {
                }));
    }

    public RoleCalibration readCalibration() {
        return read(ROLE_CALIBRATION, new TypeReference<RoleCalibration>() {
        });
    }

    public List<FormationDefinition> readFormations() {
        return read(FORMATIONS, new TypeReference<>() {
        });
    }

    private <T> T read(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Seed file could not be read: " + path, e);
        }
    }
}
