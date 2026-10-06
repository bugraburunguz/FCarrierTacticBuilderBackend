package com.fcareer.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "career_player_rating")
public class CareerPlayerRatingEntity extends RatingColumns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long careerId;

    @Column(nullable = false)
    private Long basePlayerId;

    @Column(nullable = false)
    private Short gameVersionId;

    private Integer clubId;
}
