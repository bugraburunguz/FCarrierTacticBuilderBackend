package com.fcareer.development.track;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

/** GTR-a: gelişim ölçüm raporu (yalnız ADMIN, yalnız okuma). from/to oyun tarihi; format=csv indirme. */
@RestController
@RequiredArgsConstructor
public class GrowthTrackAdminController {

    private static final String CSV = "csv";

    private final GrowthTrackService service;

    @GetMapping(ADMIN_PATH + "/careers/{careerId}/growth-report")
    public ResponseEntity<?> report(@PathVariable long careerId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) String format) {
        GrowthReport.Report report = service.report(careerId, from, to);
        if (CSV.equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gelisim-olcumu-" + careerId + ".csv\"").body(GrowthReport.csv(report));
        }
        return ResponseEntity.ok(report);
    }
}
