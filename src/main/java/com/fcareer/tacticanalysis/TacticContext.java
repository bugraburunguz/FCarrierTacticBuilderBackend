package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.RoleMovements.Movement;

import java.util.List;
import java.util.Map;

/** Yerleştirilmiş taktik: her slot için hareket profili, topla/topsuz konum, taraf ve (varsa) oyuncu. Metrik ve kural hesapları bunun üstündedir. */
public record TacticContext(AnalysisBand band, BuildUpType buildUp, int depth, List<Placed> placed, TacticParams params, double pressMult) {

    public enum Side { LEFT, RIGHT, CENTER }

    /** x/y: formasyon konumu; xBall/yBall: topla; yNoBall: topsuz (hat kaymasıyla). Kaleci dahildir (gk=true) ama saha metriklerinde yok sayılır. */
    public record Placed(String slotId, String position, int x, int y, Movement m, FitPlayer player, double xBall, double yBall, double yNoBall, Side side, boolean gk) {
    }

    private static final double SIDE_LEFT_MAX = 45;
    private static final double SIDE_RIGHT_MIN = 55;

    public List<Placed> outfield() {
        return placed.stream().filter((p) -> !p.gk()).toList();
    }

    public boolean hasPlayers() {
        return placed.stream().anyMatch((p) -> p.player() != null);
    }

    public static Side sideOf(int x) {
        if (x < SIDE_LEFT_MAX) {
            return Side.LEFT;
        }
        return x > SIDE_RIGHT_MIN ? Side.RIGHT : Side.CENTER;
    }

    public static TacticContext of(TacticAnalysisModels.Input input, RoleMovements movements, TacticParams params) {
        AnalysisBand band = AnalysisBand.of(input.depth());
        double shift = movements.lineShift(band);
        Map<String, FitPlayer> players = input.players() == null ? Map.of() : input.players();
        List<Placed> placed = input.slots().stream().map((s) -> {
            Movement m = movements.find(s.roleId()).orElseThrow(() -> new IllegalArgumentException("hareket profili yok: " + s.roleId()));
            int sign = s.x() < 50 ? 1 : s.x() > 50 ? -1 : 0;
            boolean gk = "GK".equals(s.position());
            double xBall = clamp(s.x() + sign * m.dxIn());
            double yBall = clamp(s.y() - m.dyFwd());
            double yNoBall = clamp(s.y() - ("ST".equals(s.position()) || "CF".equals(s.position()) ? 0 : shift));
            return new Placed(s.slotId(), s.position(), s.x(), s.y(), m, players.get(s.slotId()), xBall, yBall, yNoBall, sideOf(s.x()), gk);
        }).toList();
        return new TacticContext(band, input.buildUp(), input.depth(), placed, params, movements.pressMult(band));
    }

    private static double clamp(double v) {
        return Math.min(100, Math.max(0, v));
    }
}
