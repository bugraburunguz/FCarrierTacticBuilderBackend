package com.fcareer.tacticcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.model.EaCodecData;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.model.EaTactic.EaSlot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * EA FC taktik paylaşım kodunun (12 karakter) kodlayıcı/çözücüsü. Format, resmî olmayan tersine mühendislikle çıkarıldı:
 * her kod 12 adet 6-bit basamak; alanlar (build-up 2 bit, hat yüksekliği 7 bit, 11 slotun rol+odak sırası 4'er bit) bit-paketlenir,
 * ardından alanların toplamından (S) türeyen bir kaydırma 2. basamak hariç tüm basamaklara eklenir.
 * Tüm sabitler 100+ oyun içi/easysbc doğrulama örneğiyle sınandı (bkz. EaTacticCodecTest).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EaTacticCodec {

    private static final String RESOURCE = "seed/ea-tactic-codec.json";
    private static final int DIGITS = 12;
    private static final int SLOTS = 11;
    private static final int DIGIT_BITS = 6;
    private static final int DIGIT_MASK = 63;
    private static final int SECOND_DIGIT = 1;
    private static final int CARRY_OFFSET = 5;
    private static final int LINE_HEIGHT_SHIFT = 63;
    private static final int BUILD_UP_SHIFT = 70;
    private static final int SLOT_TOP_SHIFT = 59;
    private static final int SLOT_STRIDE = 4;
    private static final int DEFAULT_LINE_HEIGHT = 50;
    private static final int MIN_LINE_HEIGHT = 1;
    private static final int MAX_LINE_HEIGHT = 100;
    private static final int SIGNED_LIMIT = 32;
    private static final int SLOT_FIELD_BITS = 4;

    private final ObjectMapper objectMapper;

    private EaCodecData data;
    private final Map<String, EaCodecData.Formation> formations = new LinkedHashMap<>();
    private final Map<Integer, EaCodecData.Role> roles = new HashMap<>();
    private final Map<String, Map<String, Integer>> absByPosition = new HashMap<>();
    private final Map<String, Map<Integer, EaSlot>> slotByAbs = new HashMap<>();
    private final Map<Character, Integer> alphabetIndex = new HashMap<>();
    private final Map<Character, Character> unsubstitute = new HashMap<>();
    private final Map<Character, Character> substitute = new HashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            data = objectMapper.readValue(in, EaCodecData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("EA codec data could not be read", e);
        }
        data.formations().forEach(f -> formations.put(f.id(), f));
        data.roles().forEach(r -> roles.put(r.id(), r));
        for (int i = 0; i < data.alphabet().length(); i++) {
            alphabetIndex.put(data.alphabet().charAt(i), i);
        }
        data.substitutions().forEach((plain, shown) -> {
            substitute.put(plain.charAt(0), shown.charAt(0));
            unsubstitute.put(shown.charAt(0), plain.charAt(0));
        });
        data.positionTables().forEach(this::loadPositionTable);
        log.info("EA tactic codec loaded formations : {} roles : {}", formations.size(), roles.size());
    }

    private void loadPositionTable(String position, String text) {
        Map<String, Integer> relative = new LinkedHashMap<>();
        int min = Integer.MAX_VALUE;
        for (String entry : text.split(",")) {
            String[] parts = entry.split(":");
            int value = Integer.parseInt(parts[1]);
            value = value > SIGNED_LIMIT ? value - 64 : value;
            relative.put(parts[0], value);
            min = Math.min(min, value);
        }
        Map<String, Integer> abs = new LinkedHashMap<>();
        Map<Integer, EaSlot> reverse = new HashMap<>();
        for (Map.Entry<String, Integer> entry : relative.entrySet()) {
            int value = entry.getValue() - min;
            abs.put(entry.getKey(), value);
            String[] key = entry.getKey().split("\\.");
            reverse.put(value, new EaSlot(Integer.parseInt(key[0]), Integer.parseInt(key[1])));
        }
        absByPosition.put(position, abs);
        slotByAbs.put(position, reverse);
    }

    public Optional<EaCodecData.Formation> formation(String formationId) {
        return Optional.ofNullable(formations.get(formationId));
    }

    public List<EaCodecData.Formation> formations() {
        return List.copyOf(formations.values());
    }

    public Optional<EaCodecData.Role> role(int roleId) {
        return Optional.ofNullable(roles.get(roleId));
    }

    public List<EaCodecData.Role> roles() {
        return List.copyOf(roles.values());
    }

    public boolean isSupported(String position, int roleId, int focusIndex) {
        Map<String, Integer> abs = absByPosition.get(position);
        return Objects.nonNull(abs) && abs.containsKey(roleId + "." + focusIndex);
    }

    public String encode(EaTactic tactic) {
        return toText(digits(tactic));
    }

    int[] digits(EaTactic tactic) {
        EaCodecData.Formation formation = Optional.ofNullable(formations.get(tactic.formationId()))
                .orElseThrow(() -> new IllegalArgumentException("unknown formation " + tactic.formationId()));
        if (tactic.slots().size() != SLOTS) {
            throw new IllegalArgumentException("a tactic needs " + SLOTS + " slots");
        }
        if (tactic.lineHeight() < MIN_LINE_HEIGHT || tactic.lineHeight() > MAX_LINE_HEIGHT) {
            throw new IllegalArgumentException("line height out of range");
        }
        int[] add = new int[DIGITS];
        int shift = 0;
        for (int s = 0; s < SLOTS; s++) {
            String position = formation.positions().get(s);
            EaSlot slot = tactic.slots().get(s);
            Integer abs = absByPosition.get(position).get(slot.roleId() + "." + slot.focusIndex());
            if (Objects.isNull(abs)) {
                throw new IllegalArgumentException("role " + slot.roleId() + "/" + slot.focusIndex() + " is not allowed at " + position);
            }
            int defaultAbs = absByPosition.get(position).get(formation.roleIds().get(s) + ".0");
            shift += abs - defaultAbs;
            accumulate(add, abs, defaultAbs, SLOT_TOP_SHIFT - SLOT_STRIDE * s);
        }
        shift += tactic.lineHeight() - DEFAULT_LINE_HEIGHT;
        accumulate(add, tactic.lineHeight(), DEFAULT_LINE_HEIGHT, LINE_HEIGHT_SHIFT);
        shift += tactic.buildUp().getChecksumValue();
        accumulate(add, tactic.buildUp().getFieldValue(), EaBuildUp.BALANCED.getFieldValue(), BUILD_UP_SHIFT);
        return apply(formation.base(), add, shift);
    }

    private void accumulate(int[] add, int value, int reference, int bitShift) {
        int[] now = split(value, bitShift);
        int[] ref = split(reference, bitShift);
        for (int i = 0; i < DIGITS; i++) {
            add[i] += now[i] - ref[i];
        }
    }

    private int[] apply(List<Integer> base, int[] add, int shift) {
        int[] out = new int[DIGITS];
        int first = base.get(0);
        for (int i = 0; i < DIGITS; i++) {
            if (i == SECOND_DIGIT) {
                int carry = Math.floorDiv(first + shift + CARRY_OFFSET, 64) - Math.floorDiv(first + CARRY_OFFSET, 64);
                out[i] = mod(base.get(i) + add[i] + carry);
            } else {
                out[i] = mod(base.get(i) + add[i] + shift);
            }
        }
        return out;
    }

    private static int[] split(int value, int bitShift) {
        BigInteger x = BigInteger.valueOf(value).shiftLeft(bitShift);
        int[] out = new int[DIGITS];
        for (int i = 0; i < DIGITS; i++) {
            out[i] = x.shiftRight(DIGIT_BITS * i).and(BigInteger.valueOf(DIGIT_MASK)).intValue();
        }
        return out;
    }

    private static int mod(int value) {
        return Math.floorMod(value, 64);
    }

    private String toText(int[] digits) {
        StringBuilder text = new StringBuilder();
        for (int d : digits) {
            char plain = data.alphabet().charAt(d);
            text.append(substitute.getOrDefault(plain, plain));
        }
        return text.toString();
    }

    public int[] parse(String code) {
        String trimmed = Objects.isNull(code) ? "" : code.trim();
        if (trimmed.length() != DIGITS) {
            throw new IllegalArgumentException("code must have " + DIGITS + " characters");
        }
        int[] digits = new int[DIGITS];
        for (int i = 0; i < DIGITS; i++) {
            char shown = trimmed.charAt(i);
            Integer index = alphabetIndex.get(unsubstitute.getOrDefault(shown, shown));
            if (Objects.isNull(index)) {
                throw new IllegalArgumentException("invalid character '" + shown + "'");
            }
            digits[i] = index;
        }
        return digits;
    }

    /** Kodu çözer; birden çok formasyon uyarsa (beklenmez) ilkini döner. Boş dönerse kod geçersizdir. */
    public Optional<EaTactic> decode(String code) {
        int[] digits = parse(code);
        for (EaCodecData.Formation formation : formations.values()) {
            Optional<EaTactic> found = tryFormation(formation, digits);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<EaTactic> tryFormation(EaCodecData.Formation formation, int[] digits) {
        List<Integer> base = formation.base();
        int shift = mod(digits[0] - base.get(0));
        if (mod(digits[2] - base.get(2)) != shift) {
            return Optional.empty();
        }
        int[] residual = new int[DIGITS];
        for (int i = 3; i < DIGITS; i++) {
            residual[i] = mod(digits[i] - base.get(i) - shift);
        }
        Search search = new Search(formation, residual, shift, digits[SECOND_DIGIT]);
        return search.run();
    }

    private final class Search {

        private final EaCodecData.Formation formation;
        private final int[] residual;
        private final int shift;
        private final int secondDigit;
        private final int[] add = new int[DIGITS];
        private final EaSlot[] chosen = new EaSlot[SLOTS];
        private EaBuildUp buildUp;
        private int lineHeight;

        Search(EaCodecData.Formation formation, int[] residual, int shift, int secondDigit) {
            this.secondDigit = secondDigit;
            this.formation = formation;
            this.residual = residual;
            this.shift = shift;
        }

        Optional<EaTactic> run() {
            for (EaBuildUp candidate : EaBuildUp.values()) {
                int[] before = add.clone();
                accumulate(add, candidate.getFieldValue(), EaBuildUp.BALANCED.getFieldValue(), BUILD_UP_SHIFT);
                buildUp = candidate;
                if (searchLineHeight()) {
                    return Optional.of(new EaTactic(formation.id(), buildUp, lineHeight, List.of(chosen)));
                }
                System.arraycopy(before, 0, add, 0, DIGITS);
            }
            return Optional.empty();
        }

        private boolean searchLineHeight() {
            for (int lh = MIN_LINE_HEIGHT; lh <= MAX_LINE_HEIGHT; lh++) {
                int[] before = add.clone();
                accumulate(add, lh, DEFAULT_LINE_HEIGHT, LINE_HEIGHT_SHIFT);
                lineHeight = lh;
                if (finalDigits(SLOT_TOP_SHIFT) && searchSlot(0)) {
                    return true;
                }
                System.arraycopy(before, 0, add, 0, DIGITS);
            }
            return false;
        }

        private boolean searchSlot(int s) {
            if (s == SLOTS) {
                return checksumMatches() && finalDigits(-1);
            }
            String position = formation.positions().get(s);
            int defaultAbs = absByPosition.get(position).get(formation.roleIds().get(s) + ".0");
            int bitShift = SLOT_TOP_SHIFT - SLOT_STRIDE * s;
            for (Map.Entry<Integer, EaSlot> candidate : slotByAbs.get(position).entrySet()) {
                int[] before = add.clone();
                accumulate(add, candidate.getKey(), defaultAbs, bitShift);
                chosen[s] = candidate.getValue();
                int nextShift = s + 1 < SLOTS ? SLOT_TOP_SHIFT - SLOT_STRIDE * (s + 1) : -1;
                if (finalDigits(nextShift) && searchSlot(s + 1)) {
                    return true;
                }
                System.arraycopy(before, 0, add, 0, DIGITS);
            }
            return false;
        }

        /** Kalan alanların (en üst biti nextShift+3) dokunamayacağı üst basamaklar kesinleşmiştir; bunlar kalıntıyla eşleşmeli. */
        private boolean finalDigits(int nextShift) {
            int highestOpenDigit = nextShift < 0 ? -1 : (nextShift + SLOT_FIELD_BITS - 1) / DIGIT_BITS;
            for (int i = DIGITS - 1; i > highestOpenDigit && i >= 3; i--) {
                if (mod(add[i]) != residual[i]) {
                    return false;
                }
            }
            return true;
        }

        private boolean checksumMatches() {
            int sum = lineHeight - DEFAULT_LINE_HEIGHT + buildUp.getChecksumValue();
            for (int s = 0; s < SLOTS; s++) {
                String position = formation.positions().get(s);
                int abs = absByPosition.get(position).get(chosen[s].roleId() + "." + chosen[s].focusIndex());
                sum += abs - absByPosition.get(position).get(formation.roleIds().get(s) + ".0");
            }
            if (mod(sum) != shift) {
                return false;
            }
            int first = formation.base().get(0);
            int carry = Math.floorDiv(first + sum + CARRY_OFFSET, 64) - Math.floorDiv(first + CARRY_OFFSET, 64);
            return secondDigit == mod(formation.base().get(SECOND_DIGIT) + carry);
        }
    }
}
