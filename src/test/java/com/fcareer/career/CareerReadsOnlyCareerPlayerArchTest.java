package com.fcareer.career;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-1 mimari kuralı (13 §2 madde 1, "tek doğruluk kaynağı"): kariyer sorguları katalog tablosuyla (`player_rating`) birleşmez; yalnız `career_player` okur.
 * İhlal = kariyer tabloları (`career_player_data`, `career_player_rating`, `career_player`) ile `player_rating` aynı SQL metninde geçmesi,
 * ya da `career` paketinde `player_rating` geçmesi.
 *
 * Bilinen ihlaller (E3-2'de sıfırlanır) bir cırcır (ratchet) listesindedir: yeni ihlal build'i kırar; liste küçülmeden strict test yeşil olmaz.
 * Strict test E3-2'ye kadar kapalıdır; kırmızılığını görmek için `-Darch.strict=true`.
 */
class CareerReadsOnlyCareerPlayerArchTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Pattern CATALOG_RATING = Pattern.compile("(?<![A-Za-z_])player_rating(?![A-Za-z_])");
    private static final Pattern CAREER_TABLE = Pattern.compile("career_player_data|career_player_rating|career_player(?![A-Za-z_])");

    /** E3-2 bitince boş olmalı. Biçim: dosya adı (yol sonu). */
    private static final List<String> KNOWN_VIOLATIONS = List.of(
            "career/desk/CareerDeskService.java",
            "career/service/CareerService.java",
            "catalog/repository/PlayerSearchRepository.java");

    private List<String> violations() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter((p) -> p.toString().endsWith(".java")).toList()) {
                String relative = MAIN.relativize(file).toString().replace(java.io.File.separatorChar, '/').replaceFirst("^com/fcareer/", "");
                String text = Files.readString(file);
                boolean mixesCatalogAndCareer = CATALOG_RATING.matcher(text).find() && CAREER_TABLE.matcher(text).find();
                boolean careerPackageReadsCatalog = relative.startsWith("career/") && CATALOG_RATING.matcher(text).find();
                if ((mixesCatalogAndCareer || careerPackageReadsCatalog) && !isIngestionTooling(relative)) {
                    found.add(relative);
                }
            }
        }
        return found.stream().sorted().toList();
    }

    /** Katalog/kariyer içe aktarımı iki tabloya da yazmak zorundadır; okuma yolu değildir. */
    private static boolean isIngestionTooling(String relative) {
        return relative.startsWith("ingestion/") || relative.startsWith("catalog/entity/");
    }

    @Test
    void noNewCareerQueryMixesTheCatalogRatingTable() throws IOException {
        assertThat(violations()).as("yeni ihlal: kariyer sorgusu player_rating ile birleşiyor; career_player kullan").isSubsetOf(KNOWN_VIOLATIONS);
    }

    @Test
    void theKnownViolationListOnlyContainsFilesThatStillViolate() throws IOException {
        assertThat(KNOWN_VIOLATIONS).as("düzelen dosyayı listeden çıkar (cırcır)").isSubsetOf(violations());
    }

    @Test
    @EnabledIfSystemProperty(named = "arch.strict", matches = "true")
    void strictCareerReadsNeverTouchTheCatalogRatingTable() throws IOException {
        assertThat(violations()).as("E3-2 hedefi: kariyer okuması yalnız career_player").isEmpty();
    }
}
