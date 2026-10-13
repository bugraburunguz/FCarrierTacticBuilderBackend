package com.fcareer.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "base_player")
public class BasePlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long eaResourceId;

    @Column(nullable = false)
    private String fullName;

    private LocalDate dob;

    private Integer nationalityId;

    private Short heightCm;

    private Short weightKg;

    private String preferredFoot;

    @Column(nullable = false)
    private Short gender = 0;

    /** Bu oyuncuyu en son gören ingestion koşusu; boşsa siteden hiç gelmemiş (LE, kariyer). */
    private Long lastSeenRun;

    /** Tam bir koşuda siteden kalkmışsa emeklilik zamanı; göz atma/aramada gizlenir, kimlikle erişim sürer. */
    private java.time.Instant retiredAt;
}
