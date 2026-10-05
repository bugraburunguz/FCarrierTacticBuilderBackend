package com.fcareer.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "game_version")
public class GameVersionEntity {

    @Id
    private Short id;

    @Column(nullable = false, unique = true)
    private String code;

    private LocalDate releasedOn;
}
