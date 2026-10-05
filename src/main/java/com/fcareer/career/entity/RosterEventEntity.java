package com.fcareer.career.entity;

import com.fcareer.career.model.enums.RosterEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "roster_event")
@EntityListeners(AuditingEntityListener.class)
public class RosterEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long careerId;

    private Long basePlayerId;

    @Enumerated(EnumType.STRING)
    private RosterEventType type;

    private Long feeEur;

    private Integer fromClubId;

    private Integer toClubId;

    @CreatedDate
    @Column(columnDefinition = "timestamptz", updatable = false)
    private Instant createdAt;
}
