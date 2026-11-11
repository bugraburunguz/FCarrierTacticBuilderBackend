package com.fcareer.ingestion;

import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.base.BaseUploads;
import com.fcareer.ingestion.base.LeExportV2Parser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FCA-21: site export'u gzip'le gönderir; sunucu ilk iki bayttan (1f 8b) tanır ve açar. Açılmış boyut sınırlıdır (sıkıştırma bombası), sınırı aşan
 * dosya 400 (ImportParseException). Sıkıştırılmamış log eskisi gibi okunur.
 */
class BaseUploadsGzipTest {

    private static final String PREFIX = "10:00:00.1\tINFO\t[LUA API] FCX2\t";

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    private static String fnv(String row) {
        long h = 2166136261L;
        for (byte b : row.getBytes(StandardCharsets.UTF_8)) {
            h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
        }
        return String.format("fnv1a32:%08x", h);
    }

    private static String table(String name, String header, String row) {
        return PREFIX + "HEADER\t" + name + "\t" + header + "\n" + PREFIX + "CHUNK\t" + name + "\t1\t[" + row + "]\n"
                + PREFIX + "END\t" + name + "\t1\t" + fnv(row) + "\t1\n";
    }

    private static String log() {
        return PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"script\":\"2.0.5\",\"context\":{\"inCareer\":true,\"gameDate\":\"2029-07-01\",\"userTeamId\":7}}\n"
                + table("players", "[\"playerid\",\"overallrating\"]", "[1,70]")
                + table("teams", "[\"teamid\",\"teamname\"]", "[7,\"A\"]")
                + table("teamplayerlinks", "[\"playerid\",\"teamid\"]", "[1,7]")
                + PREFIX + "DONE\t[]";
    }

    @Test
    void aGzippedLogIsReadLikeThePlainOne() throws IOException {
        byte[] raw = log().getBytes(StandardCharsets.ISO_8859_1);
        BaseUploads.Upload plain = BaseUploads.toUpload(new MockMultipartFile("log", "a.log", "text/plain", raw), Map.of());
        BaseUploads.Upload zipped = BaseUploads.toUpload(new MockMultipartFile("log", "a.log.gz", "application/gzip", gzip(raw)), Map.of());
        assertThat(zipped.context()).isEqualTo(plain.context());
        assertThat(zipped.exportId()).isEqualTo(plain.exportId());
        assertThat(zipped.tables().get("players")).isEqualTo(plain.tables().get("players"));
        assertThat(zipped.context().userTeamId()).isEqualTo(7);
    }

    @Test
    void aGzipThatInflatesPastTheLimitIsRejected() throws IOException {
        byte[] big = new byte[4096];
        byte[] zipped = gzip(big);
        MockMultipartFile file = new MockMultipartFile("log", "bomb.log.gz", "application/gzip", zipped);
        assertThatThrownBy(() -> BaseUploads.toUpload(file, Map.of(), LeExportV2Parser.Prefer.CAREER, 1024)).isInstanceOf(ImportParseException.class);
        byte[] raw = log().getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile export = new MockMultipartFile("log", "a.log.gz", "application/gzip", gzip(raw));
        assertThat(BaseUploads.toUpload(export, Map.of(), LeExportV2Parser.Prefer.CAREER, raw.length).context()).isNotNull();
        assertThatThrownBy(() -> BaseUploads.toUpload(export, Map.of(), LeExportV2Parser.Prefer.CAREER, raw.length - 10L))
                .as("sınır ayrıştırıcının okuduğu ikinci açılışta da geçerli").isInstanceOf(ImportParseException.class);
        assertThat(BaseUploads.MAX_INFLATED_BYTES).as("sıkıştırılmamış yükleme sınırıyla aynı").isEqualTo(64L * 1024 * 1024);
    }

    @Test
    void aCorruptGzipIsRejectedAsUnreadable() {
        MockMultipartFile file = new MockMultipartFile("log", "bad.log.gz", "application/gzip", new byte[] {0x1f, (byte) 0x8b, 0, 0, 0});
        assertThatThrownBy(() -> BaseUploads.toUpload(file, Map.of())).isInstanceOf(ImportParseException.class);
    }

    @Test
    void rawNonUtf8BytesSurviveTheGzipRoundTripSoTheChecksumStillMatches() throws IOException {
        String row = "[1,\"M" + (char) 0xfc + "ller\"]";
        String text = PREFIX + "META\t{\"schema\":\"fccareer.le-export\",\"schemaVersion\":2,\"context\":{\"inCareer\":true,\"gameDate\":\"2029-07-01\",\"userTeamId\":7}}\n"
                + table("players", "[\"playerid\",\"overallrating\"]", "[1,70]")
                + table("teams", "[\"teamid\",\"teamname\"]", "[7,\"A\"]")
                + table("teamplayerlinks", "[\"playerid\",\"teamid\"]", "[1,7]")
                + PREFIX + "HEADER\tdcplayernames\t[\"nameid\",\"name\"]\n" + PREFIX + "CHUNK\tdcplayernames\t1\t[" + row + "]\n"
                + PREFIX + "END\tdcplayernames\t1\t" + fnvLatin1(row) + "\t1\n" + PREFIX + "DONE\t[]";
        byte[] raw = text.getBytes(StandardCharsets.ISO_8859_1);
        BaseUploads.Upload plain = BaseUploads.toUpload(new MockMultipartFile("log", "a.log", "text/plain", raw), Map.of());
        BaseUploads.Upload zipped = BaseUploads.toUpload(new MockMultipartFile("log", "a.log.gz", "application/gzip", gzip(raw)), Map.of());
        assertThat(zipped.tables().get("dcplayernames")).isEqualTo(plain.tables().get("dcplayernames")).isNotEmpty();
        assertThat(zipped.warnings()).isEqualTo(plain.warnings());
    }

    private static String fnvLatin1(String row) {
        long h = 2166136261L;
        for (byte b : row.getBytes(StandardCharsets.ISO_8859_1)) {
            h = ((h ^ (b & 0xFF)) * 16777619L) & 0xFFFFFFFFL;
        }
        return String.format("fnv1a32:%08x", h);
    }
}
