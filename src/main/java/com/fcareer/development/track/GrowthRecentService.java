package com.fcareer.development.track;

import com.fcareer.career.service.CareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
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

    /** playerId base_player kimliği (Gelişim satırlarıyla aynı). */
    public record Row(long playerId, int months, int overallDelta, Integer matches) {
    }

    @Transactional(readOnly = true)
    public List<Row> recent(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        Map<Long, Long> eaIds = repository.eaIds(careerId, squad.ownedIds());
        List<GrowthTrackRepository.SnapshotRef> refs = repository.refs(careerId);
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
        return rows;
    }
}
