package com.fcareer.versioning.model;

import java.util.List;
import java.util.Map;

public record PlayerChange(Long basePlayerId, ChangeType type, VersionSnapshot before, VersionSnapshot after,
                           List<AttrChange> attributes, PlaystyleChange playstyles) {

    public record AttrChange(String key, int oldValue, int newValue) {
        public int delta() {
            return newValue - oldValue;
        }
    }

    public record PlaystyleChange(List<String> added, List<String> removed, List<Map<String, String>> upgraded, List<Map<String, String>> downgraded) {
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && upgraded.isEmpty() && downgraded.isEmpty();
        }
    }
}
