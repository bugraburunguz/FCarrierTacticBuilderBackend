package com.fcareer.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Tek kaynak LE export script'i (tools/live-editor): v2 şeması, salt-okur güvenceleri ve web kopyasıyla senkron özeti. */
class LeExportScriptTest {

    private static final Path DIR = Path.of("tools", "live-editor");
    private static final Path SCRIPT = DIR.resolve("fc27_career_export.lua");

    private static String script() throws Exception {
        return Files.readString(SCRIPT, StandardCharsets.UTF_8);
    }

    @Test
    void theRecordedHashMatchesTheScript() throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(SCRIPT)));
        String recorded = Files.readString(DIR.resolve("fc27_career_export.lua.sha256")).trim();

        assertThat(actual).as("script değişti: .sha256 dosyasını yenile ve web'de `npm run sync:le-script` çalıştır").isEqualTo(recorded);
    }

    @Test
    void speaksTheV2ProtocolTheBackendParses() throws Exception {
        String lua = script();

        assertThat(lua).contains("\"FCX2\"").contains("\"FCX2\", \"2.0.0\", 2,").contains("fccareer.le-export");
        for (String kind : new String[]{"META", "HEADER", "CHUNK", "END", "ERROR", "DONE"}) {
            assertThat(lua).contains("emit(\"" + kind + "\"");
        }
    }

    @Test
    void neverWritesToTheSaveAndHasNoDeveloperFacingText() throws Exception {
        String lua = script();

        assertThat(lua).doesNotContain("GetSaveUID(").doesNotContain("SetRecordFieldValue").doesNotContain("Bana");
        assertThat(lua).contains("FC Assist dışa aktarım bitti");
    }

    @Test
    void thereIsExactlyOneExportScriptAndTheRetiredDumpScriptsAreGone() {
        assertThat(DIR.resolve("fc27_dump.lua")).doesNotExist();
        assertThat(DIR.resolve("fc27_base_export.lua")).doesNotExist();
        assertThat(DIR.resolve("dump_harness.lua")).doesNotExist();
    }
}
