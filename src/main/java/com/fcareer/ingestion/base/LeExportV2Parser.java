package com.fcareer.ingestion.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.common.exception.FcareerRuntimeException;
import com.fcareer.common.exception.ImportParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.fcareer.common.constants.ErrorCodes.IMPORT_PARSE_ERROR;

/**
 * Live Editor export v2 (07 §4): log satırları `FCX2` önekli ve sekme ayraçlıdır:
 * `META <json>` · `HEADER <tablo> <json kolonlar>` · `CHUNK <tablo> <sıra> <json satırlar>` · `END <tablo> <satır> <fnv1a32:…> <chunk>` · `ERROR <tablo> <mesaj>` · `DONE <json uyarılar>`.
 * Aynı logda birden çok çalıştırma varsa son META'dan başlayan blok geçerlidir. Her tablo END satırındaki satır sayısı ve FNV-1a checksum'ıyla doğrulanır;
 * zorunlu bir tablo eksik/bozuksa import reddedilir ve hangi tablo olduğu söylenir.
 */
@Slf4j
public final class LeExportV2Parser {

    public static final int SCHEMA_VERSION = 2;
    private static final String MARK = "FCX2";
    private static final String UNCHECKED = "-";
    private static final Set<String> REQUIRED = Set.of("players", "teams", "teamplayerlinks");
    private static final int MAX_FIELDS = 8;
    private static final int KIND = 3;
    private static final int TABLE = 4;
    private static final long FNV_OFFSET = 2166136261L;
    private static final long FNV_PRIME = 16777619L;
    private static final long MASK_32 = 0xFFFFFFFFL;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LeExportV2Parser() {
    }

    /** Dışa aktarımın bağlamı (07 §4.1 `context`). */
    public record LeContext(boolean inCareer, String mode, LocalDate gameDate, Integer userTeamId, String saveUid) {
    }

    public record LeExport(String exportId, LeContext context, Map<String, List<Map<String, String>>> tables, List<String> warnings) {
    }

    /** Bilinmeyen şema sürümü: iş kuralı hatası (422). */
    public static class UnsupportedExportVersionException extends FcareerRuntimeException {
        public UnsupportedExportVersionException(int version) {
            super(IMPORT_PARSE_ERROR, "Unsupported export schema version", "schemaVersion " + version);
        }

        @Override
        public HttpStatus httpStatus() {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
    }

    private static final class Table {
        private List<String> columns = List.of();
        private final List<Map<String, String>> rows = new ArrayList<>();
        private long hash = FNV_OFFSET;
        private int chunks;
        private boolean ended;
        private boolean valid = true;
        private String checksum = UNCHECKED;
        private String error;
    }

    /** Log bir v2 dışa aktarımı (FCX2 META) içeriyor mu? */
    public static boolean isV2(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (line.contains(MARK) && line.contains("\tMETA\t")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new ImportParseException("log unreadable");
        }
    }

    public static LeExport parse(InputStream in) {
        Map<String, Table> tables = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        JsonNode context = null;
        String metaLine = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while (Objects.nonNull(line = reader.readLine())) {
                if (!line.contains(MARK)) {
                    continue;
                }
                String[] parts = line.split("\t", MAX_FIELDS);
                if (parts.length <= KIND || !parts[2].contains(MARK)) {
                    continue;
                }
                switch (parts[KIND]) {
                    case "META" -> {
                        tables.clear();
                        warnings.clear();
                        metaLine = parts.length > TABLE ? parts[TABLE] : "";
                        JsonNode meta = readJson(metaLine, "META");
                        int version = meta.path("schemaVersion").asInt(-1);
                        if (version != SCHEMA_VERSION) {
                            throw new UnsupportedExportVersionException(version);
                        }
                        context = meta.path("context");
                    }
                    case "HEADER" -> table(tables, parts).columns = stringList(readJson(parts[5], "HEADER"));
                    case "CHUNK" -> chunk(table(tables, parts), parts);
                    case "END" -> end(table(tables, parts), parts);
                    case "ERROR" -> table(tables, parts).error = parts.length > 5 ? parts[5] : "error";
                    case "DONE" -> warnings.addAll(stringList(readJson(parts.length > TABLE ? parts[TABLE] : "[]", "DONE")));
                    default -> {
                        // SCHEMA vb. veri taşımayan satırlar
                    }
                }
            }
        } catch (IOException e) {
            log.error("FCX2 log unreadable", e);
            throw new ImportParseException("log unreadable");
        }
        if (Objects.isNull(context)) {
            throw new ImportParseException("no FCX2 export found in the log");
        }
        return finish(tables, warnings, context, metaLine);
    }

    private static LeExport finish(Map<String, Table> tables, List<String> warnings, JsonNode context, String metaLine) {
        List<String> missing = new ArrayList<>();
        Map<String, List<Map<String, String>>> result = new HashMap<>();
        Map<String, String> checksums = new TreeMap<>();
        for (String name : REQUIRED) {
            Table table = tables.get(name);
            if (Objects.isNull(table) || !table.ended || !table.valid || Objects.nonNull(table.error)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new ImportParseException("incomplete or missing tables: " + String.join(", ", missing));
        }
        tables.forEach((name, table) -> {
            if (!table.ended || !table.valid || Objects.nonNull(table.error)) {
                warnings.add("INCOMPLETE " + name);
                return;
            }
            result.put(name, table.rows);
            checksums.put(name, table.checksum);
        });
        return new LeExport(exportId(metaLine, checksums), context(context), result, List.copyOf(warnings));
    }

    private static Table table(Map<String, Table> tables, String[] parts) {
        return tables.computeIfAbsent(parts[TABLE], k -> new Table());
    }

    private static void chunk(Table table, String[] parts) {
        table.chunks++;
        String body = parts.length > 6 ? parts[6] : "[]";
        for (String raw : splitRows(body)) {
            table.hash = fnv(table.hash, raw);
            table.rows.add(toRow(table.columns, raw));
        }
    }

    private static void end(Table table, String[] parts) {
        table.ended = true;
        String checksum = parts.length > 6 ? parts[6] : UNCHECKED;
        table.checksum = checksum;
        if (UNCHECKED.equals(checksum)) {
            return;
        }
        int declaredRows = parts.length > 5 ? parseInt(parts[5]) : -1;
        int declaredChunks = parts.length > 7 ? parseInt(parts[7]) : -1;
        boolean sameHash = checksum.equals(String.format("fnv1a32:%08x", table.hash));
        table.valid = declaredRows == table.rows.size() && sameHash && declaredChunks == table.chunks;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Lua `fnv()` ile birebir: satır dizgesinin UTF-8 baytları üzerinde FNV-1a 32 bit. */
    static long fnv(long seed, String text) {
        long h = seed;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            h = ((h ^ (b & 0xFF)) * FNV_PRIME) & MASK_32;
        }
        return h;
    }

    /** `[[..],[..]]` gövdesini üst düzey satır dizgelerine böler (köşeli parantez ve tırnak içi kaçışlar gözetilir); satırlar checksum için birebir korunur. */
    static List<String> splitRows(String body) {
        List<String> rows = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[') {
                depth++;
                if (depth == 2) {
                    start = i;
                }
            } else if (c == ']') {
                if (depth == 2) {
                    rows.add(body.substring(start, i + 1));
                }
                depth--;
            }
        }
        return rows;
    }

    private static Map<String, String> toRow(List<String> columns, String raw) {
        JsonNode cells = readJson(raw, "row");
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < columns.size() && i < cells.size(); i++) {
            JsonNode cell = cells.get(i);
            if (!cell.isNull()) {
                row.put(columns.get(i), cell.asText());
            }
        }
        return row;
    }

    private static JsonNode readJson(String text, String what) {
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new ImportParseException(what + " is not valid JSON");
        }
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }

    private static LeContext context(JsonNode node) {
        LocalDate date = null;
        String text = node.path("gameDate").asText(null);
        if (Objects.nonNull(text)) {
            try {
                date = LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                log.warn("FCX2 gameDate unreadable value : {}", text);
            }
        }
        JsonNode team = node.hasNonNull("userTeamId") ? node.get("userTeamId") : node.path("userTeamIdMem");
        Integer userTeam = team.isNumber() && team.asInt() > 0 ? team.asInt() : null;
        return new LeContext(node.path("inCareer").asBoolean(false), node.path("mode").asText(null), date, userTeam, node.path("saveUid").asText(null));
    }

    /** Aynı veriden aynı kimlik: META + tablo checksum'larının SHA-1 özeti (aynı dosyanın tekrar yüklenmesi tanınır). */
    private static String exportId(String metaLine, Map<String, String> checksums) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(Objects.requireNonNullElse(metaLine, "").getBytes(StandardCharsets.UTF_8));
            checksums.forEach((name, sum) -> digest.update((name + "=" + sum + ";").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
