package com.fcareer.hardening;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REVIEW (testler, dokümantasyon): varsayılan atlanan her opt-in test bayrağı (EnabledIfSystemProperty anotasyonunun named değeri) CLAUDE.md'de
 * {@code -D<ad>} olarak yazılı olmalı; yoksa test unutulur. Anotasyon tam adla, boşluksuz ya da {@code matches} önce yazılabilir; regex parantez
 * içinde {@code named} değerini arar.
 */
class OptInFlagsDocumentedTest {

    private static final Pattern FLAG = Pattern.compile("@(?:[\\w.]+\\.)?EnabledIfSystemProperty\\s*\\([^)]*?\\bnamed\\s*=\\s*\"([^\"]+)\"");

    @Test
    void everyOptInFlagIsDocumentedInClaudeMd() throws IOException {
        String doc = Files.readString(Path.of("CLAUDE.md"));
        TreeSet<String> flags = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            for (Path file : files.filter((f) -> f.toString().endsWith(".java")).toList()) {
                Matcher matcher = FLAG.matcher(Files.readString(file));
                while (matcher.find()) {
                    flags.add(matcher.group(1));
                }
            }
        }
        assertThat(flags).as("opt-in bayrakları kaynaktan okunabilmeli").isNotEmpty();
        List<String> missing = flags.stream().filter((flag) -> !Pattern.compile("-D" + Pattern.quote(flag) + "(?![\\w.])").matcher(doc).find()).toList();
        assertThat(missing).as("CLAUDE.md'de -D ile yazılmamış opt-in bayrakları").isEmpty();
    }
}
