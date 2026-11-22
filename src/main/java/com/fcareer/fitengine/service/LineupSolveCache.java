package com.fcareer.fitengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class LineupSolveCache {

    public static final int MAX_ENTRIES = 500;
    public static final Duration TTL = Duration.ofMinutes(10);
    private static final String DIGEST = "SHA-256";

    private record Entry(Instant at, SquadFitResponse response) {
    }

    private record KeyInput(List<FitPlayer> players, List<IntentProfile> slots, List<Map.Entry<String, Long>> locked) {
    }

    private final ObjectWriter keyWriter;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public LineupSolveCache(ObjectMapper objectMapper, Clock clock) {
        this.keyWriter = objectMapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.clock = clock;
    }

    public SquadFitResponse getOrSolve(long careerId, List<FitPlayer> players, List<IntentProfile> slots, String engineVersion, Map<String, Long> locked,
                                       Supplier<SquadFitResponse> solver) {
        String key = buildKey(careerId, players, slots, engineVersion, locked);
        Instant now = clock.instant();
        synchronized (entries) {
            Entry hit = entries.get(key);
            if (Objects.nonNull(hit) && hit.at().plus(TTL).isAfter(now)) {
                return hit.response();
            }
        }
        SquadFitResponse solved = solver.get();
        synchronized (entries) {
            entries.put(key, new Entry(now, solved));
        }
        return solved;
    }

    public void clear() {
        synchronized (entries) {
            entries.clear();
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    private String buildKey(long careerId, List<FitPlayer> players, List<IntentProfile> slots, String engineVersion, Map<String, Long> locked) {
        List<Map.Entry<String, Long>> lockedInOrder = Objects.isNull(locked) ? List.of()
                : locked.entrySet().stream().<Map.Entry<String, Long>>map(AbstractMap.SimpleImmutableEntry::new).toList();
        try {
            byte[] content = keyWriter.writeValueAsBytes(new KeyInput(players, slots, lockedInOrder));
            return careerId + ":" + engineVersion + ":" + HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(content));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("lineup cache key", e);
        }
    }
}
