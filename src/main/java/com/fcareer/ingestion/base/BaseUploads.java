package com.fcareer.ingestion.base;

import com.fcareer.career.importer.CsvStream;
import com.fcareer.common.exception.ImportParseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BaseUploads {

    private static final Set<String> KEYS = Set.of("players", "teams", "teamplayerlinks", "leagueteamlinks", "leagues",
            "playerloans", "playernames", "dcplayernames", "editedplayernames", "context");

    private BaseUploads() {
    }

    /** Yüklenen veri: tablolar + (v2 export ya da CSV `context` tablosundan) bağlam, export kimliği ve uyarılar. */
    public record Upload(Map<String, List<Map<String, String>>> tables, LeExportV2Parser.LeContext context, String exportId, List<String> warnings) {
    }

    /** Ya tek bir Live Editor log dosyası (v2 FCX2 ya da eski FCDUMP) ya da tablo başına bir CSV dosyası kabul edilir. */
    public static Map<String, List<Map<String, String>>> toTables(MultipartFile log, Map<String, MultipartFile> csvFiles) {
        return toUpload(log, csvFiles).tables();
    }

    public static Upload toUpload(MultipartFile log, Map<String, MultipartFile> csvFiles) {
        if (Objects.nonNull(log) && !log.isEmpty()) {
            try {
                boolean v2;
                try (InputStream probe = log.getInputStream()) {
                    v2 = LeExportV2Parser.isV2(probe);
                }
                try (InputStream in = log.getInputStream()) {
                    if (v2) {
                        LeExportV2Parser.LeExport export = LeExportV2Parser.parse(in);
                        return new Upload(export.tables(), export.context(), export.exportId(), export.warnings());
                    }
                    return new Upload(FcDumpLogParser.parse(in), null, null, List.of());
                }
            } catch (IOException e) {
                throw new ImportParseException("log unreadable");
            }
        }
        Map<String, List<Map<String, String>>> tables = new HashMap<>();
        csvFiles.forEach((name, file) -> {
            if (KEYS.contains(name) && Objects.nonNull(file) && !file.isEmpty()) {
                tables.put(name, readCsv(file));
            }
        });
        return new Upload(tables, contextOf(tables.get("context")), null, List.of());
    }

    private static LeExportV2Parser.LeContext contextOf(List<Map<String, String>> rows) {
        if (Objects.isNull(rows) || rows.isEmpty()) {
            return null;
        }
        Map<String, String> row = rows.get(0);
        java.time.LocalDate date = null;
        try {
            date = Objects.isNull(row.get("gamedate")) ? null : java.time.LocalDate.parse(row.get("gamedate"));
        } catch (java.time.format.DateTimeParseException e) {
            date = null;
        }
        Integer team = Objects.isNull(row.get("userteamid")) ? null : Integer.valueOf(row.get("userteamid").trim());
        return new LeExportV2Parser.LeContext(!"0".equals(row.get("incareer")), row.get("mode"), date, Objects.nonNull(team) && team > 0 ? team : null, row.get("saveuid"));
    }

    private static List<Map<String, String>> readCsv(MultipartFile file) {
        List<Map<String, String>> rows = new ArrayList<>();
        Set<String> wanted = new HashSet<>();
        try (InputStream header = file.getInputStream()) {
            String first = new String(header.readNBytes(1 << 16), java.nio.charset.StandardCharsets.UTF_8).split("\\R", 2)[0];
            wanted.addAll(com.fcareer.ingestion.service.OpenDatasetImporter.splitLine(first.replace("﻿", ""), ','));
        } catch (IOException e) {
            throw new ImportParseException("csv unreadable");
        }
        try (InputStream in = file.getInputStream()) {
            CsvStream.read(in, Set.of(), wanted, rows::add);
        } catch (IOException e) {
            throw new ImportParseException("csv unreadable");
        }
        return rows;
    }
}
