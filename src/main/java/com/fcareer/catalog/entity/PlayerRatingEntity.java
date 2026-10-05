package com.fcareer.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "player_rating")
public class PlayerRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long basePlayerId;

    @Column(nullable = false)
    private Short gameVersionId;

    private Integer clubId;

    @Column(nullable = false)
    private Short overall;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> positions;

    private String accelerate;
    private Short runStyle1 = 0;
    private Short runStyle2 = 0;
    private Short weakFoot;
    private Short skillMoves;
    private Short intlReputation;
    private String attWr;
    private String defWr;
    private String bodyType;

    private Short acceleration;
    private Short sprintSpeed;
    private Short agility;
    private Short balance;
    private Short reactions;
    private Short ballControl;
    private Short dribbling;
    private Short composure;
    private Short vision;
    private Short shortPassing;
    private Short longPassing;
    private Short crossing;
    private Short curve;
    private Short fkAccuracy;
    private Short finishing;
    private Short shotPower;
    private Short longShots;
    private Short volleys;
    private Short penalties;
    private Short attPosition;
    private Short heading;
    private Short defAwareness;
    private Short standingTackle;
    private Short slidingTackle;
    private Short interceptions;
    private Short jumping;
    private Short stamina;
    private Short strength;
    private Short aggression;
    private Short gkDiving;
    private Short gkHandling;
    private Short gkKicking;
    private Short gkPositioning;
    private Short gkReflexes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> playstyles = new HashMap<>();
}
