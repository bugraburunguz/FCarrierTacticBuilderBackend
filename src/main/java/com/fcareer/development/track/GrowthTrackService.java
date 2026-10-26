package com.fcareer.development.track;

import com.fcareer.common.exception.NotFoundException;
import com.fcareer.development.service.GrowthProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GTR-a: içe aktarmada anlık görüntü yazar (aynı ay tek, en çok maxSnapshots, seyreltme) ve iki görüntüden ölçüm raporu üretir (yalnız admin).
 * Model değerleri değişmez; rapor GrowthProjector'ın plansız tahminiyle kıyaslar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthTrackService {

    private static final String CAREER_SNAPSHOT = "Gelişim anlık görüntüsü";

    private final GrowthTrackRepository repository;
    private final GrowthTrackProperties properties;
    private final GrowthProjector projector;

    /**
     * Kariyer içi içe aktarmanın tabloları; oyun tarihi yoksa görüntü yazılmaz (yaş ve ay bilinemez). Ayrı transaction (REQUIRES_NEW): görüntü yalnız
     * ölçüm içindir; yazımındaki bir hata içe aktarmayı geri almaz (çağıran yakalar ve loglar). saveUid: farklı save'den içe aktarma loglanır.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(long careerId, LocalDate gameDate, String saveUid, Map<String, List<Map<String, String>>> tables, Map<Long, Integer> playerTeam) {
        if (!properties.isEnabled() || Objects.isNull(gameDate)) {
            return;
        }
        long started = System.nanoTime();
        List<TrackedPlayer> players = GrowthSnapshotBuilder.build(gameDate, tables.get("players"), tables.get("teamplayerlinks"), tables.get("leagueteamlinks"),
                tables.get("career_playermatchratinghistory"), playerTeam);
        byte[] data = GrowthSnapshotCodec.encode(players);
        String previousSave = repository.lastSaveUid(careerId);
        if (Objects.nonNull(previousSave) && Objects.nonNull(saveUid) && !previousSave.equals(saveUid)) {
            log.warn("Growth snapshot from another save careerId : {} gameDate : {}", careerId, gameDate);
        }
        repository.upsert(careerId, gameDate, saveUid, players.size(), data);
        List<GrowthTrackRepository.SnapshotRef> refs = repository.refs(careerId);
        List<Long> thinned = toThin(refs, properties.getMaxSnapshots(), properties.getKeepRecent());
        repository.delete(thinned);
        if (refs.size() - thinned.size() > properties.getMaxSnapshots()) {
            log.warn("Growth snapshot cap not reached careerId : {} kept : {} max : {} keepRecent : {}", careerId, refs.size() - thinned.size(),
                    properties.getMaxSnapshots(), properties.getKeepRecent());
        }
        log.info("Growth snapshot careerId : {} gameDate : {} players : {} bytes : {} thinned : {} ms : {}", careerId, gameDate, players.size(), data.length,
                thinned.size(), Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    /**
     * Seyreltme: görüntü sayısı max'ı aşarken, en eski görüntü ve en yeni keepRecent görüntü dışındakilerden bir öncekine en yakın olanı (eşitlikte en
     * eski) silinir. refs oyun tarihine göre eskiden yeniye.
     */
    public static List<Long> toThin(List<GrowthTrackRepository.SnapshotRef> refs, int max, int keepRecent) {
        List<GrowthTrackRepository.SnapshotRef> kept = new ArrayList<>(refs);
        List<Long> drop = new ArrayList<>();
        while (kept.size() > max) {
            int last = kept.size() - keepRecent - 1;
            int pick = -1;
            long best = Long.MAX_VALUE;
            for (int i = 1; i <= last; i++) {
                long gap = ChronoUnit.DAYS.between(kept.get(i - 1).gameDate(), kept.get(i).gameDate());
                if (gap < best) {
                    best = gap;
                    pick = i;
                }
            }
            if (pick < 0) {
                break;
            }
            drop.add(kept.remove(pick).id());
        }
        return drop;
    }

    /**
     * Ölçüm raporu: from/to oyun tarihleri (verilmezse ilk ve son görüntü). from → o tarihte ya da sonraki ilk görüntü; to → o tarihte ya da önceki son
     * görüntü. İki farklı görüntü yoksa 404.
     */
    @Transactional(readOnly = true)
    public GrowthReport.Report report(long careerId, LocalDate from, LocalDate to) {
        List<GrowthTrackRepository.SnapshotRef> refs = repository.refs(careerId);
        GrowthTrackRepository.SnapshotRef start = refs.stream().filter((r) -> Objects.isNull(from) || !r.gameDate().isBefore(from)).findFirst().orElse(null);
        GrowthTrackRepository.SnapshotRef end = refs.stream().filter((r) -> Objects.isNull(to) || !r.gameDate().isAfter(to)).reduce((a, b) -> b).orElse(null);
        if (Objects.isNull(start) || Objects.isNull(end) || start.id() == end.id() || end.gameDate().isBefore(start.gameDate())) {
            throw new NotFoundException(CAREER_SNAPSHOT, careerId);
        }
        // Oynama ara görüntülerden toplanır (sezon sıfırlaması adım adım); en çok maxSnapshots görüntü açılır.
        List<GrowthReport.Dated> chain = refs.stream().filter((r) -> !r.gameDate().isBefore(start.gameDate()) && !r.gameDate().isAfter(end.gameDate()))
                .map((r) -> repository.load(r.id()).orElseThrow())
                .map((s) -> new GrowthReport.Dated(s.gameDate(), GrowthSnapshotCodec.decode(s.data()))).toList();
        return GrowthReport.build(chain, projector, properties);
    }
}
