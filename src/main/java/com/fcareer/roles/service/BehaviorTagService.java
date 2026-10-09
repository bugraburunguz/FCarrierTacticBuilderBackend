package com.fcareer.roles.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fcareer.common.configuration.CacheProperties;
import com.fcareer.common.redis.RedisJsonCache;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.roles.mapper.BehaviorTagMapper;
import com.fcareer.roles.model.response.BehaviorTagResponse;
import com.fcareer.roles.repository.BehaviorTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.RedisConstants.CACHE_BEHAVIOR_TAGS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BehaviorTagService {

    private final BehaviorTagRepository behaviorTagRepository;
    private final BehaviorTagMapper behaviorTagMapper;
    private final RedisJsonCache redisJsonCache;
    private final CacheProperties cacheProperties;

    public List<BehaviorTagResponse> findByPosition(String position) {
        List<BehaviorTagResponse> all = redisJsonCache.getOrLoad(CACHE_BEHAVIOR_TAGS, new TypeReference<>() {
                }, cacheProperties.getReferenceTtl(),
                () -> behaviorTagRepository.findAllByOrderBySortOrderAsc().stream().map(behaviorTagMapper::toResponse).toList());
        if (Objects.isNull(position) || position.isBlank()) {
            return all;
        }
        return all.stream().filter(t -> t.getPosition().equalsIgnoreCase(position)).toList();
    }

    private final com.fcareer.common.cache.TtlMemo<Map<String, FitTag>> fitTags = new com.fcareer.common.cache.TtlMemo<>(java.time.Duration.ofMinutes(10), this::loadFitTags);

    public Map<String, FitTag> fitTagsById() {
        return fitTags.get();
    }

    private Map<String, FitTag> loadFitTags() {
        return behaviorTagRepository.findAll().stream().map(behaviorTagMapper::toFitTag).collect(Collectors.toMap(FitTag::getId, Function.identity()));
    }
}
