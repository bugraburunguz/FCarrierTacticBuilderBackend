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

    /** Ya tek bir Live Editor log dosyası ya da tablo başına bir CSV dosyası kabul edilir. */
    public static Map<String, List<Map<String, String>>> toTables(MultipartFile log, Map<String, MultipartFile> csvFiles) {
        if (Objects.nonNull(log) && !log.isEmpty()) {
            try (InputStream in = log.getInputStream()) {
                return FcDumpLogParser.parse(in);
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
        return tables;
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
