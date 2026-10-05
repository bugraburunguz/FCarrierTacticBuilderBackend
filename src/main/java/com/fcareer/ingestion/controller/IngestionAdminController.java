package com.fcareer.ingestion.controller;

import com.fcareer.ingestion.model.EnrichmentResult;
import com.fcareer.ingestion.model.IngestionResult;
import com.fcareer.ingestion.service.CatalogEnrichmentService;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

@RestController
@RequestMapping(ADMIN_PATH + "/ingestion")
@RequiredArgsConstructor
public class IngestionAdminController {

    private final IngestionService ingestionService;
    private final OpenDatasetImporter openDatasetImporter;
    private final CatalogEnrichmentService catalogEnrichmentService;
    private final com.fcareer.catalog.configuration.CatalogProperties catalogProperties;

    @PostMapping("/run")
    public IngestionResult run() {
        return ingestionService.run();
    }

    @PostMapping(value = "/enrich-dump", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public EnrichmentResult enrichFromDump(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        try (java.io.InputStream in = file.getInputStream()) {
            return catalogEnrichmentService.enrichFromPlayersCsv(in, catalogProperties.getDefaultGameVersion());
        }
    }

    @PostMapping("/open-dataset")
    public int importOpenDataset() {
        return openDatasetImporter.importSeed();
    }
}
