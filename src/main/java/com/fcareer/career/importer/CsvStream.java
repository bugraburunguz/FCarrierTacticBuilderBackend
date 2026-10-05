package com.fcareer.career.importer;

import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.service.OpenDatasetImporter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
public final class CsvStream {

    private CsvStream() {
    }

    public static int read(InputStream in, Set<String> required, Set<String> optional, Consumer<Map<String, String>> sink) {
        int rows = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (Objects.isNull(headerLine)) {
                throw new ImportParseException("empty file");
            }
            List<String> header = OpenDatasetImporter.splitLine(headerLine.replace("﻿", ""), ',');
            for (String column : required) {
                if (!header.contains(column)) {
                    throw new ImportParseException("missing column " + column);
                }
            }
            Map<String, Integer> index = new HashMap<>();
            for (String column : required) {
                index.put(column, header.indexOf(column));
            }
            for (String column : optional) {
                if (header.contains(column)) {
                    index.put(column, header.indexOf(column));
                }
            }
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = OpenDatasetImporter.splitLine(line, ',');
                Map<String, String> row = new HashMap<>();
                index.forEach((column, i) -> row.put(column, i < cells.size() ? cells.get(i) : ""));
                sink.accept(row);
                rows++;
            }
        } catch (IOException e) {
            log.error("Import file unreadable", e);
            throw new ImportParseException("file unreadable");
        }
        return rows;
    }

    public static Integer toInt(String raw) {
        if (Objects.isNull(raw) || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long toLong(String raw) {
        if (Objects.isNull(raw) || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
