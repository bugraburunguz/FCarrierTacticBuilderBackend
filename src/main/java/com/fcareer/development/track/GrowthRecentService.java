package com.fcareer.development.track;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GTR-b: kullanıcının kadro oyuncuları için son görüntü penceresindeki değişim (Gelişim satırı). Yalnız kariyerin sahibi; görüntü yoksa ya da pencerede
 * taban yoksa boş liste. Pencerede en çok recentWindowMonths + 1 görüntü açılır (ayda bir görüntü).
 */
@Service
@RequiredArgsConstructor
public class GrowthRecentService {

    private final GrowthTrackRepository repository;
    private final GrowthTrackProperties properties;
    private final CareerService careerService;
    private final CareerPlayerRepository careerPlayerRepository;

    /** playerId base_player kimliği (Gelişim satırlarıyla aynı). */
    public record Row(long playerId, int months, int overallDelta, Integer matches) {
    }

    /**
     * Önbellek anahtarı (career_id, data_version); görüntü listesi, son görüntü yazımı ve kadro da aynı olmalı: görüntü ayrı transaction'da
     * (REQUIRES_NEW) yazılır, içe aktarma sonra geri alınırsa sürüm artmaz ama aynı ayın görüntüsü değişmiş olur; satış/alış olayı sürümü artırmaz.
     * Anahtar okunduktan sonra yazılan görüntü en çok bir isteklik bayatlık yapar (sonraki istekte yazım zamanı farklı). Ölçüm (GrowthRecentCostReportTest,
     * 7 görüntü × 22 bin oyuncu): önbelleksiz istek ~2 sn ve ~60 MB ayırma, önbellekten ~13 ms; giriş birkaç KB.
     */
    private record Cached(long dataVersion, List<GrowthTrackRepository.SnapshotRef> refs, Instant written, Map<Long, Long> eaIds, List<Row> rows) {
    }

    public static final int CACHE_SIZE = 64;

    // En son kullanılan CACHE_SIZE kariyer (ClubStrengthService ile aynı kalıp).
    private final Map<Long, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    @Transactional(readOnly = true)
    public List<Row> recent(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        long version = careerPlayerRepository.careerDataVersion(careerId);
        Instant written = repository.lastWritten(careerId);
        Map<Long, Long> eaIds = repository.eaIds(careerId, squad.ownedIds());
        List<GrowthTrackRepository.SnapshotRef> refs = repository.refs(careerId);
        Cached cached = cache.get(careerId);
        if (Objects.nonNull(cached) && cached.dataVersion() == version && cached.refs().equals(refs) && Objects.equals(cached.written(), written)
                && cached.eaIds().equals(eaIds)) {
            return cached.rows();
        }
        List<Row> rows = compute(eaIds, refs);
        cache.put(careerId, new Cached(version, refs, written, eaIds, rows));
        return rows;
    }

    private List<Row> compute(Map<Long, Long> eaIds, List<GrowthTrackRepository.SnapshotRef> refs) {
        int base = GrowthRecent.baseIndex(refs.stream().map(GrowthTrackRepository.SnapshotRef::gameDate).toList(), properties.getRecentWindowMonths());
        if (base < 0 || eaIds.isEmpty()) {
            return List.of();
        }
        // Arada silinen görüntü (kariyer silme, seyreltme) atlanır; görüntüler yalnız kadroya indirilir.
        List<GrowthReport.Dated> chain = refs.subList(base, refs.size()).stream().flatMap((r) -> repository.load(r.id()).stream())
                .map((s) -> new GrowthReport.Dated(s.gameDate(), GrowthSnapshotCodec.decode(s.data()))).toList();
        Map<Long, GrowthRecent.Change> changes = GrowthRecent.compute(GrowthRecent.only(chain, new HashSet<>(eaIds.values())));
        List<Row> rows = new ArrayList<>();
        eaIds.forEach((playerId, ea) -> {
            GrowthRecent.Change c = changes.get(ea);
            if (Objects.nonNull(c)) {
                rows.add(new Row(playerId, c.months(), c.overallDelta(), c.matches()));
            }
        });
        return List.copyOf(rows);
    }
}
