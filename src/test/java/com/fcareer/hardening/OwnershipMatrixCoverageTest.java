package com.fcareer.hardening;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REVIEW (testler, güvenlik): {@code /api/v1/careers/{…}} altındaki her Spring MVC ucu (yöntem + kalıp) {@link OwnershipMatrixIntegrationTest}'teki
 * en az bir {@code new Call(HttpMethod.X, "/api/v1/careers/" + c + "…")} çağrısıyla eşleşmeli. Yeni bir kariyer ucu matrise eklenmeden gelirse
 * sahiplik denetimi sınanmamış olur; bu test onu yakalar. Matris yolları kaynaktan okunur: dize parçaları korunur, değişkenler ({@code c},
 * {@code squadPlayer} …) tek bir yol parçasına ("1") çevrilir, sonra Spring'in kendi {@link PathPattern} eşleştiricisiyle kalıba bakılır.
 * Kariyer kimliği yolda değil gövdede/sorguda taşıyan uçlar (fit, scouting, advisor, players?career=) bu testin kapsamı dışındadır; matriste
 * elle tutulur. Şu an {@code /api/v1/careers/{…}} altında sahibine bakmayan (admin vb.) bir kalıp yoktur; çıkarsa aşağıdaki listeye gerekçeyle
 * eklenir.
 */
@IntegrationTest
class OwnershipMatrixCoverageTest {

    private static final String MATRIX = "src/test/java/com/fcareer/hardening/OwnershipMatrixIntegrationTest.java";
    private static final Pattern CALL = Pattern.compile("new Call\\(HttpMethod\\.(\\w+),\\s*\"/api/v1/careers/\"((?:\\s*\\+\\s*(?:\"[^\"]*\"|[\\w.]+))*)");
    private static final Pattern PART = Pattern.compile("\"([^\"]*)\"|([\\w.]+)");
    /** Sahiplik denetimi dışında bilerek tutulan "YÖNTEM kalıp" çiftleri (şu an yok). */
    private static final List<String> EXEMPT = List.of();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    void everyCareerScopedEndpointIsInTheMatrix() throws Exception {
        List<String[]> matrixCalls = matrixCalls();
        assertThat(matrixCalls).as("matris çağrıları kaynaktan okunabilmeli").hasSizeGreaterThan(10);

        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            List<String> methods = info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/careers/{")) {
                    continue;
                }
                PathPattern parsed = PathPatternParser.defaultInstance.parse(pattern);
                for (String method : methods.isEmpty() ? List.of("ANY") : methods) {
                    String key = method + " " + pattern;
                    boolean covered = matrixCalls.stream().anyMatch((call) -> (method.equals("ANY") || call[0].equals(method))
                            && parsed.matches(PathContainer.parsePath(call[1])));
                    if (!covered && !EXEMPT.contains(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        assertThat(missing.stream().sorted().toList()).as("OwnershipMatrixIntegrationTest'e eklenmemiş kariyer uçları").isEmpty();
    }

    /** Matristeki kariyer çağrıları: {yöntem, somut yol}. Sorgu dizesi atılır. */
    private static List<String[]> matrixCalls() throws Exception {
        String source = Files.readString(Path.of(MATRIX));
        List<String[]> calls = new ArrayList<>();
        Matcher call = CALL.matcher(source);
        while (call.find()) {
            StringBuilder path = new StringBuilder("/api/v1/careers/");
            Matcher part = PART.matcher(call.group(2));
            while (part.find()) {
                path.append(part.group(1) != null ? part.group(1) : "1");
            }
            String concrete = path.toString().split("\\?")[0];
            calls.add(new String[] {call.group(1), concrete});
        }
        return calls;
    }
}
