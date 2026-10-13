package com.fcareer.ut.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.ut.kb.KbModels.ContributionResult;
import com.fcareer.ut.kb.KbModels.EvolutionDto;
import com.fcareer.ut.kb.KbModels.ItemDto;
import com.fcareer.ut.kb.KbModels.ItemSearch;
import com.fcareer.ut.kb.KbModels.ObjectiveGroupDto;
import com.fcareer.ut.kb.KbModels.PricePoint;
import com.fcareer.ut.kb.KbModels.PromoDto;
import com.fcareer.ut.kb.KbModels.SbcSetDto;
import com.fcareer.ut.model.UtCaptureModels.Capture;
import com.fcareer.ut.model.UtCaptureModels.CaptureRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** UT bilgi tabanı: herkese açık okuma (extension gerekmez), katkıcılar için yazma, extension bağlama. */
@RestController
@RequestMapping(API_V1 + "/ut")
@RequiredArgsConstructor
public class UtKnowledgeController {

    private static final String RULES_RESOURCE = "seed/ut-rules.fc27.json";
    private static final Duration LINK_TTL = Duration.ofMinutes(15);
    private static final Set<String> PLATFORMS = Set.of("CONSOLE", "PC");
    private static final Map<String, Duration> RANGES = Map.of("24h", Duration.ofHours(24), "7d", Duration.ofDays(7), "30d", Duration.ofDays(30), "season", Duration.ofDays(120));

    private final KnowledgeBaseRepository repository;
    private final ContributionService contributionService;
    private final ContributorService contributorService;
    private final LinkTokenStore linkTokenStore;
    private final CatalogProperties catalogProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CaptureRules captureRules;
    private final com.fcareer.ut.service.UtRules utRules;

    /** Kural dokümanı + içerik hash'i; ETag/If-None-Match ile 304 döner (BE-ARCH-03). */
    @GetMapping("/rules")
    public JsonNode rules(org.springframework.web.context.request.WebRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String hash = utRules.hash();
        response.setHeader("Cache-Control", "public, max-age=300");
        if (request.checkNotModified("\"" + hash + "\"")) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ObjectNode body = utRules.document().deepCopy();
        body.put("rulesHash", hash);
        return body;
    }

    /** Extension'ın açılışta çektiği tek kaynaklı yakalama kuralları (EXT-04): deny listesi, kişisel/katkı uçları, minimum sürüm. */
    @GetMapping("/capture/rules")
    public JsonNode captureRules() {
        return captureRules.rules();
    }

    @GetMapping("/kb/sbc")
    public List<SbcSetDto> sbcSets(@RequestParam(required = false) String category, @RequestParam(defaultValue = "true") boolean active,
                                   @RequestParam(defaultValue = "false") boolean challenges) {
        return repository.sbcSets(gameVersion(), category, active, challenges);
    }

    @GetMapping("/kb/sbc/{id}")
    public SbcSetDto sbcSet(@PathVariable long id) {
        return repository.sbcSet(id).orElseThrow(() -> new NotFoundException("sbc", id));
    }

    @GetMapping("/kb/objectives")
    public List<ObjectiveGroupDto> objectives(@RequestParam(defaultValue = "true") boolean active) {
        return repository.objectiveGroups(gameVersion(), active);
    }

    @GetMapping("/kb/evolutions")
    public List<EvolutionDto> evolutions(@RequestParam(defaultValue = "true") boolean active) {
        return repository.evolutions(gameVersion(), active);
    }

    @GetMapping("/kb/promos")
    public List<PromoDto> promos() {
        return repository.promos(gameVersion());
    }

    @GetMapping("/kb/items")
    public List<ItemDto> items(@RequestParam(required = false) String q, @RequestParam(required = false) Integer minRating, @RequestParam(required = false) Integer maxRating,
                               @RequestParam(required = false) String pos, @RequestParam(required = false) Integer league, @RequestParam(required = false) Integer nation,
                               @RequestParam(required = false) Integer club, @RequestParam(required = false) String type,
                               @RequestParam(defaultValue = "40") int limit, @RequestParam(defaultValue = "0") int offset) {
        return repository.items(gameVersion(), new ItemSearch(q, minRating, maxRating, pos, league, nation, club, type, limit, offset));
    }

    @GetMapping("/kb/items/{id}")
    public Map<String, Object> item(@PathVariable long id) {
        ItemDto item = repository.item(id).orElseThrow(() -> new NotFoundException("item", id));
        return Map.of("item", item, "versions", repository.versionsOf(item.eaAssetId(), gameVersion()));
    }

    @GetMapping("/kb/items/{id}/prices")
    public List<PricePoint> prices(@PathVariable long id, @RequestParam(defaultValue = "CONSOLE") String platform, @RequestParam(defaultValue = "7d") String range) {
        String normalized = platform.toUpperCase();
        if (!PLATFORMS.contains(normalized) || !RANGES.containsKey(range)) {
            throw new InvalidTacticException("platform/range");
        }
        return repository.prices(id, normalized, clock.instant().minus(RANGES.get(range)));
    }

    /** Extension bağlama oturumu: kullanıcı siteden başlatır; extension JWT görmez, yalnız 15 dakikalık bu anahtarı taşır. Katkı modu yalnız aktif katkıcıya açılır. */
    @PostMapping("/capture/link")
    public Map<String, Object> link(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "PERSONAL") String mode) {
        String granted = "CONTRIB".equalsIgnoreCase(mode) && contributorService.isActive(user.id()) ? "CONTRIB" : "PERSONAL";
        return Map.of("token", linkTokenStore.issue(user.id(), granted, LINK_TTL), "mode", granted, "expiresInSeconds", LINK_TTL.toSeconds());
    }

    @PostMapping("/contrib/capture")
    public ContributionResult contribute(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CaptureRequest request,
                                         @RequestParam(defaultValue = "CONSOLE") String platform, @RequestParam(required = false) String locale,
                                         @RequestHeader(value = "X-Ext-Version", required = false) String extensionVersion) throws IOException {
        requireCurrentExtension(extensionVersion);
        List<Capture> captures = Objects.requireNonNull(request.captures());
        return contributionService.contribute(user.id(), captures, platform, locale);
    }

    /** EXT-12: sürüm başlığı varsa ve minimumun altındaysa güncelleme istenir (426). Başlıksız (ham yükleme) istekler etkilenmez. */
    private void requireCurrentExtension(String version) throws IOException {
        if (Objects.isNull(version) || version.isBlank()) {
            return;
        }
        String minimum = captureRules().path("minExtensionVersion").asText("0.0.0");
        if (compareVersions(version, minimum) < 0) {
            throw new com.fcareer.common.exception.ExtensionOutdatedException(minimum);
        }
    }

    static int compareVersions(String a, String b) {
        String[] left = a.split("[.]");
        String[] right = b.split("[.]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int x = i < left.length ? parse(left[i]) : 0;
            int y = i < right.length ? parse(right[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private short gameVersion() {
        return catalogProperties.getDefaultGameVersion();
    }
}
