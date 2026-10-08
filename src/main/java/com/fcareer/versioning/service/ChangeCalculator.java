package com.fcareer.versioning.service;

import com.fcareer.versioning.model.ChangeType;
import com.fcareer.versioning.model.PlayerChange;
import com.fcareer.versioning.model.PlayerChange.AttrChange;
import com.fcareer.versioning.model.PlayerChange.PlaystyleChange;
import com.fcareer.versioning.model.VersionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class ChangeCalculator {

    private ChangeCalculator() {
    }

    /**
     * Deterministik sınıflandırma (squad_versioning_diff_spec §5):
     * OVR farkı belirleyicidir; fark 0 ise özelliklerin net toplamı (yeni - eski) yönü verir;
     * net toplam da 0 iken pozisyon/PlayStyle/AcceleRATE değişimi ya da karışık özellik değişimi MIXED sayılır.
     * Yalnız kulüp değiştiyse TRANSFER; kulüple birlikte başka değişiklik varsa kulüp alanları dolu kalır ve tür başka kurala göre belirlenir.
     * Hiçbir şey değişmediyse null döner.
     */
    public static PlayerChange diff(Long playerId, VersionSnapshot before, VersionSnapshot incoming) {
        VersionSnapshot after = fillUnknown(before, incoming);
        List<AttrChange> attrs = attributeChanges(before.attrs(), after.attrs());
        PlaystyleChange styles = playstyleChanges(before.playstyles(), after.playstyles());
        boolean overallChanged = !Objects.equals(before.overall(), after.overall());
        boolean accelChanged = !Objects.equals(before.accelerate(), after.accelerate());
        boolean positionsChanged = !Objects.equals(before.positions(), after.positions());
        boolean clubChanged = Objects.nonNull(before.clubId()) && Objects.nonNull(after.clubId()) && !Objects.equals(before.clubId(), after.clubId());
        boolean potentialChanged = Objects.nonNull(before.potential()) && Objects.nonNull(after.potential()) && !Objects.equals(before.potential(), after.potential());
        boolean other = overallChanged || accelChanged || positionsChanged || potentialChanged || !attrs.isEmpty() || !styles.isEmpty();
        if (!other && !clubChanged) {
            return null;
        }
        if (!other) {
            return new PlayerChange(playerId, ChangeType.TRANSFER, before, after, attrs, styles);
        }
        int overallDelta = overallChanged && Objects.nonNull(before.overall()) && Objects.nonNull(after.overall()) ? after.overall() - before.overall() : 0;
        int attrNet = attrs.stream().mapToInt(AttrChange::delta).sum();
        ChangeType type;
        if (overallDelta > 0 || (overallDelta == 0 && attrNet > 0)) {
            type = ChangeType.UPGRADE;
        } else if (overallDelta < 0 || (overallDelta == 0 && attrNet < 0)) {
            type = ChangeType.DOWNGRADE;
        } else {
            type = ChangeType.MIXED;
        }
        return new PlayerChange(playerId, type, before, after, attrs, styles);
    }

    /** Yeni kayıtta bilinmeyen (null) alan değişmedi sayılır: eski değer korunur, sahte fark üretilmez. */
    private static VersionSnapshot fillUnknown(VersionSnapshot before, VersionSnapshot after) {
        return new VersionSnapshot(after.overall(), Objects.isNull(after.potential()) ? before.potential() : after.potential(),
                Objects.isNull(after.accelerate()) ? before.accelerate() : after.accelerate(),
                Objects.isNull(after.positions()) || after.positions().isBlank() ? before.positions() : after.positions(),
                Objects.isNull(after.clubId()) ? before.clubId() : after.clubId(), after.attrs(), after.playstyles());
    }

    static List<AttrChange> attributeChanges(Map<String, Integer> before, Map<String, Integer> after) {
        List<AttrChange> changes = new ArrayList<>();
        for (String key : new TreeSet<>(after.keySet())) {
            Integer old = before.get(key);
            Integer now = after.get(key);
            if (Objects.nonNull(old) && Objects.nonNull(now) && !old.equals(now)) {
                changes.add(new AttrChange(key, old, now));
            }
        }
        return changes;
    }

    static PlaystyleChange playstyleChanges(Map<String, Integer> before, Map<String, Integer> after) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<Map<String, String>> upgraded = new ArrayList<>();
        List<Map<String, String>> downgraded = new ArrayList<>();
        for (String key : new TreeSet<>(after.keySet())) {
            Integer old = before.get(key);
            if (Objects.isNull(old)) {
                added.add(label(key, after.get(key)));
            } else if (after.get(key) > old) {
                upgraded.add(Map.of("from", label(key, old), "to", label(key, after.get(key))));
            } else if (after.get(key) < old) {
                downgraded.add(Map.of("from", label(key, old), "to", label(key, after.get(key))));
            }
        }
        for (String key : new TreeSet<>(before.keySet())) {
            if (!after.containsKey(key)) {
                removed.add(label(key, before.get(key)));
            }
        }
        return new PlaystyleChange(added, removed, upgraded, downgraded);
    }

    private static String label(String key, int level) {
        return level >= 2 ? key + "+" : key;
    }
}
