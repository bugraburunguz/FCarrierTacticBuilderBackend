package com.fcareer.career;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mimari kural (13 §2 madde 1, "tek doğruluk kaynağı"): kariyer sorguları katalog tablosuyla (`player_rating`) birleşmez; yalnız `career_player` okur.
 * İhlal = kariyer tabloları (`career_player_data`, `career_player_rating`, `career_player`) ile `player_rating` aynı kaynak dosyasında geçmesi,
 * ya da `career` paketinde `player_rating` geçmesi. E3-2'de bilinen ihlal listesi (E3-1 cırcırı) sıfırlandı: kural artık koşulsuz.
 * Muaf: `ingestion/` (katalog ve overlay'e yazan içe aktarıcılar; CareerPlayerWriter türetmeyi orada yapar) ve JPA varlıkları.
 */
class CareerReadsOnlyCareerPlayerArchTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Pattern CATALOG_RATING = Pattern.compile("(?<![A-Za-z_])player_rating(?![A-Za-z_])");
    private static final Pattern CAREER_TABLE = Pattern.compile("career_player_data|career_player_rating|career_player(?![A-Za-z_])");

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

    private static boolean isIngestionTooling(String relative) {
        return relative.startsWith("ingestion/") || relative.startsWith("catalog/entity/");
    }

    @Test
    void careerReadsNeverTouchTheCatalogRatingTable() throws IOException {
        assertThat(violations()).as("kariyer sorgusu player_rating ile birleşiyor; career_player kullan").isEmpty();
    }

    @Test
    void theRuleActuallyDetectsAMixedQuery() {
        String mixed = "SELECT * FROM player_rating r LEFT JOIN career_player_data d ON d.base_player_id = r.base_player_id";
        assertThat(CATALOG_RATING.matcher(mixed).find() && CAREER_TABLE.matcher(mixed).find()).isTrue();
        assertThat(CATALOG_RATING.matcher("SELECT * FROM career_player cp").find()).isFalse();
        assertThat(CAREER_TABLE.matcher("class CareerPlayerSearchRepository").find()).isFalse();
    }
}
