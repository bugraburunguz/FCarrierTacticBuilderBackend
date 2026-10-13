package com.fcareer.ingestion.controller;

import com.fcareer.ingestion.model.EnrichmentResult;
import com.fcareer.ingestion.model.IngestionResult;
import com.fcareer.ingestion.service.CatalogEnrichmentService;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.base.BaseUploads;
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
    private final BaseCatalogImporter baseCatalogImporter;
    private final com.fcareer.catalog.configuration.CatalogProperties catalogProperties;

    @PostMapping("/run")
    public IngestionService.Started run() {
        return ingestionService.startAsync();
    }

    @PostMapping(value = "/enrich-dump", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public EnrichmentResult enrichFromDump(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        try (java.io.InputStream in = file.getInputStream()) {
            return catalogEnrichmentService.enrichFromPlayersCsv(in, catalogProperties.getDefaultGameVersion());
        }
    }

    @PostMapping(value = "/import-base", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseCatalogImporter.BaseImportResult importBase(
            @RequestParam(value = "log", required = false) org.springframework.web.multipart.MultipartFile log,
            @RequestParam(value = "players", required = false) org.springframework.web.multipart.MultipartFile players,
            @RequestParam(value = "teams", required = false) org.springframework.web.multipart.MultipartFile teams,
            @RequestParam(value = "teamplayerlinks", required = false) org.springframework.web.multipart.MultipartFile links,
            @RequestParam(value = "leagueteamlinks", required = false) org.springframework.web.multipart.MultipartFile leagueLinks,
            @RequestParam(value = "leagues", required = false) org.springframework.web.multipart.MultipartFile leagues,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
            @RequestParam(value = "createMissing", defaultValue = "true") boolean createMissing,
            @RequestParam(value = "full", defaultValue = "false") boolean full,
            @RequestParam(value = "label", required = false) String label) {
        java.util.Map<String, org.springframework.web.multipart.MultipartFile> csvs = new java.util.HashMap<>();
        csvs.put("players", players);
        csvs.put("teams", teams);
        csvs.put("teamplayerlinks", links);
        csvs.put("leagueteamlinks", leagueLinks);
        csvs.put("leagues", leagues);
        return baseCatalogImporter.importMissing(BaseUploads.toTables(log, csvs), catalogProperties.getDefaultGameVersion(), overwrite, createMissing, full, label);
    }

    @PostMapping("/open-dataset")
    public int importOpenDataset() {
        return openDatasetImporter.importSeed();
    }
}
