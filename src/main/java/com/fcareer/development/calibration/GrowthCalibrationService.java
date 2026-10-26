package com.fcareer.development.calibration;

import com.fcareer.development.calibration.GrowthCalibration.Calibration;
import com.fcareer.development.service.GrowthProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FIT-3 madde 10: ardışık iki IMPORT anlık görüntüsünden büyüme ölçümü (admin raporu). Katsayılar otomatik değişmez. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthCalibrationService {

    private static final int PAIR = 2;

    private final GrowthCalibrationRepository repository;
    private final GrowthProjector growth;

    @Transactional(readOnly = true)
    public Calibration report(long careerId) {
        List<Long> ids = repository.lastImportsWithAttrs(careerId, PAIR);
        if (ids.size() < PAIR) {
            log.info("Growth calibration careerId : {} imports : {} (veri yok)", careerId, ids.size());
            return GrowthCalibration.noData();
        }
        long to = ids.get(0);
        long from = ids.get(1);
        List<GrowthCalibration.SnapshotPlayer> before = repository.players(from);
        List<GrowthCalibration.SnapshotPlayer> after = repository.players(to);
        Calibration c = GrowthCalibration.summarize(before, after, growth.config().getHardToTrain());
        // GROWTH-CAL: altyapı/A takım × yaş için gerçek artış ve önerilen tavan; config otomatik değişmez.
        Long interval = repository.intervalDays(from, to);
        List<GrowthCalibration.StageBand> bands = GrowthCalibration.stageBands(before, after, repository.academyTeamId(careerId), interval,
                growth.config().getAcademyMaxGain(), growth.config().getFirstTeamMaxGain());
        // GT-a: yaş grubuna göre gerçek düşüş (fiziksel/teknik/zihinsel); başlangıç yaşları otomatik değişmez.
        List<GrowthCalibration.DeclineBand> declines = GrowthCalibration.declineBands(before, after, interval);
        log.info("Growth calibration careerId : {} from : {} to : {} players : {} stageBands : {} declineBands : {}", careerId, from, to, c.players(), bands.size(),
                declines.size());
        return GrowthCalibration.withSnapshots(GrowthCalibration.withDeclineBands(GrowthCalibration.withStageBands(c, bands, interval), declines), from, to);
    }
}
