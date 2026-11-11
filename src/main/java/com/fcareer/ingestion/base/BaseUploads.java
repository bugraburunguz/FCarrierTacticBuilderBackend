package com.fcareer.ingestion.base;

import com.fcareer.career.importer.CsvStream;
import com.fcareer.common.exception.ImportParseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
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

    /**
     * FCA-21: gzip'li logun açılmış en büyük boyutu (sıkıştırma bombasına karşı). Sıkıştırılmamış yükleme sınırıyla (64 MB) aynı: 512 MB yığında
     * ayrıştırıcının bellek tavanı değişmez. Kırpılmış tek kariyer export'u ~11 MB.
     */
    public static final long MAX_INFLATED_BYTES = 64L * 1024 * 1024;
    private static final int GZIP_MAGIC_FIRST = 0x1f;
    private static final int GZIP_MAGIC_SECOND = 0x8b;

    private BaseUploads() {
    }

    /** Yüklenen veri: tablolar + (v2 export ya da CSV `context` tablosundan) bağlam, export kimliği ve uyarılar. */
    public record Upload(Map<String, List<Map<String, String>>> tables, LeExportV2Parser.LeContext context, String exportId, List<String> warnings) {
    }

    /** Ya tek bir Live Editor log dosyası (v2 FCX2 ya da eski FCDUMP) ya da tablo başına bir CSV dosyası kabul edilir. */
    /** Temel katalog yüklemesi: logdaki ana menü (BASE) dışa aktarımı; yalnız kariyer dışa aktarımı varsa reddedilir. */
    public static Map<String, List<Map<String, String>>> toTables(MultipartFile log, Map<String, MultipartFile> csvFiles) {
        return toUpload(log, csvFiles, LeExportV2Parser.Prefer.BASE).tables();
    }

    /** Kariyer içe aktarımı: logdaki en son kariyer dışa aktarımı. */
    public static Upload toUpload(MultipartFile log, Map<String, MultipartFile> csvFiles) {
        return toUpload(log, csvFiles, LeExportV2Parser.Prefer.CAREER);
    }

    public static Upload toUpload(MultipartFile log, Map<String, MultipartFile> csvFiles, LeExportV2Parser.Prefer prefer) {
        return toUpload(log, csvFiles, prefer, MAX_INFLATED_BYTES);
    }

    public static Upload toUpload(MultipartFile log, Map<String, MultipartFile> csvFiles, LeExportV2Parser.Prefer prefer, long maxInflatedBytes) {
        if (Objects.nonNull(log) && !log.isEmpty()) {
            try {
                boolean v2;
                try (InputStream probe = open(log, maxInflatedBytes)) {
                    v2 = LeExportV2Parser.isV2(probe);
                }
                try (InputStream in = open(log, maxInflatedBytes)) {
                    if (v2) {
                        LeExportV2Parser.LeExport export = LeExportV2Parser.parse(in, prefer);
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

    /** FCA-21: gzip'li log (ilk iki bayt 1f 8b) açılarak okunur; açılmış boyut maxInflatedBytes ile sınırlı. Diğer dosyalar olduğu gibi. */
    private static InputStream open(MultipartFile log, long maxInflatedBytes) throws IOException {
        BufferedInputStream in = new BufferedInputStream(log.getInputStream());
        in.mark(2);
        int first = in.read();
        int second = in.read();
        in.reset();
        if (first != GZIP_MAGIC_FIRST || second != GZIP_MAGIC_SECOND) {
            return in;
        }
        try {
            return new LimitedInputStream(new GZIPInputStream(in), maxInflatedBytes);
        } catch (IOException e) {
            in.close();
            throw e;
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                checkLimit(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                checkLimit(n);
            }
            return n;
        }

        private void checkLimit(long added) {
            count += added;
            if (count > limit) {
                throw new ImportParseException("log too large after decompression");
            }
        }
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
        return new LeExportV2Parser.LeContext(!"0".equals(row.get("incareer")), row.get("mode"), date, Objects.nonNull(team) && team > 0 ? team : null, row.get("saveuid"), null);
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
