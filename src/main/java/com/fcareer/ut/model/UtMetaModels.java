package com.fcareer.ut.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public final class UtMetaModels {

    private UtMetaModels() {
    }

    public record MetaFormation(String id, Integer share, String note, String buildUp, String lineHeight, List<String> keyRoles) {
    }

    public record MetaSeed(String snapshotNote, List<MetaFormation> formations, Map<String, Double> paceWeight, Map<String, Integer> tiers,
                           Map<String, Double> playstylePlusBonus, double playstylePlusCap, Map<String, Map<String, Integer>> positionAttrs,
                           int chemStyleBoost, Map<String, List<String>> chemStyles) {
    }

    public record MetaFormations(String snapshotNote, List<MetaFormation> formations) {
    }

    public record ChemStyleResult(String style, double metaRating, double delta, String tier) {
    }

    public record UpgradeOption(MetaCard card, double deltaMeta, int deltaOverall) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpgradeResult(MetaCard current, List<UpgradeOption> options, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetaCard(Long id, String name, Integer age, int overall, String position, double metaRating, String tier,
                           String bestChemStyle, List<ChemStyleResult> chemStyles, boolean assumption) {
    }
}
