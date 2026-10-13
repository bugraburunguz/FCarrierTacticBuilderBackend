package com.fcareer.fitengine.level;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * fit_level_norms üretici (E3-5): katalogdaki her oyuncunun her (mevki, rol, focus) için currentFit'ini FitEngineV3 ile hesaplar,
 * OVR başına ±3 (<30 oyuncuda genişleyen) bandın yüzdelik noktalarını tek transaction'da yazar. Türetilmiş veridir, yeniden üretilebilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FitLevelNormsBuilder {

    public static final int MIN_BAND_PLAYERS = 30;
    public static final int HALF_WIDTH = 3;
    public static final int MAX_HALF_WIDTH = 10;
    private static final int MIN_OVR = 30;
    private static final int MAX_OVR = 99;
    private static final int BASE_PLAYER_CHUNK = 2000;

    private final PlayerRatingRepository ratings;
    private final BasePlayerRepository basePlayers;
    private final PlayerMapper playerMapper;
    private final FitEngineV3 engine;
    private final RoleSeeds seeds;
    private final FitLevelNormsRepository repository;

    private record Candidate(int ovr, FitPlayer player) {
    }

    @Transactional
    public int rebuild(short gameVersionId) {
        List<PlayerRatingEntity> catalog = ratings.findCatalog(gameVersionId);
        Map<Long, BasePlayerEntity> bases = loadBases(catalog);
        Map<String, List<Candidate>> byGenderPosition = new HashMap<>();
        for (PlayerRatingEntity rating : catalog) {
            BasePlayerEntity base = bases.get(rating.getBasePlayerId());
            if (base == null) {
                continue;
            }
            FitPlayer player = playerMapper.toFitPlayer(base, rating, null, null);
            Candidate candidate = new Candidate(rating.getOverall().intValue(), player);
            for (String position : player.getPositions()) {
                byGenderPosition.computeIfAbsent(player.getGender() + "|" + position, (k) -> new ArrayList<>()).add(candidate);
            }
        }
        List<FitLevelNormsRepository.Row> rows = new ArrayList<>();
        for (String key : seeds.vectorKeys()) {
            String[] parts = key.split("\\|");
            for (int gender = 0; gender <= 1; gender++) {
                List<Candidate> candidates = byGenderPosition.get(gender + "|" + parts[0]);
                if (candidates != null && !candidates.isEmpty()) {
                    rows.addAll(rowsFor(gender, parts[0], parts[1], parts[2], candidates));
                }
            }
        }
        repository.deleteByEngineVersion(FitEngineV3.VERSION);
        repository.insertAll(FitEngineV3.VERSION, rows);
        log.info("fit_level_norms yeniden üretildi: {} satır, {} oyuncu, motor {}", rows.size(), catalog.size(), FitEngineV3.VERSION);
        return rows.size();
    }

    private List<FitLevelNormsRepository.Row> rowsFor(int gender, String position, String role, String focus, List<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream().sorted(Comparator.comparingInt(Candidate::ovr)).toList();
        int[] ovrs = sorted.stream().mapToInt(Candidate::ovr).toArray();
        double[] fits = new double[sorted.size()];
        for (int i = 0; i < fits.length; i++) {
            fits[i] = engine.currentFit(sorted.get(i).player(), position, role, focus, null).currentFit();
        }
        List<FitLevelNormsRepository.Row> rows = new ArrayList<>();
        for (int ovr = MIN_OVR; ovr <= MAX_OVR; ovr++) {
            LevelFitCalculator.Band band = LevelFitCalculator.band(ovrs, ovr, MIN_BAND_PLAYERS, HALF_WIDTH, MAX_HALF_WIDTH);
            if (band.count() == 0) {
                continue;
            }
            double[] inBand = new double[band.count()];
            int n = 0;
            for (int i = 0; i < ovrs.length; i++) {
                if (ovrs[i] >= band.lo() && ovrs[i] <= band.hi()) {
                    inBand[n++] = fits[i];
                }
            }
            rows.add(new FitLevelNormsRepository.Row(gender, position, role, focus, ovr, band.lo(), band.hi(), band.count(), LevelFitCalculator.quantiles(inBand)));
        }
        return rows;
    }

    private Map<Long, BasePlayerEntity> loadBases(List<PlayerRatingEntity> catalog) {
        List<Long> ids = catalog.stream().map(PlayerRatingEntity::getBasePlayerId).distinct().toList();
        Map<Long, BasePlayerEntity> map = new HashMap<>();
        for (int from = 0; from < ids.size(); from += BASE_PLAYER_CHUNK) {
            basePlayers.findAllById(ids.subList(from, Math.min(ids.size(), from + BASE_PLAYER_CHUNK))).forEach((b) -> map.put(b.getId(), b));
        }
        return map;
    }
}
