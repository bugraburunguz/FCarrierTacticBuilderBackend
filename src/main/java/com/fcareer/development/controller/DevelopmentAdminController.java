package com.fcareer.development.controller;

import com.fcareer.development.calibration.GrowthCalibration.Calibration;
import com.fcareer.development.calibration.GrowthCalibrationService;
import com.fcareer.development.plan.DevelopmentPlanReport;
import com.fcareer.development.plan.DevelopmentPlanReport.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

/** FIT-3 madde 8 ve 10: gelişim planı ↔ vektör raporu ve büyüme kalibrasyonu (ADMIN, yalnız okuma; otomatik değişiklik yok). */
@RestController
@RequestMapping(ADMIN_PATH + "/development")
@RequiredArgsConstructor
public class DevelopmentAdminController {

    private final DevelopmentPlanReport planReport;
    private final GrowthCalibrationService calibration;

    @GetMapping("/plan-report")
    public Report planReport() {
        return planReport.build();
    }

    @GetMapping("/careers/{careerId}/growth-calibration")
    public Calibration growthCalibration(@PathVariable long careerId) {
        return calibration.report(careerId);
    }
}
