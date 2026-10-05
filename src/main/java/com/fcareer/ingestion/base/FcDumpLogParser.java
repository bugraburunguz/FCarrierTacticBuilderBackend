package com.fcareer.ingestion.base;

import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Live Editor log dosyasındaki FCDUMP satırlarını tablolara çevirir. Aynı tablo birden çok kez dökülmüşse
 * (log birden çok çalıştırma içerir) son BEGIN…END bloğu kazanır.
 */
@Slf4j
public final class FcDumpLogParser {

    private static final String MARK = "FCDUMP";
    private static final String BEGIN = "BEGIN";
    private static final String HEADER = "HEADER";
    private static final String ROW = "ROW";
    private static final int TAB_FIELDS = 6;

    private FcDumpLogParser() {
    }

    public static Map<String, List<Map<String, String>>> parse(InputStream in) {
        Map<String, List<Map<String, String>>> tables = new HashMap<>();
        Map<String, List<String>> headers = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (!line.contains(MARK)) {
                    continue;
                }
                String[] parts = line.split("\t", TAB_FIELDS);
                if (parts.length < TAB_FIELDS || !parts[2].contains(MARK)) {
                    continue;
                }
                String kind = parts[3];
                String table = parts[4];
                String payload = parts[5];
                switch (kind) {
                    case BEGIN -> tables.put(table, new ArrayList<>());
                    case HEADER -> headers.put(table, OpenDatasetImporter.splitLine(payload, ','));
                    case ROW -> addRow(tables, headers, table, payload);
                    default -> {
                        // PROGRESS / END / TABLES / SKIP satırları veri taşımaz
                    }
                }
            }
        } catch (IOException e) {
            log.error("FCDUMP log unreadable", e);
            throw new ImportParseException("log unreadable");
        }
        return tables;
    }

    private static void addRow(Map<String, List<Map<String, String>>> tables, Map<String, List<String>> headers, String table, String payload) {
        List<String> header = headers.get(table);
        List<Map<String, String>> rows = tables.get(table);
        if (Objects.isNull(header) || Objects.isNull(rows)) {
            return;
        }
        List<String> cells = OpenDatasetImporter.splitLine(payload, ',');
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < header.size() && i < cells.size(); i++) {
            row.put(header.get(i), cells.get(i));
        }
        rows.add(row);
    }
}
