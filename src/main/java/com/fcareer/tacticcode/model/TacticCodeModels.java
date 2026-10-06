package com.fcareer.tacticcode.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TacticCodeModels {

    private TacticCodeModels() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportResponse {
        private String code;
        private String formation;
        private boolean exact;
        private List<String> warnings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRequest {
        @NotBlank
        @Size(max = 64)
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportedSlot {
        private String slotId;
        private String roleId;
        private String roleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResponse {
        private String formation;
        private String buildUp;
        private int depth;
        private List<ImportedSlot> slots;
        private boolean exact;
        private List<String> warnings;
    }
}
