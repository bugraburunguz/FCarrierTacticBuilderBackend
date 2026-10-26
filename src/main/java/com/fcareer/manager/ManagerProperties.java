package com.fcareer.manager;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** MGR-a (verified:false, kaynak: "FM esinli, kalibre edilmedi"): itibar, ilgi, ihtiyaç, kategori ve zorluk eşikleri. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.manager")
public class ManagerProperties {
    /** Futbolculuk geçmişi tabanı. */
    private Map<String, Integer> backgroundBase = new LinkedHashMap<>(Map.of("AMATEUR", 5, "SEMI_PRO", 15, "PRO_LOCAL", 30, "PRO_REGIONAL", 40,
            "PRO_NATIONAL", 55, "INTERNATIONAL", 70, "WORLD_CLASS", 85));
    /** Diploma katkısı. */
    private Map<String, Integer> licenceBonus = new LinkedHashMap<>(Map.of("NONE", 0, "NATIONAL_C", 2, "NATIONAL_B", 4, "NATIONAL_A", 6,
            "INTERNATIONAL_C", 8, "INTERNATIONAL_B", 10, "INTERNATIONAL_A", 12, "PRO", 15));
    /** Kulüp ihtiyacı: (yurtiçi × w + uluslararası × w) − lig seviyesi düzeltmesi × (seviye − 1), aynı cinsiyette yüzdelik sıra. */
    private double domesticWeight = 0.6;
    private double internationalWeight = 0.4;
    private double leagueLevelPenalty = 1.0;
    /** İlgi: ihtiyaç ≤ itibar + interestMargin; hayal: (itibar + interestMargin, itibar + dreamMargin]. */
    private int interestMargin = 10;
    private int dreamMargin = 25;
    /** Kaba sıralamada uyum hesaplanacak aday sayısı. */
    private int poolSize = 40;
    /** Hayal aralığından (itibar + 10, itibar + 25] ayrıca uyum hesaplanacak aday sayısı. */
    private int dreamPoolSize = 10;
    /** Kurtarma: ihtiyaç − kadro gücü yüzdeliği ≥ bu fark. */
    private int rescueGap = 20;
    /** Güvenli: |ihtiyaç − itibar| ≤ bu fark ve kadro gücü ligin üst üçte birinde. */
    private int safeMargin = 10;
    /** Gençlik projesi: altyapı gelişimi en az bu (0–10) ve en az bir genç yetenek. */
    private int youthMinDevelopment = 7;
    private int youngMaxAge = 21;
    private int youngMinHeadroom = 10;
    /** Tahmini transfer bütçesi: en iyi budgetTop oyuncunun MODELED değer toplamı × budgetShare (CareerRecommendationReportRunner ile aynı varsayım). */
    private double budgetShare = 0.10;
    private int budgetTop = 16;
    /** Kadro gücü: en iyi strengthTop oyuncunun ortalama OVR'si. */
    private int strengthTop = 11;
    /** Zorluk tercihi: aynı zorluk +bonus, iki kademe uzak −bonus (uyum puanına). */
    private double difficultyBonus = 5.0;
    /** Zorluk puanı: ≤ easyMax Kolay, ≥ hardMin Zor. */
    private int easyMax = 1;
    private int hardMin = 3;
    /** Bu itibarın altı deneyimsiz sayılır (+1 zorluk). */
    private int inexperiencedBelow = 30;
    /** Beklenti metni: |itibar − ihtiyaç| bu farkı aşarsa "hızlı başarı" ya da "sabırlı ama şüpheli". */
    private int expectationGap = 15;
}
