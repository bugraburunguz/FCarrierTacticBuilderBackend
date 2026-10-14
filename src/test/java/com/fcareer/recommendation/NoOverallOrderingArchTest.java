package com.fcareer.recommendation;

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
 * Mimari kural (13 §3.3 madde 2, Buğra kararı): öneri / danışman / gelişim yollarında aday seçimi OVR'ye göre sıralanıp kesilmez.
 * Yasak: sort("overall") ve SQL'de ORDER BY ... overall. Kapsam: recommendation, advisor, development paketleri (aday üretimi yapan kod);
 * takım özeti / UT meta gibi gösterim sıralamaları kapsam dışıdır.
 */
class NoOverallOrderingArchTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "fcareer");
    private static final List<String> PACKAGES = List.of("recommendation", "advisor", "development");
    private static final Pattern SORT_OVERALL = Pattern.compile("sort\\(\\s*\"overall\"\\s*\\)");
    private static final Pattern ORDER_BY_OVERALL = Pattern.compile("(?i)ORDER\\s+BY\\s+[^\"\\n;]*\\boverall\\b");

    @Test
    void candidateSelectionNeverOrdersByOverall() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String pkg : PACKAGES) {
            try (Stream<Path> files = Files.walk(MAIN.resolve(pkg))) {
                for (Path file : files.filter((p) -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file);
                    if (SORT_OVERALL.matcher(text).find() || ORDER_BY_OVERALL.matcher(text).find()) {
                        violations.add(MAIN.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        }
        assertThat(violations).as("OVR sıralaması yasak; sort(\"prescore\") kullan").isEmpty();
    }

    @Test
    void theRuleDetectsBothForms() {
        assertThat(SORT_OVERALL.matcher(".sort(\"overall\")").find()).isTrue();
        assertThat(ORDER_BY_OVERALL.matcher("SELECT x FROM t ORDER BY r.overall DESC").find()).isTrue();
        assertThat(ORDER_BY_OVERALL.matcher("ORDER BY prescore DESC").find()).isFalse();
    }
}
