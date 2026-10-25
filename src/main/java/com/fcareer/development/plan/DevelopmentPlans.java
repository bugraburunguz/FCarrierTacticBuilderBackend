package com.fcareer.development.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FIT-3 madde 8: FC 27 kariyer gelişim planları (`seed/development-plans.fc27.json`, Buğra'nın oyun ekranı tablosu).
 * Dengeli (genel), 11 mevki planı (Role+ geliştirmez) ve 37 rol planı (Role+ geliştirir). Rol planının anahtarı (baseRole, mevki): aynı rol farklı
 * mevkide farklı attribute çalıştırır (holding CDM/CM ...). Bilinmeyen attribute ya da `roles.json`'da olmayan (rol, mevki) çifti yüklemeyi durdurur.
 */
@Component
public class DevelopmentPlans {

    private static final String SEED = "seed/development-plans.fc27.json";
    private static final String HASH = "seed/development-plans.fc27.hash";
    private static final String ROLES = "seed/roles.json";

    public enum Kind { GENERAL, POSITION, ROLE }

    /** key: API'de tekil anahtar (rol planı id'leri tekrar edebilir). baseRole yalnız rol planında. */
    public record Plan(String key, String id, String name, Kind kind, String baseRole, List<String> positions, List<Attr> attributes, boolean roleFamiliarity) {
    }

    private final String text;
    private final List<Plan> plans;
    private final List<JsonNode> issues;

    @Autowired
    public DevelopmentPlans(ObjectMapper mapper) {
        this(mapper, read(SEED), read(ROLES));
    }

    private DevelopmentPlans(ObjectMapper mapper, String text, String rolesJson) {
        this.text = text;
        try {
            JsonNode root = mapper.readTree(text);
            this.plans = load(root, rolePositions(mapper.readTree(rolesJson)));
            List<JsonNode> found = new ArrayList<>();
            root.path("_issues").forEach(found::add);
            this.issues = List.copyOf(found);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Test ve doğrulama için: verilen plan ve rol JSON'undan (seed yerine). */
    public static DevelopmentPlans parse(ObjectMapper mapper, String planJson, String rolesJson) {
        return new DevelopmentPlans(mapper, planJson, rolesJson);
    }

    private static Map<String, Set<String>> rolePositions(JsonNode roles) {
        Map<String, Set<String>> byRole = new HashMap<>();
        roles.forEach((r) -> {
            Set<String> positions = new HashSet<>();
            r.path("positions").forEach((p) -> positions.add(p.asText()));
            byRole.put(r.path("id").asText(), positions);
        });
        return byRole;
    }

    private static List<Plan> load(JsonNode root, Map<String, Set<String>> rolePositions) {
        List<Plan> all = new ArrayList<>();
        root.path("general").forEach((n) -> all.add(new Plan(n.path("id").asText(), n.path("id").asText(), n.path("name").asText(), Kind.GENERAL, null, List.of(), List.of(), false)));
        root.path("positionPlans").forEach((n) -> all.add(plan(n, Kind.POSITION, null, n.path("id").asText())));
        for (JsonNode n : root.path("rolePlans")) {
            String baseRole = n.path("baseRole").asText();
            List<String> positions = strings(n.path("positions"));
            for (String position : positions) {
                if (!rolePositions.getOrDefault(baseRole, Set.of()).contains(position)) {
                    throw new IllegalStateException("Gelişim planı " + n.path("id").asText() + ": roles.json'da " + baseRole + " @ " + position + " yok");
                }
            }
            all.add(plan(n, Kind.ROLE, baseRole, "role:" + baseRole + ":" + String.join("-", positions)));
        }
        if (all.stream().noneMatch((p) -> p.kind() == Kind.GENERAL)) {
            throw new IllegalStateException("Gelişim planları: genel (Dengeli) plan yok");
        }
        return List.copyOf(all);
    }

    private static Plan plan(JsonNode n, Kind kind, String baseRole, String key) {
        List<Attr> attributes = new ArrayList<>();
        for (String raw : strings(n.path("attributes"))) {
            attributes.add(Attr.fromKey(raw).orElseThrow(() -> new IllegalStateException("Gelişim planı " + n.path("id").asText() + ": bilinmeyen attribute " + raw)));
        }
        return new Plan(key, n.path("id").asText(), n.path("name").asText(), kind, baseRole, strings(n.path("positions")), List.copyOf(attributes),
                n.path("roleFamiliarity").asBoolean(false));
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach((x) -> out.add(x.asText()));
        return List.copyOf(out);
    }

    public List<Plan> all() {
        return plans;
    }

    public Plan general() {
        return plans.stream().filter((p) -> p.kind() == Kind.GENERAL).findFirst().orElseThrow();
    }

    public Optional<Plan> byKey(String key) {
        return plans.stream().filter((p) -> p.key().equals(key)).findFirst();
    }

    public Optional<Plan> positionPlan(String position) {
        return plans.stream().filter((p) -> p.kind() == Kind.POSITION && p.positions().contains(position)).findFirst();
    }

    public List<Plan> rolePlans(String position) {
        return plans.stream().filter((p) -> p.kind() == Kind.ROLE && p.positions().contains(position)).toList();
    }

    public Optional<Plan> rolePlan(String baseRole, String position) {
        return rolePlans(position).stream().filter((p) -> p.baseRole().equals(baseRole)).findFirst();
    }

    /** Rol planında olup aynı mevkinin mevki planında olmayan attribute'lar (rolün mevkiye göre farkı). Mevki planı yoksa (kaleci) hepsi. */
    public List<Attr> extraVsPosition(Plan rolePlan) {
        Set<Attr> positionAttrs = new HashSet<>();
        rolePlan.positions().forEach((pos) -> positionPlan(pos).ifPresent((p) -> positionAttrs.addAll(p.attributes())));
        return rolePlan.attributes().stream().filter((a) -> !positionAttrs.contains(a)).toList();
    }

    public List<JsonNode> issues() {
        return issues;
    }

    public String hash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String expectedHash() {
        return read(HASH).trim();
    }

    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
