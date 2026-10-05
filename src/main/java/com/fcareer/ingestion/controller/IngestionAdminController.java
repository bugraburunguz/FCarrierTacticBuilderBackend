package com.fcareer.ingestion.controller;

import com.fcareer.ingestion.model.IngestionResult;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

@RestController
@RequestMapping(ADMIN_PATH + "/ingestion")
@RequiredArgsConstructor
public class IngestionAdminController {

    private final IngestionService ingestionService;
    private final OpenDatasetImporter openDatasetImporter;

    @PostMapping("/run")
    public IngestionResult run() {
        return ingestionService.run();
    }

    @PostMapping("/open-dataset")
    public int importOpenDataset() {
        return openDatasetImporter.importSeed();
    }
}
