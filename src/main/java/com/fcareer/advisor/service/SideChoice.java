package com.fcareer.advisor.service;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;

import java.util.Map;
import java.util.Objects;

/**
 * FIT-3 madde 13 (BEHAVIOR CHANGE): kanat mevkilerinde (LB/RB, LM/RM, LW/RW) taraf, iki tarafın en iyi rol Güç'üne göre seçilir. FitEngineV3'ün ayak
 * terimleri tarzı taşır (orta açan roller aynı ayak, içe kat edenler ters ayak tarafında bonus; zayıf ayak yükseldikçe fark küçülür). Fark eşiğin
 * altındaysa eski seçim (BestPositionRule: listedeki taraf, sonra ayak) kalır. EA OVR değerleri değişmez; değişen önerilen taraf ve rol.
 */
public final class SideChoice {

    private static final Map<String, String> MIRROR = Map.of("LB", "RB", "RB", "LB", "LM", "RM", "RM", "LM", "LW", "RW", "RW", "LW");

    private SideChoice() {
    }

    /** Mevkideki en iyi rol ve Güç'ü. */
    public record Best(String roleName, double strength) {
    }

    /** styled: taraf tarzla seçildi (gerekçe var); değilse eski kural. */
    public record Choice(String position, String roleName, boolean styled, String reason) {
    }

    /** Bir rolün iki taraftaki Güç'ü ve bunun içindeki ayak terimi (FitEngineV3 contributions, term "foot"). */
    public record RoleAtSides(String roleName, double fitHere, double footHere, double fitThere, double footThere) {
    }

    /**
     * Seed rol vektörleri veriden geldiği için ayna mevkiler (LW/RW ...) asimetrik olabilir (ör. aynı oyuncu RW'de ~1 Güç fazla); bu, ayak terimini
     * yutar. Taraf karşılaştırmasında her rolün ayaksız Güç'ü iki tarafın ortalaması alınır, taraf yalnız ayak terimiyle ayrışır (tarz).
     */
    public static Map<String, Best> sideBests(String here, String there, java.util.List<RoleAtSides> roles) {
        Best bestHere = null;
        Best bestThere = null;
        for (RoleAtSides r : roles) {
            double symmetric = ((r.fitHere() - r.footHere()) + (r.fitThere() - r.footThere())) / 2;
            Best h = new Best(r.roleName(), symmetric + r.footHere());
            Best t = new Best(r.roleName(), symmetric + r.footThere());
            bestHere = Objects.isNull(bestHere) || h.strength() > bestHere.strength() ? h : bestHere;
            bestThere = Objects.isNull(bestThere) || t.strength() > bestThere.strength() ? t : bestThere;
        }
        java.util.Map<String, Best> out = new java.util.HashMap<>();
        if (Objects.nonNull(bestHere)) {
            out.put(here, bestHere);
            out.put(there, bestThere);
        }
        return out;
    }

    public static Choice choose(String picked, Map<String, Best> bestByPosition, FitPlayer player, double threshold) {
        String mirror = MIRROR.get(picked);
        Best here = bestByPosition.get(picked);
        Best there = Objects.isNull(mirror) ? null : bestByPosition.get(mirror);
        if (Objects.isNull(here) || Objects.isNull(there) || Math.abs(here.strength() - there.strength()) < threshold) {
            return new Choice(picked, Objects.isNull(here) ? null : here.roleName(), false, null);
        }
        String position = there.strength() > here.strength() ? mirror : picked;
        Best best = position.equals(picked) ? here : there;
        return new Choice(position, best.roleName(), true, reason(player, position, best.roleName()));
    }

    public static boolean wide(String position) {
        return MIRROR.containsKey(position);
    }

    public static String mirror(String position) {
        return MIRROR.get(position);
    }

    /**
     * Taraf karşılaştırması için oyuncu: ayna mevki de doğal sayılır (LW listeliyse RW de), böylece iki taraf arasındaki fark mevki dışı cezasından değil
     * yalnız ayak/tarz terimlerinden gelir.
     */
    public static FitPlayer bothSides(FitPlayer player, String position) {
        String mirror = MIRROR.get(position);
        if (Objects.isNull(mirror)) {
            return player;
        }
        java.util.List<String> positions = new java.util.ArrayList<>(player.getPositions());
        java.util.stream.Stream.of(position, mirror).filter((p) -> !positions.contains(p)).forEach(positions::add);
        return player.toBuilder().positions(java.util.List.copyOf(positions)).build();
    }

    /** "Orta profili baskın (Orta 78 · Bitiricilik 61) + sağ ayak → RW · Winger". Orta = Orta + Falso; bitiricilik = Bitiricilik + Uzaktan şut + Pozisyon alma. */
    private static String reason(FitPlayer player, String position, String roleName) {
        long cross = Math.round((player.attr(Attr.CROSSING) + player.attr(Attr.CURVE)) / 2.0);
        long finishing = Math.round((player.attr(Attr.FINISHING) + player.attr(Attr.LONG_SHOTS) + player.attr(Attr.POSITIONING)) / 3.0);
        String profile = cross >= finishing ? "Orta profili baskın" : "Bitirici profil baskın";
        String foot = "Left".equalsIgnoreCase(player.getPreferredFoot()) ? "sol ayak" : "Right".equalsIgnoreCase(player.getPreferredFoot()) ? "sağ ayak" : "ayak bilinmiyor";
        String base = roleName.replaceAll("\\s*\\(.*\\)\\s*$", "");
        return profile + " (Orta " + cross + " · Bitiricilik " + finishing + ") + " + foot + " → " + position + " · " + base;
    }
}
