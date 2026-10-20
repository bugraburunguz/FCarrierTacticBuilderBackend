package com.fcareer.ingestion;

import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.base.LeExportV2Parser;
import com.fcareer.ingestion.base.LeExportV2Parser.LeExport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** LE export v2 (07 §4): FCX2 satırlarından tablo, context ve bütünlük doğrulaması. */
class LeExportV2ParserTest {

    private static final String PREFIX = "10:00:00.1\tINFO\t[LUA API] FCX2\t";
    private static final String META = "{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.0\",\"context\":{\"inCareer\":true,\"mode\":\"MANAGER\",\"gameDate\":\"2027-01-14\",\"userTeamId\":121,\"userTeamIdMem\":121}}";

    /** Lua'daki fnv(): satır dizgelerinin FNV-1a 32 bit özeti. */
    private static String fnv(List<String> rows) {
        long h = 2166136261L;
        for (String row : rows) {
            for (byte b : row.getBytes(StandardCharsets.UTF_8)) {
                h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
            }
        }
        return String.format("fnv1a32:%08x", h);
    }

    private static void table(List<String> log, String name, String header, List<String> rows, int perChunk) {
        log.add(PREFIX + "HEADER\t" + name + "\t" + header);
        int seq = 0;
        for (int i = 0; i < rows.size(); i += perChunk) {
            seq++;
            log.add(PREFIX + "CHUNK\t" + name + "\t" + seq + "\t[" + String.join(",", rows.subList(i, Math.min(i + perChunk, rows.size()))) + "]");
        }
        log.add(PREFIX + "END\t" + name + "\t" + rows.size() + "\t" + fnv(rows) + "\t" + seq);
    }

    private static List<String> goodLog() {
        List<String> log = new ArrayList<>();
        log.add("noise line without marker");
        log.add(PREFIX + "META\t" + META);
        table(log, "players", "[\"playerid\",\"overallrating\",\"firstname\"]", List.of("[20801,84,\"Ada, \\\"B\\\"\"]", "[231747,91,null]", "[5,60.5,\"x\"]"), 2);
        table(log, "teams", "[\"teamid\",\"teamname\"]", List.of("[121,\"Crewe Alexandra\"]"), 10);
        table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[20801,121]"), 10);
        log.add(PREFIX + "DONE\t[\"MISSINGTABLE nations\"]");
        return log;
    }

    private static LeExport parse(List<String> lines) {
        return LeExportV2Parser.parse(new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesTablesRowsContextAndWarnings() {
        LeExport export = parse(goodLog());

        assertThat(export.tables().get("players")).hasSize(3);
        assertThat(export.tables().get("players").get(0)).containsEntry("playerid", "20801").containsEntry("firstname", "Ada, \"B\"");
        assertThat(export.tables().get("players").get(1)).containsEntry("overallrating", "91").doesNotContainKey("firstname");
        assertThat(export.tables().get("players").get(2)).containsEntry("overallrating", "60.5");
        assertThat(export.context().inCareer()).isTrue();
        assertThat(export.context().gameDate()).isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(export.context().userTeamId()).isEqualTo(121);
        assertThat(export.warnings()).containsExactly("MISSINGTABLE nations");
        assertThat(export.exportId()).isNotBlank();
    }

    /**
     * OPS-03: 21,7 bin oyuncu × ~110 sütun HashMap satırları içe aktarmada heap'i yüzlerce MB büyütüyordu. Satırlar salt okunur, sütun
     * dizini tabloda paylaşılır, kısa tekrar eden değerler tek kopyadır. Map davranışı aynı kalır (null hücre anahtar değildir).
     */
    @Test
    void rowsAreCompactReadOnlyMapsThatShareRepeatedShortValues() {
        List<String> log = new ArrayList<>();
        log.add(PREFIX + "META\t" + META);
        table(log, "players", "[\"playerid\",\"overallrating\",\"firstname\"]", List.of("[1,84,\"Ada\"]", "[2,84,null]"), 10);
        table(log, "teams", "[\"teamid\",\"teamname\"]", List.of("[121,\"Crewe Alexandra\"]"), 10);
        table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[1,121]"), 10);
        log.add(PREFIX + "DONE\t[]");

        List<java.util.Map<String, String>> players = parse(log).tables().get("players");
        java.util.Map<String, String> first = players.get(0);
        java.util.Map<String, String> second = players.get(1);

        assertThat(first).isEqualTo(java.util.Map.of("playerid", "1", "overallrating", "84", "firstname", "Ada"));
        assertThat(second).hasSize(2).doesNotContainKey("firstname").containsEntry("overallrating", "84");
        assertThat(second.get("missing-column")).isNull();
        assertThat(second.get("overallrating")).as("kısa değer tek kopya").isSameAs(first.get("overallrating"));
        assertThatThrownBy(() -> first.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theExportIdIsStableForTheSameDataAndChangesWhenDataChanges() {
        // checksum da yeniden hesaplanır: bütünlük hatası değil veri farkı test edilir
        List<String> rows = List.of("[20801,84,\"Ada, \\\"B\\\"\"]", "[231747,92,null]", "[5,60.5,\"x\"]");
        List<String> second = new ArrayList<>();
        second.add(PREFIX + "META\t" + META);
        table(second, "players", "[\"playerid\",\"overallrating\",\"firstname\"]", rows, 2);
        table(second, "teams", "[\"teamid\",\"teamname\"]", List.of("[121,\"Crewe Alexandra\"]"), 10);
        table(second, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[20801,121]"), 10);

        assertThat(parse(goodLog()).exportId()).isEqualTo(parse(goodLog()).exportId());
        assertThat(parse(second).exportId()).isNotEqualTo(parse(goodLog()).exportId());
    }

    @Test
    void theLastRunInAMultiRunLogWins() {
        List<String> log = new ArrayList<>();
        log.add(PREFIX + "META\t" + META.replace("\"userTeamId\":121", "\"userTeamId\":1").replace("2027-01-14", "2026-08-01"));
        table(log, "players", "[\"playerid\"]", List.of("[1]"), 10);
        log.addAll(goodLog());

        LeExport export = parse(log);

        assertThat(export.context().userTeamId()).isEqualTo(121);
        assertThat(export.tables().get("players")).hasSize(3);
    }

    @Test
    void aChecksumMismatchRejectsTheImportAndNamesTheTable() {
        List<String> log = goodLog();
        int chunk = log.indexOf(log.stream().filter(l -> l.contains("CHUNK	players	1")).findFirst().orElseThrow());
        log.set(chunk, log.get(chunk).replace("20801", "20802"));

        assertThatThrownBy(() -> parse(log)).isInstanceOf(ImportParseException.class).hasMessageContaining("Import file");
        assertThatThrownBy(() -> parse(log)).satisfies(e -> assertThat(((ImportParseException) e).getArgs()[0].toString()).contains("players"));
    }

    @Test
    void aMissingEndOrTruncatedRowCountRejectsTheImport() {
        List<String> log = goodLog();
        log.removeIf(l -> l.contains("END\tteams"));

        assertThatThrownBy(() -> parse(log)).isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getArgs()[0].toString()).contains("teams"));
    }

    @Test
    void aLuaSideErrorOnARequiredTableRejectsTheImport() {
        List<String> log = goodLog();
        log.removeIf(l -> l.contains("teamplayerlinks"));
        log.add(PREFIX + "ERROR\tteamplayerlinks\tzorunlu tablo yok");

        assertThatThrownBy(() -> parse(log)).isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getArgs()[0].toString()).contains("teamplayerlinks"));
    }

    @Test
    void anUnknownSchemaVersionIsRejectedWith422() {
        List<String> log = goodLog();
        log.set(1, log.get(1).replace("\"schemaVersion\":2", "\"schemaVersion\":9"));

        assertThatThrownBy(() -> parse(log)).isInstanceOf(LeExportV2Parser.UnsupportedExportVersionException.class)
                .satisfies(e -> assertThat(((LeExportV2Parser.UnsupportedExportVersionException) e).httpStatus().value()).isEqualTo(422));
    }

    /** Bir LE log'u birden çok dışa aktarım içerebilir (oturum boyunca birikir): kariyer içinden ve ana menüden. */
    private static List<String> careerThenBaseLog(String careerContext) {
        List<String> log = new ArrayList<>();
        log.add(PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.0\",\"context\":" + careerContext + "}");
        table(log, "players", "[\"playerid\",\"overallrating\"]", List.of("[82789,70]"), 10);
        table(log, "teams", "[\"teamid\",\"teamname\"]", List.of("[1928,\"Port Vale\"]"), 10);
        table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[82789,1928]"), 10);
        table(log, "career_calendar", "[\"currdate\"]", List.of("[20260912]"), 10);
        log.add(PREFIX + "DONE\t[]");
        log.add(PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.0\",\"context\":{\"mode\":\"BASE\",\"inCareer\":false}}");
        table(log, "players", "[\"playerid\",\"overallrating\"]", List.of("[82789,66]"), 10);
        table(log, "teams", "[\"teamid\",\"teamname\"]", List.of("[245,\"Ajax\"]"), 10);
        table(log, "teamplayerlinks", "[\"playerid\",\"teamid\"]", List.of("[82789,245]"), 10);
        log.add(PREFIX + "DONE\t[]");
        return log;
    }

    private static LeExport parse(List<String> lines, LeExportV2Parser.Prefer prefer) {
        return LeExportV2Parser.parse(new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)), prefer);
    }

    @Test
    void careerImportPicksTheCareerExportEvenWhenAMainMenuExportComesLater() {
        LeExport export = parse(careerThenBaseLog("{\"inCareer\":true,\"mode\":\"MANAGER\",\"gameDate\":\"2026-09-12\",\"userTeamIdMem\":1928}"), LeExportV2Parser.Prefer.CAREER);

        assertThat(export.context().inCareer()).isTrue();
        assertThat(export.tables().get("players").get(0)).containsEntry("overallrating", "70");
        assertThat(export.tables()).containsKey("career_calendar");
    }

    @Test
    void baseImportPicksTheMainMenuExport() {
        LeExport export = parse(careerThenBaseLog("{\"inCareer\":true,\"mode\":\"MANAGER\",\"gameDate\":\"2026-09-12\"}"), LeExportV2Parser.Prefer.BASE);

        assertThat(export.context().inCareer()).isFalse();
        assertThat(export.tables().get("players").get(0)).containsEntry("overallrating", "66");
    }

    /** Script 2.0.0 boolean'ı null yazıyordu (jval): oyun tarihi ya da kariyer tabloları olan dışa aktarım kariyer sayılır. */
    @Test
    void aNullInCareerFlagWithCareerDataCountsAsCareer() {
        LeExport export = parse(careerThenBaseLog("{\"inCareer\":null,\"mode\":\"UNKNOWN\",\"gameDate\":\"2026-09-12\",\"userTeamIdMem\":1928}"), LeExportV2Parser.Prefer.CAREER);

        assertThat(export.context().inCareer()).isTrue();
        assertThat(export.context().gameDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(export.context().userTeamId()).isEqualTo(1928);
    }

    @Test
    void aBaseImportNeverUsesACareerExport() {
        List<String> careerOnly = careerThenBaseLog("{\"inCareer\":null,\"mode\":\"UNKNOWN\",\"gameDate\":\"2026-09-12\"}");
        careerOnly.subList(careerOnly.indexOf(careerOnly.stream().filter(l -> l.contains("\"mode\":\"BASE\"")).findFirst().orElseThrow()), careerOnly.size()).clear();

        assertThatThrownBy(() -> parse(careerOnly, LeExportV2Parser.Prefer.BASE)).isInstanceOf(ImportParseException.class)
                .satisfies(e -> assertThat(((ImportParseException) e).getArgs()[0].toString()).contains("BASE"));
    }

    @Test
    void aCareerImportOfAMainMenuOnlyLogStillWorksButIsNotInCareer() {
        List<String> baseOnly = careerThenBaseLog("{\"inCareer\":true}");
        baseOnly.subList(0, baseOnly.indexOf(baseOnly.stream().filter(l -> l.contains("\"mode\":\"BASE\"")).findFirst().orElseThrow())).clear();

        LeExport export = parse(baseOnly, LeExportV2Parser.Prefer.CAREER);
        assertThat(export.context().inCareer()).isFalse();
        assertThat(export.tables().get("players").get(0)).containsEntry("overallrating", "66");
    }

    /**
     * 2.0.3 kariyer log'u: nations.isocountrycode alanına bellekten çöp bayt karışıyor ("AL" + 0xB8 ...). Lua özeti ham baytlar üzerinden;
     * log UTF-8 okununca geçersiz bayt U+FFFD'ye dönüp özet tutmuyor ve tablo INCOMPLETE sayılıyordu. Özet ham baytla doğrulanmalı.
     */
    @Test
    void checksumsAreVerifiedOnRawBytesEvenWhenAStringHasInvalidUtf8() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (String line : goodLog().subList(0, goodLog().size() - 1)) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        byte[] bad = "[1,\"AL".getBytes(StandardCharsets.UTF_8);
        byte[] row = new byte[bad.length + 1 + "\",\"Türkiye\"]".getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(bad, 0, row, 0, bad.length);
        row[bad.length] = (byte) 0xB8;
        byte[] tail = "\",\"Türkiye\"]".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(tail, 0, row, bad.length + 1, tail.length);
        long h = 2166136261L;
        for (byte b : row) {
            h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
        }
        out.write((PREFIX + "HEADER\tnations\t[\"nationid\",\"isocountrycode\",\"nationname\"]\n" + PREFIX + "CHUNK\tnations\t1\t[").getBytes(StandardCharsets.UTF_8));
        out.write(row);
        out.write(("]\n" + PREFIX + "END\tnations\t1\t" + String.format("fnv1a32:%08x", h) + "\t1\n" + PREFIX + "DONE\t[]").getBytes(StandardCharsets.UTF_8));

        LeExport export = LeExportV2Parser.parse(new ByteArrayInputStream(out.toByteArray()));

        assertThat(export.warnings()).noneMatch(w -> w.startsWith("INCOMPLETE"));
        assertThat(export.tables().get("nations").get(0)).containsEntry("nationid", "1").containsEntry("nationname", "Türkiye");
    }

    @Test
    void aLogWithoutAnyFcx2MetaIsNotAV2Export() {
        assertThat(LeExportV2Parser.isV2(new ByteArrayInputStream("10:00\tINFO\t[LUA API] FCDUMP\tBEGIN\tplayers\tfields=3".getBytes(StandardCharsets.UTF_8)))).isFalse();
        assertThat(LeExportV2Parser.isV2(new ByteArrayInputStream(String.join("\n", goodLog()).getBytes(StandardCharsets.UTF_8)))).isTrue();
    }
}
