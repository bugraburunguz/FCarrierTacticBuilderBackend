package com.fcareer.manager;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** MGR-a: menajer profili (itibar + tercihler) ve kulüp önerisi modelleri. FC'de menajerin oyun içi özelliği yok; profil önerileri yönlendirir. */
public final class ManagerModels {

    private ManagerModels() {
    }

    /** Antrenörlük diploması (FM): Yok · Ulusal C/B/A · Uluslararası C/B/A · Uluslararası Pro Lisans. */
    public enum Licence { NONE, NATIONAL_C, NATIONAL_B, NATIONAL_A, INTERNATIONAL_C, INTERNATIONAL_B, INTERNATIONAL_A, PRO }

    /** Futbolculuk geçmişi (FM): Amatör · Yarı profesyonel · Profesyonel (yerel/bölgesel/ulusal) · Milli takım (uluslararası/dünya çapında). */
    public enum Background { AMATEUR, SEMI_PRO, PRO_LOCAL, PRO_REGIONAL, PRO_NATIONAL, INTERNATIONAL, WORLD_CLASS }

    /** Oyun tarzı: topa sahip olma · dengeli · kontra · yüksek pres (build-up ve savunma yaklaşımına eşlenir: ManagerRules.setup). */
    public enum Style { POSSESSION, BALANCED, COUNTER, HIGH_PRESS }

    /** Öncelik (en çok 2): gençler · hızlı başarı · finansal güç · kurtarma görevi. */
    public enum Priority { YOUTH, QUICK_SUCCESS, FINANCIAL, RESCUE }

    public enum Difficulty { EASY, MEDIUM, HARD }

    /** Kart kategorileri: tarzına en uygun · gençlik projesi · kurtarma görevi · bütçesi güçlü · itibarına göre güvenli · hayal kulübü. */
    public enum Category { BEST_FIT, YOUTH_PROJECT, RESCUE, STRONG_BUDGET, SAFE, DREAM }

    /** formation null → "Fark etmez" (kulübün en uygun formasyonu). leagueId/gender: isteğe bağlı filtreler. */
    public record ManagerProfile(@Size(max = 40) String name, @NotNull Licence licence, @NotNull Background background, @Size(max = 20) String formation,
                                 @NotNull Style style, @NotNull @Size(max = 2) List<@NotNull Priority> priorities, @NotNull Difficulty difficulty,
                                 Integer leagueId, @Min(0) @Max(1) Integer gender) {
    }

    /** Profil + hesaplanan itibar (0–100) ve bandı ("Yerel tanınırlık"). */
    public record ProfileView(ManagerProfile profile, int reputation, String band) {
    }

    /** profile null → kayıtlı profil. leagueId/gender filtreleri verilirse profildekinin yerine geçer. */
    public record RecommendationRequest(@jakarta.validation.Valid ManagerProfile profile, Integer leagueId, @Min(0) @Max(1) Integer gender) {
    }

    /** Kart: kategori, kulüp, lig, zorluk, 3 kanıt satırı, beklenti metni; uyum için kullanılan formasyon/tarz ve Takım gücü. */
    public record Card(Category category, long clubId, String clubName, Integer leagueId, String league, Difficulty difficulty, List<String> evidence,
                       String expectation, String formation, Style style, Double squadFit, int need, boolean dream) {
    }

    public record MissingCategory(Category category, String reason) {
    }

    /** catalogReady false → katalogda kulüp profili yok (içe aktarma gerekli); kartlar boş. */
    public record Recommendations(int reputation, String band, boolean catalogReady, List<Card> cards, List<MissingCategory> missing) {
    }
}
