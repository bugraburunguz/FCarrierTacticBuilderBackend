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
        Calibration c = GrowthCalibration.summarize(repository.players(from), repository.players(to), growth.config().getHardToTrain());
        log.info("Growth calibration careerId : {} from : {} to : {} players : {}", careerId, from, to, c.players());
        return GrowthCalibration.withSnapshots(c, from, to);
    }
}
