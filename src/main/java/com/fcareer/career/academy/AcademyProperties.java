package com.fcareer.career.academy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** ACADEMY-b: altyapı satırı durumu. Değerler doğrulanmadı (verified:false). */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.academy")
public class AcademyProperties {
    /** "Bekliyor": altyapıda en az bu kadar ay ve son içe aktarmalarda OVR artışı yok (verified:false). */
    private int waitingMonths = 12;
    /** Artış bakılan son içe aktarma sayısı (en az 2; tek içe aktarmada durum bilinmez → Gelişiyor). */
    private int stallImports = 2;
}
