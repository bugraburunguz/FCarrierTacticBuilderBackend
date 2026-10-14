package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.FitPlayer;

import java.util.List;
import java.util.Map;

/** Taktik analizi (13 §3.4) giriş/çıkış modelleri. Hepsi saf veri; metin yalnız sayılardan üretilir (E3-8 hazırlığı). */
public final class TacticAnalysisModels {

    private TacticAnalysisModels() {
    }

    /** roleId "rol_focus" (örn. "fullback_defend"); players: slotId → atanmış oyuncu (boş olabilir). */
    public record Slot(String slotId, String position, int x, int y, String roleId) {
    }

    public record Input(String formationId, BuildUpType buildUp, int depth, List<Slot> slots, Map<String, FitPlayer> players) {
    }

    public record AttackDefence(double attackLoad, double defenceLoad, double attackPct) {
    }

    public record Width(double index, int left, int right) {
    }

    public record Length(double withBall, double withoutBall, double value) {
    }

    /** supply/gap oyuncu yoksa null. */
    public record Stamina(double demand, Double supply, Double gap) {
    }

    public record BuildUp(String type, Double safety, double required, Double margin) {
    }

    public record RestDefence(int count, List<String> slots) {
    }

    /** volume: rol eğilimlerinin toplamı; quality (0-100) ve score yalnız oyuncu varsa. */
    public record Channel(double volume, Double quality, Double score) {
    }

    public record Opportunity(Channel cross, Channel cutIn, Channel runBehind, Channel longShot) {
    }

    public record Metrics(String band, int depth, AttackDefence attackDefence, Width width, Length length, Stamina stamina, BuildUp buildUp, RestDefence restDefence, Opportunity opportunity) {
    }

    public enum Severity { INFO, WARN, RISK }

    public record Evidence(String metric, Map<String, Object> params, double value, String op, double threshold) {
    }

    public record Recommendation(String code, String text) {
    }

    /** code: kural kimliği; instance: foreach değişkenleri (örn. side=LEFT). */
    public record Finding(String code, Severity severity, Map<String, String> instance, List<Evidence> evidence, Recommendation recommendation, String text) {
    }

    public record NotEvaluated(String code, String reason) {
    }

    public record Analysis(Metrics metrics, List<Finding> findings, List<NotEvaluated> notEvaluated) {
    }
}
