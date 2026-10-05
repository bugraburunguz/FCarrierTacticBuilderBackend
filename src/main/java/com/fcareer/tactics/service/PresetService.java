package com.fcareer.tactics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fcareer.common.configuration.CacheProperties;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.redis.RedisJsonCache;
import com.fcareer.tactics.entity.PresetEntity;
import com.fcareer.tactics.mapper.TacticMapper;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.repository.PresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.fcareer.common.constants.RedisConstants.CACHE_PRESETS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresetService {

    private static final String PRESET = "preset";

    private final PresetRepository presetRepository;
    private final TacticMapper tacticMapper;
    private final RedisJsonCache redisJsonCache;
    private final CacheProperties cacheProperties;

    public List<PresetResponse> findByKind(String kind) {
        List<PresetResponse> all = redisJsonCache.getOrLoad(CACHE_PRESETS, new TypeReference<>() {
                }, cacheProperties.getReferenceTtl(),
                () -> presetRepository.findAllByOrderByKindDescIdAsc().stream().map(tacticMapper::toPresetResponse).toList());
        if (Objects.isNull(kind) || kind.isBlank()) {
            return all;
        }
        return all.stream().filter(p -> p.getKind().equalsIgnoreCase(kind)).toList();
    }

    public PresetResponse get(String presetId) {
        return tacticMapper.toPresetResponse(getEntity(presetId));
    }

    public PresetEntity getEntity(String presetId) {
        return presetRepository.findById(presetId).orElseThrow(() -> new NotFoundException(PRESET, presetId));
    }
}
