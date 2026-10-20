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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.fcareer.common.constants.ErrorCodes.IMPORT_PARSE_ERROR;

/**
 * Live Editor export v2 (07 §4): log satırları `FCX2` önekli ve sekme ayraçlıdır:
 * `META <json>` · `HEADER <tablo> <json kolonlar>` · `CHUNK <tablo> <sıra> <json satırlar>` · `END <tablo> <satır> <fnv1a32:…> <chunk>` · `ERROR <tablo> <mesaj>` · `DONE <json uyarılar>`.
 * Aynı logda birden çok çalıştırma olabilir (LE oturum boyunca biriktirir): kariyer içe aktarımı en son kariyer dışa aktarımını, temel katalog
 * yüklemesi en son ana menü (BASE) dışa aktarımını seçer ({@link Prefer}). Her tablo END satırındaki satır sayısı ve FNV-1a checksum'ıyla doğrulanır;
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
    /** Bu uzunluğa kadar değerler (sayılar, kısa kodlar) tablo içinde tek kopya tutulur. */
    private static final int SHORT_VALUE = 8;
    private static final ObjectMapper JSON = new ObjectMapper();

    private LeExportV2Parser() {
    }

    /** Dışa aktarımın bağlamı (07 §4.1 `context`). */
    /** transferBudget: betik 2.0.2'den (GetUserTransferBudget, oyun içi para birimi); yoksa null. */
    public record LeContext(boolean inCareer, String mode, LocalDate gameDate, Integer userTeamId, String saveUid, Long transferBudget) {
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
        private Map<String, Integer> index = Map.of();
        /** Kısa tekrar eden değerlerin ("0", "65", "1928") tek kopyası; yalnız bu tablonun ayrıştırması boyunca. */
        private final Map<String, String> shortValues = new HashMap<>();
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

    /** Logda birden çok dışa aktarım varken hangisi: kariyer içe aktarımı kariyerdekini, temel katalog yüklemesi ana menüdekini (BASE) ister. */
    public enum Prefer { CAREER, BASE }

    /** Bir META'dan başlayan tek dışa aktarım. */
    private static final class Block {
        private final Map<String, Table> tables = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private JsonNode context;
        private String metaLine;
        private int index;
    }

    public static LeExport parse(InputStream in) {
        return parse(in, Prefer.CAREER);
    }

    public static LeExport parse(InputStream in, Prefer prefer) {
        // Bellekte en çok iki dışa aktarım: işlenen (block) ve şu ana kadarki en iyi aday (best). Birikmiş logda tümünü tutmak her biri
        // ~22 bin oyuncu satırıyla belleği tüketebilirdi.
        Selection selection = new Selection(prefer);
        Block block = null;
        // Log Latin-1 okunur: her karakter tam bir ham bayttır. Özet Lua'daki gibi ham baytlar üzerinden hesaplanır; metin ardından UTF-8'e
        // çevrilir. Bellekten okunan dizgelerde geçersiz UTF-8 bayt olabiliyor (2.0.3 nations.isocountrycode); UTF-8 okumada U+FFFD olup özeti bozuyordu.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1), 1 << 16)) {
            String rawLine;
            while (Objects.nonNull(rawLine = reader.readLine())) {
                if (!rawLine.contains(MARK)) {
                    continue;
                }
                String[] rawParts = rawLine.split("\t", MAX_FIELDS);
                String[] parts = new String[rawParts.length];
                for (int i = 0; i < rawParts.length; i++) {
                    parts[i] = utf8(rawParts[i]);
                }
                if (parts.length <= KIND || !parts[2].contains(MARK)) {
                    continue;
                }
                if ("META".equals(parts[KIND])) {
                    if (Objects.nonNull(block)) {
                        selection.offer(block);
                    }
                    block = new Block();
                    block.metaLine = parts.length > TABLE ? parts[TABLE] : "";
                    JsonNode meta = readJson(block.metaLine, "META");
                    int version = meta.path("schemaVersion").asInt(-1);
                    if (version != SCHEMA_VERSION) {
                        throw new UnsupportedExportVersionException(version);
                    }
                    block.context = meta.path("context");
                    continue;
                }
                if (Objects.isNull(block)) {
                    continue;
                }
                Map<String, Table> tables = block.tables;
                switch (parts[KIND]) {
                    case "HEADER" -> header(table(tables, parts), stringList(readJson(parts[5], "HEADER")));
                    case "CHUNK" -> chunk(table(tables, parts), rawParts);
                    case "END" -> end(table(tables, parts), parts);
                    case "ERROR" -> table(tables, parts).error = parts.length > 5 ? parts[5] : "error";
                    case "DONE" -> block.warnings.addAll(stringList(readJson(parts.length > TABLE ? parts[TABLE] : "[]", "DONE")));
                    default -> {
                        // SCHEMA vb. veri taşımayan satırlar
                    }
                }
            }
        } catch (IOException e) {
            log.error("FCX2 log unreadable", e);
            throw new ImportParseException("log unreadable");
        }
        if (Objects.isNull(block)) {
            throw new ImportParseException("no FCX2 export found in the log");
        }
        selection.offer(block);
        Block chosen = selection.chosen();
        if (selection.seen > 1) {
            log.info("FCX2 log has exports : {} prefer : {} chosen : {}", selection.seen, prefer, chosen.index);
        }
        return finish(chosen.tables, chosen.warnings, chosen.context, chosen.metaLine, isCareer(chosen));
    }

    /**
     * Akan seçim: istenen türde en son tam (zorunlu tabloları sağlam) dışa aktarım; tamı yoksa istenen türün sonuncusu (finish eksik tabloyu
     * söyler). CAREER istenip hiç kariyer dışa aktarımı yoksa en son dışa aktarım (NOT_IN_CAREER uyarısıyla). BASE istenip hiç ana menü dışa
     * aktarımı yoksa reddedilir: kariyer verisi temel kataloğa asla yazılmaz.
     */
    private static final class Selection {
        private final boolean wantCareer;
        private Block best;
        private Block last;
        private int seen;

        private Selection(Prefer prefer) {
            this.wantCareer = prefer == Prefer.CAREER;
        }

        private void offer(Block block) {
            block.index = ++seen;
            if (isCareer(block) == wantCareer) {
                if (Objects.isNull(best) || complete(block) || !complete(best)) {
                    best = block;
                }
                last = null;
            } else if (Objects.isNull(best)) {
                last = block;
            }
        }

        private Block chosen() {
            if (Objects.nonNull(best)) {
                return best;
            }
            if (!wantCareer) {
                throw new ImportParseException("no main-menu (BASE) export in the log; career data is never imported into the base catalog");
            }
            return last;
        }
    }

    private static boolean complete(Block block) {
        return REQUIRED.stream().map(block.tables::get)
                .allMatch(t -> Objects.nonNull(t) && t.ended && t.valid && Objects.isNull(t.error));
    }

    /**
     * Kariyer içinden mi: inCareer boolean ise o. Script 2.0.0 boolean'ı null yazıyordu (jval); o zaman mode BASE değilse ve oyun tarihi ya da
     * kariyer tablosu varsa kariyer sayılır.
     */
    private static boolean isCareer(Block block) {
        JsonNode flag = block.context.path("inCareer");
        if (flag.isBoolean()) {
            return flag.asBoolean();
        }
        if ("BASE".equals(block.context.path("mode").asText(null))) {
            return false;
        }
        return block.context.hasNonNull("gameDate") || block.tables.containsKey("career_calendar") || block.tables.containsKey("career_users");
    }

    private static LeExport finish(Map<String, Table> tables, List<String> warnings, JsonNode context, String metaLine, boolean inCareer) {
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
        return new LeExport(exportId(metaLine, checksums), context(context, inCareer), result, List.copyOf(warnings));
    }

    private static Table table(Map<String, Table> tables, String[] parts) {
        return tables.computeIfAbsent(parts[TABLE], k -> new Table());
    }

    private static void header(Table table, List<String> columns) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            index.putIfAbsent(columns.get(i), i);
        }
        table.columns = columns;
        table.index = index;
    }

    /** rawParts: Latin-1 okunmuş (ham bayt) alanlar; özet ham baytla, satır UTF-8 metinle. */
    private static void chunk(Table table, String[] rawParts) {
        table.chunks++;
        String body = rawParts.length > 6 ? rawParts[6] : "[]";
        for (String raw : splitRows(body)) {
            table.hash = fnv(table.hash, raw.getBytes(StandardCharsets.ISO_8859_1));
            table.rows.add(toRow(table, utf8(raw)));
        }
    }

    private static String utf8(String latin1) {
        return new String(latin1.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
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
        return fnv(seed, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Ham baytlar üzerinde FNV-1a 32 bit (Lua dizgeleri bayt dizisidir). */
    static long fnv(long seed, byte[] bytes) {
        long h = seed;
        for (byte b : bytes) {
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

    private static Map<String, String> toRow(Table table, String raw) {
        JsonNode cells = readJson(raw, "row");
        String[] values = new String[Math.min(table.columns.size(), cells.size())];
        for (int i = 0; i < values.length; i++) {
            JsonNode cell = cells.get(i);
            if (!cell.isNull()) {
                String text = cell.asText();
                values[i] = text.length() <= SHORT_VALUE ? table.shortValues.computeIfAbsent(text, v -> v) : text;
            }
        }
        return new Row(table.columns, table.index, values);
    }

    /**
     * Salt okunur satır (OPS-03): sütun dizini tabloda bir kez tutulur, değerler dizide. 21,7 bin oyuncu × ~110 sütunda HashMap satırları
     * içe aktarmada heap'i yüzlerce MB büyütüyordu. Null hücre anahtar değildir (eski HashMap satırıyla aynı).
     */
    private static final class Row extends AbstractMap<String, String> {
        private final List<String> columns;
        private final Map<String, Integer> index;
        private final String[] values;

        private Row(List<String> columns, Map<String, Integer> index, String[] values) {
            this.columns = columns;
            this.index = index;
            this.values = values;
        }

        @Override
        public String get(Object key) {
            Integer i = index.get(key);
            return Objects.isNull(i) || i >= values.length ? null : values[i];
        }

        @Override
        public boolean containsKey(Object key) {
            return Objects.nonNull(get(key));
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            Set<Entry<String, String>> entries = new LinkedHashSet<>();
            for (int i = 0; i < values.length; i++) {
                if (Objects.nonNull(values[i]) && index.get(columns.get(i)) == i) {
                    entries.add(new SimpleImmutableEntry<>(columns.get(i), values[i]));
                }
            }
            return Collections.unmodifiableSet(entries);
        }
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

    private static LeContext context(JsonNode node, boolean inCareer) {
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
        JsonNode budget = node.path("transferBudget");
        Long transferBudget = budget.isNumber() && budget.asLong() >= 0 ? budget.asLong() : null;
        return new LeContext(inCareer, node.path("mode").asText(null), date, userTeam, node.path("saveUid").asText(null), transferBudget);
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
