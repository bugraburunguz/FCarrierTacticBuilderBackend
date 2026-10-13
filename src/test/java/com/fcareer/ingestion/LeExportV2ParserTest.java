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

    @Test
    void aLogWithoutAnyFcx2MetaIsNotAV2Export() {
        assertThat(LeExportV2Parser.isV2(new ByteArrayInputStream("10:00\tINFO\t[LUA API] FCDUMP\tBEGIN\tplayers\tfields=3".getBytes(StandardCharsets.UTF_8)))).isFalse();
        assertThat(LeExportV2Parser.isV2(new ByteArrayInputStream(String.join("\n", goodLog()).getBytes(StandardCharsets.UTF_8)))).isTrue();
    }
}
