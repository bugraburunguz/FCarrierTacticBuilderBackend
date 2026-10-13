package com.fcareer.career.desk;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kariyer masası ayarları: snapshot saklama sınırı boşsa sınırsızdır ("hiçbir şey kaybolmaz", BE-BUG-14). */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.career")
public class CareerDeskProperties {
    /** Kariyer başına tutulacak en son snapshot sayısı; null ya da 0 = sınırsız. */
    private Integer snapshotRetention;
}
