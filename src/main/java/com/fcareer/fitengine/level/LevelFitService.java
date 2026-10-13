package com.fcareer.fitengine.level;

import com.fcareer.fitengine.service.FitEngineV3;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** levelFit sorgusu: fit_level_norms satırlarını ilk kullanımda belleğe alır; yeniden üretimden sonra {@link #refresh()}. */
@Service
@RequiredArgsConstructor
public class LevelFitService {

    private final FitLevelNormsRepository repository;
    private volatile Map<String, TreeMap<Integer, FitLevelNormsRepository.Row>> cache;

    public record LevelFit(double value, int bandLo, int bandHi, int sampleCount) {
    }

    /** Satırı olmayan (mevki, rol, focus) için boş döner; OVR için en yakın satır kullanılır. */
    public Optional<LevelFit> levelFit(int gender, String position, String roleId, String focus, int ovr, double currentFit) {
        TreeMap<Integer, FitLevelNormsRepository.Row> rows = norms().get(key(gender, position, roleId, focus));
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Integer, FitLevelNormsRepository.Row> below = rows.floorEntry(ovr);
        Map.Entry<Integer, FitLevelNormsRepository.Row> above = rows.ceilingEntry(ovr);
        FitLevelNormsRepository.Row row = below == null ? above.getValue()
                : above == null || ovr - below.getKey() <= above.getKey() - ovr ? below.getValue() : above.getValue();
        double value = Math.round(LevelFitCalculator.percentile(row.quantiles(), currentFit) * 10.0) / 10.0;
        return Optional.of(new LevelFit(value, row.bandLo(), row.bandHi(), row.sampleCount()));
    }

    public void refresh() {
        cache = null;
    }

    private Map<String, TreeMap<Integer, FitLevelNormsRepository.Row>> norms() {
        Map<String, TreeMap<Integer, FitLevelNormsRepository.Row>> local = cache;
        if (local == null) {
            local = new HashMap<>();
            for (FitLevelNormsRepository.Row row : repository.findByEngineVersion(FitEngineV3.VERSION)) {
                local.computeIfAbsent(key(row.gender(), row.position(), row.roleId(), row.focus()), (k) -> new TreeMap<>()).put(row.ovr(), row);
            }
            cache = local;
        }
        return local;
    }

    private static String key(int gender, String position, String roleId, String focus) {
        return gender + "|" + position + "|" + roleId + "|" + focus;
    }
}
