package com.fcareer.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "user_player_state")
public class UserPlayerStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long careerId;

    private Long basePlayerId;

    private Integer clubId;

    private Short dynamicPotential;

    private Long valueEur;

    private Short form;

    @Column(nullable = false)
    private boolean onLoan;

    @Column(nullable = false)
    private boolean loanedOut;

    @Column(columnDefinition = "timestamptz")
    private Instant acquiredAt;

    @Column(columnDefinition = "timestamptz")
    private Instant updatedAt;
}
